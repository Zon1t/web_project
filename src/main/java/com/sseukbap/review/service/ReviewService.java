package com.sseukbap.review.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.review.dto.ReviewCreateResponse;
import com.sseukbap.review.dto.ReviewItemResponse;
import com.sseukbap.review.dto.ReviewListResponse;
import com.sseukbap.review.entity.Review;
import com.sseukbap.review.entity.ReviewTag;
import com.sseukbap.review.entity.Tag;
import com.sseukbap.review.repository.ReviewRepository;
import com.sseukbap.review.repository.ReviewTagNameRow;
import com.sseukbap.review.repository.ReviewTagRepository;
import com.sseukbap.review.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 리뷰 목록·작성·삭제. 기준: docs/API_SPEC.md 5장
 *
 * 사진(F-09): ReviewImageStorage가 검사·저장·삭제한다.
 *   작성 실패(롤백) → 저장한 파일 삭제, 리뷰 삭제 → 커밋된 뒤 파일 삭제
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private static final int CONTENT_MAX = 100;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private final ReviewRepository reviewRepository;
    private final ReviewTagRepository reviewTagRepository;
    private final TagRepository tagRepository;
    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;
    private final ReviewImageStorage reviewImageStorage;

    /** 리뷰 목록 (최신순 페이징) */
    public ReviewListResponse getReviewList(Long restaurantId, Integer page, Integer size, Long memberId) {
        // 1. page, size 검증 (없으면 기본값)
        int pageNo = (page == null) ? DEFAULT_PAGE : page;
        int pageSize = (size == null) ? DEFAULT_SIZE : size;
        if (pageNo < 1 || pageSize < 1 || pageSize > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "페이지 정보를 확인해 주세요.");
        }

        // 2. 식당 확인
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND);
        }

        // 3. 리뷰 페이지 조회 (Spring의 페이지 번호는 0부터라 1을 뺀다)
        Page<Review> reviewPage = reviewRepository.findPageByRestaurantId(
                restaurantId, PageRequest.of(pageNo - 1, pageSize));

        // 4. 이 페이지 리뷰들의 태그 이름을 한 번에 조회 → reviewId별로 묶기
        Map<Long, List<String>> tagsByReview = findTagNames(reviewPage.getContent());

        // 5. mine 표시해서 응답으로 변환
        List<ReviewItemResponse> items = reviewPage.getContent().stream()
                .map(review -> ReviewItemResponse.of(
                        review,
                        tagsByReview.getOrDefault(review.getReviewId(), List.of()),
                        memberId))
                .toList();

        // 6. 전체 개수, 다음 페이지 여부
        return new ReviewListResponse(
                pageNo, pageSize, reviewPage.getTotalElements(), reviewPage.hasNext(), items);
    }

    /**
     * 리뷰 작성
     *
     * @param tagIds 화면에서 보낸 tagId 문자열들. 숫자가 아니거나 1~13이 아니면 INVALID_TAG
     * @param image  사진 1장 (선택). 없거나 빈 파일이면 사진 없이 저장
     */
    @Transactional
    public ReviewCreateResponse createReview(Long restaurantId, Long memberId,
                                             String content, List<String> tagIds, MultipartFile image) {
        // 1. 한줄평 검증: 앞뒤 공백 제거 후 1~100자
        String trimmed = (content == null) ? "" : content.trim();
        if (trimmed.isEmpty() || trimmed.length() > CONTENT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "한줄평을 입력해 주세요.");
        }

        // 2. 식당 확인
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        // 3. 태그: 중복 제거 후 모두 TAG에 있는지 확인
        List<Tag> tags = findTags(tagIds);

        // 4. 사진: 검사 후 저장. 이 트랜잭션이 실패(롤백)하면 저장한 파일을 지운다 (명세 7번)
        String imagePath = null;
        if (image != null && !image.isEmpty()) {
            imagePath = reviewImageStorage.save(image);
            deleteFileOnRollback(imagePath);
        }

        // 5. 리뷰 저장
        Member member = memberRepository.getReferenceById(memberId); // 로그인 회원 (SELECT 없이 참조만)
        Review review = reviewRepository.save(Review.create(restaurant, member, trimmed, imagePath));

        // 6. 리뷰-태그 저장 (5와 같은 트랜잭션이라 하나라도 실패하면 리뷰도 저장되지 않는다)
        reviewTagRepository.saveAll(tags.stream()
                .map(tag -> ReviewTag.create(review, tag))
                .toList());

        return new ReviewCreateResponse(review.getReviewId());
    }

    /** 리뷰 삭제 (작성자만) */
    @Transactional
    public void deleteReview(Long reviewId, Long memberId) {
        // 1. 리뷰 조회
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        // 2. 작성자 확인
        if (!review.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 작성한 리뷰만 삭제할 수 있습니다.");
        }

        // 3. REVIEW_TAG → REVIEW 순서로 삭제
        String imagePath = review.getImagePath();
        reviewTagRepository.deleteByReview_ReviewId(reviewId);
        reviewRepository.delete(review);

        // 4. 사진 파일 삭제: DB 삭제가 확정(커밋)된 뒤에. 실패해도 로그만 남기고 응답은 성공
        if (imagePath != null) {
            runAfterCommit(() -> reviewImageStorage.delete(imagePath));
        }
    }

    // ===== 트랜잭션이 끝난 뒤 할 일 =====

    /** 이 트랜잭션이 롤백되면 방금 저장한 사진 파일을 지운다 */
    private void deleteFileOnRollback(String imagePath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    reviewImageStorage.delete(imagePath);
                }
            }
        });
    }

    /** 이 트랜잭션이 커밋된 뒤에 실행한다 */
    private void runAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    // ===== 내부 도우미 =====

    /** 리뷰 목록의 reviewId별 태그 이름 (태그가 없는 리뷰는 Map에 없음) */
    private Map<Long, List<String>> findTagNames(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return Map.of(); // IN () 빈 조건으로 쿼리하지 않기 위함
        }
        List<Long> reviewIds = reviews.stream().map(Review::getReviewId).toList();
        return reviewTagRepository.findTagNames(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        ReviewTagNameRow::getReviewId,
                        Collectors.mapping(ReviewTagNameRow::getTagName, Collectors.toList())));
    }

    /** tagId 문자열 → Tag. 중복은 1개로, 없는 값·숫자가 아닌 값이 있으면 INVALID_TAG */
    private List<Tag> findTags(List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (String value : tagIds) {
            try {
                uniqueIds.add(Long.parseLong(value.trim()));
            } catch (NumberFormatException | NullPointerException e) {
                throw new BusinessException(ErrorCode.INVALID_TAG);
            }
        }

        List<Tag> tags = new ArrayList<>(tagRepository.findAllById(uniqueIds));
        if (tags.size() != uniqueIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_TAG);
        }
        return tags;
    }
}
