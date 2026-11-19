package com.sseukbap.review;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.review.dto.ReviewItemResponse;
import com.sseukbap.review.dto.ReviewListResponse;
import com.sseukbap.review.repository.ReviewRepository;
import com.sseukbap.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리뷰 목록·작성·삭제 확인. @Transactional이라 테스트에서 쓴 리뷰는 DB에 남지 않는다.
 *
 * 더미데이터(docs/sql/04_dummy_data.sql) 기준 홍반점 리뷰 2개
 *   - user02, 9일 전,  태그 1·9·10 (점심추천, 웨이팅많음, 도보5분이내)  ← 최신
 *   - user01, 20일 전, 태그 1·6·8  (점심추천, 단체가능, 가성비)
 */
@SpringBootTest
@Transactional
class ReviewServiceTest {

    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;

    Long hongId;
    Member user01;
    Member user02;

    @BeforeEach
    void setUp() {
        hongId = restaurantRepository.findAll().stream()
                .filter(r -> r.getName().equals("홍반점"))
                .findFirst()
                .map(Restaurant::getRestaurantId)
                .orElseThrow();
        user01 = memberRepository.findByLoginId("user01").orElseThrow();
        user02 = memberRepository.findByLoginId("user02").orElseThrow();
    }

    // ===== 목록 =====

    @Test
    void 목록은_최신순_페이징되고_내_리뷰를_표시한다() {
        ReviewListResponse page1 = reviewService.getReviewList(hongId, 1, 1, user01.getMemberId());

        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.size()).isEqualTo(1);
        assertThat(page1.totalCount()).isEqualTo(2);
        assertThat(page1.hasNext()).isTrue();

        ReviewItemResponse newest = page1.items().get(0);
        assertThat(newest.nickname()).isEqualTo(user02.getNickname());
        assertThat(newest.tags()).containsExactly("점심추천", "웨이팅많음", "도보5분이내");
        assertThat(newest.imageUrl()).isNull();
        assertThat(newest.mine()).isFalse();

        ReviewListResponse page2 = reviewService.getReviewList(hongId, 2, 1, user01.getMemberId());
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.nickname()).isEqualTo(user01.getNickname());
                    assertThat(item.mine()).isTrue();
                });
    }

    @Test
    void 목록_page_size가_없으면_1페이지_10개() {
        ReviewListResponse result = reviewService.getReviewList(hongId, null, null, user01.getMemberId());

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.items()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 목록_page_size_범위_밖이면_400_없는_식당이면_404() {
        Long me = user01.getMemberId();
        assertError(() -> reviewService.getReviewList(hongId, 0, 10, me), ErrorCode.INVALID_REQUEST, "페이지 정보를 확인해 주세요.");
        assertError(() -> reviewService.getReviewList(hongId, 1, 0, me), ErrorCode.INVALID_REQUEST, "페이지 정보를 확인해 주세요.");
        assertError(() -> reviewService.getReviewList(hongId, 1, 51, me), ErrorCode.INVALID_REQUEST, "페이지 정보를 확인해 주세요.");
        assertError(() -> reviewService.getReviewList(-1L, 1, 10, me), ErrorCode.RESTAURANT_NOT_FOUND, null);
    }

    // ===== 작성 =====

    @Test
    void 작성하면_공백을_지우고_태그_중복을_하나로_저장한다() {
        Long me = user03Id();

        Long reviewId = reviewService.createReview(hongId, me, "  짜장면 맛있어요  ", List.of("5", "1", "5"))
                .reviewId();

        ReviewListResponse list = reviewService.getReviewList(hongId, 1, 10, me);
        assertThat(list.totalCount()).isEqualTo(3);
        ReviewItemResponse created = list.items().get(0); // 방금 쓴 리뷰가 가장 최신
        assertThat(created.reviewId()).isEqualTo(reviewId);
        assertThat(created.content()).isEqualTo("짜장면 맛있어요");
        assertThat(created.tags()).containsExactly("점심추천", "혼밥가능");
        assertThat(created.mine()).isTrue();
    }

    @Test
    void 태그_없이도_작성할_수_있다() {
        Long reviewId = reviewService.createReview(hongId, user03Id(), "한 번 더 갈게요", null).reviewId();
        assertThat(reviewRepository.findById(reviewId)).isPresent();
    }

    @Test
    void 작성_한줄평이_없거나_100자_초과면_400() {
        Long me = user03Id();
        String message = "한줄평을 입력해 주세요.";
        assertError(() -> reviewService.createReview(hongId, me, null, null), ErrorCode.INVALID_REQUEST, message);
        assertError(() -> reviewService.createReview(hongId, me, "   ", null), ErrorCode.INVALID_REQUEST, message);
        assertError(() -> reviewService.createReview(hongId, me, "가".repeat(101), null), ErrorCode.INVALID_REQUEST, message);

        // 100자는 통과
        assertThat(reviewService.createReview(hongId, me, "가".repeat(100), null).reviewId()).isNotNull();
    }

    @Test
    void 작성_없는_태그면_INVALID_TAG_없는_식당이면_404() {
        Long me = user03Id();
        assertError(() -> reviewService.createReview(hongId, me, "좋아요", List.of("1", "14")), ErrorCode.INVALID_TAG, null);
        assertError(() -> reviewService.createReview(hongId, me, "좋아요", List.of("abc")), ErrorCode.INVALID_TAG, null);
        assertError(() -> reviewService.createReview(-1L, me, "좋아요", null), ErrorCode.RESTAURANT_NOT_FOUND, null);
    }

    // ===== 삭제 =====

    @Test
    void 내_리뷰는_삭제된다() {
        Long me = user03Id();
        Long reviewId = reviewService.createReview(hongId, me, "삭제할 리뷰", List.of("1", "8")).reviewId();

        reviewService.deleteReview(reviewId, me);

        assertThat(reviewRepository.findById(reviewId)).isEmpty();
        assertThat(reviewService.getReviewList(hongId, 1, 10, me).totalCount()).isEqualTo(2);
    }

    @Test
    void 남의_리뷰는_403_없는_리뷰는_404() {
        Long othersReviewId = reviewService.getReviewList(hongId, 1, 1, user01.getMemberId())
                .items().get(0).reviewId(); // user02의 리뷰

        assertError(() -> reviewService.deleteReview(othersReviewId, user01.getMemberId()),
                ErrorCode.FORBIDDEN, "본인이 작성한 리뷰만 삭제할 수 있습니다.");
        assertError(() -> reviewService.deleteReview(-1L, user01.getMemberId()), ErrorCode.REVIEW_NOT_FOUND, null);
    }

    // ===== 도우미 =====

    private Long user03Id() {
        return memberRepository.findByLoginId("user03").map(Member::getMemberId).orElseThrow();
    }

    /** message가 null이면 문구는 확인하지 않는다 */
    private void assertError(Runnable action, ErrorCode errorCode, String message) {
        var assertion = assertThatThrownBy(action::run).isInstanceOf(BusinessException.class);
        if (message != null) {
            assertion.hasMessage(message);
        }
        assertion.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(errorCode);
    }
}
