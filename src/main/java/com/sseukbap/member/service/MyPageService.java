package com.sseukbap.member.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.dto.MyInterestResponse;
import com.sseukbap.member.dto.MyPageSummaryResponse;
import com.sseukbap.member.dto.MyPartyResponse;
import com.sseukbap.member.dto.MyRestaurantResponse;
import com.sseukbap.member.dto.MyReviewResponse;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.member.repository.MyPageQueryRepository;
import com.sseukbap.member.repository.MyPageQueryRepository.RestaurantThumbsRow;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.restaurant.entity.Interest;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.review.entity.Review;
import com.sseukbap.review.repository.ReviewTagNameRow;
import com.sseukbap.review.repository.ReviewTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 마이페이지 조회 (API_SPEC 2장). 모두 읽기 전용.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    /** 최근 30일 따봉 집계 기간 (랭킹과 같은 기준: 오늘 포함 30일) */
    private static final int THUMBS_PERIOD_DAYS = 30;

    private final MemberRepository memberRepository;
    private final MyPageQueryRepository myPageQueryRepository;
    private final ReviewTagRepository reviewTagRepository;

    /** 프로필 + 활동 건수 */
    public MyPageSummaryResponse getSummary(Long memberId) {
        // 세션은 있는데 회원이 없는 경우(DB 초기화 등)는 로그인이 필요한 상태로 본다
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        return new MyPageSummaryResponse(
                member.getNickname(),
                member.getLoginId(),
                myPageQueryRepository.countReviews(memberId),
                myPageQueryRepository.countParties(memberId, PartyStatus.DELETED),
                myPageQueryRepository.countRestaurants(memberId),
                myPageQueryRepository.countInterests(memberId)
        );
    }

    /** 내 리뷰 (최신순) */
    public List<MyReviewResponse> getReviews(Long memberId) {
        List<Review> reviews = myPageQueryRepository.findMyReviews(memberId);
        if (reviews.isEmpty()) {
            return List.of();
        }

        // 태그는 리뷰 ID 목록으로 한 번에 조회 (N+1 방지)
        List<Long> reviewIds = reviews.stream().map(Review::getReviewId).toList();
        Map<Long, List<String>> tagsByReview = new HashMap<>();
        for (ReviewTagNameRow row : reviewTagRepository.findTagNames(reviewIds)) {
            tagsByReview.computeIfAbsent(row.getReviewId(), id -> new ArrayList<>()).add(row.getTagName());
        }

        return reviews.stream()
                .map(r -> new MyReviewResponse(
                        r.getReviewId(),
                        r.getRestaurant().getRestaurantId(),
                        r.getRestaurant().getName(),
                        r.getContent(),
                        tagsByReview.getOrDefault(r.getReviewId(), List.of()),
                        r.getImagePath(),
                        toSeconds(r.getCreatedAt())))
                .toList();
    }

    /** 참여한 식사팟 (삭제 팟 제외, 식사 날짜 → 기준 시간 내림차순) */
    public List<MyPartyResponse> getParties(Long memberId) {
        List<PartyMember> rows = myPageQueryRepository.findMyParties(memberId, PartyStatus.DELETED);

        return rows.stream()
                .sorted(Comparator
                        .comparing((PartyMember pm) -> pm.getParty().getPartyDate()).reversed()
                        .thenComparing(pm -> pm.getParty().getBaseTime(), Comparator.reverseOrder())
                        .thenComparing(pm -> pm.getParty().getPartyId(), Comparator.reverseOrder()))
                .map(pm -> {
                    MealParty p = pm.getParty();
                    return new MyPartyResponse(
                            p.getPartyId(),
                            p.getPartyDate(),
                            p.getMealType(),
                            p.getBaseTime(),
                            p.getRestaurant().getRestaurantId(),
                            p.getRestaurant().getName(),
                            p.getStatus(),
                            p.isHost(memberId),
                            pm.isConfirmed());
                })
                .toList();
    }

    /** 내가 등록한 식당 (최신순) */
    public List<MyRestaurantResponse> getRestaurants(Long memberId) {
        return myPageQueryRepository.findMyRestaurants(memberId).stream()
                .map(r -> new MyRestaurantResponse(
                        r.getRestaurantId(),
                        r.getName(),
                        r.getCategory(),
                        toSeconds(r.getCreatedAt())))
                .toList();
    }

    /** 관심 식당 (최근 등록순) + 최근 30일 따봉 수 + 방문 기록 */
    public List<MyInterestResponse> getInterests(Long memberId) {
        List<Interest> interests = myPageQueryRepository.findMyInterests(memberId);
        if (interests.isEmpty()) {
            return List.of();   // IN () 빈 목록 쿼리를 피한다
        }

        List<Long> restaurantIds = interests.stream()
                .map(i -> i.getRestaurant().getRestaurantId())
                .toList();

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(THUMBS_PERIOD_DAYS - 1);
        Map<Long, Long> thumbsByRestaurant = new HashMap<>();
        for (RestaurantThumbsRow row : myPageQueryRepository.countThumbs(
                restaurantIds, true, PartyStatus.DELETED, from, today.plusDays(1))) {
            thumbsByRestaurant.put(row.getRestaurantId(), row.getThumbsCount());
        }

        Set<Long> visited = new HashSet<>(myPageQueryRepository.findVisitedRestaurantIds(
                memberId, restaurantIds, true, PartyStatus.FINISHED));

        return interests.stream()
                .map(i -> {
                    Restaurant r = i.getRestaurant();
                    return new MyInterestResponse(
                            r.getRestaurantId(),
                            r.getName(),
                            r.getCategory(),
                            thumbsByRestaurant.getOrDefault(r.getRestaurantId(), 0L),
                            visited.contains(r.getRestaurantId()),
                            toSeconds(i.getCreatedAt()));
                })
                .toList();
    }

    /** 명세 형식(2026-11-16T12:40:00)에 맞춰 초 단위로 자른다 */
    private static LocalDateTime toSeconds(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.SECONDS);
    }
}
