package com.sseukbap.restaurant;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.restaurant.dto.RestaurantAutocompleteResponse;
import com.sseukbap.restaurant.dto.RestaurantDetailResponse;
import com.sseukbap.restaurant.dto.RestaurantListResponse;
import com.sseukbap.restaurant.dto.TopTagResponse;
import com.sseukbap.restaurant.entity.Interest;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.InterestRepository;
import com.sseukbap.restaurant.service.RestaurantService;
import com.sseukbap.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 식당 목록·상세·자동완성 확인.
 *
 * 2026-11-24 (C4): 더미데이터(홍반점·스시하루 등)의 순서·건수에 기대던 테스트를,
 * 테스트 안에서 이름에 무작위 번호를 붙여 만든 식당만 골라 확인하도록 바꿨다. (DB 상태와 관계없이 통과)
 * 테스트 식당은 따봉이 0개라, 정렬은 '리뷰 수 → 등록 순'으로 확인한다. (따봉 순서는 랭킹·식사팟 테스트 몫)
 */
@SpringBootTest
@Transactional
class RestaurantServiceTest {

    @Autowired RestaurantService restaurantService;
    @Autowired InterestRepository interestRepository;
    @Autowired TestData data;

    // ===== 목록 =====

    @Test
    void 목록은_따봉수_리뷰수_등록순으로_정렬된다() {
        String token = "정렬" + TestData.uniqueNumber();
        Member writer = data.member();
        Restaurant noReview = data.restaurant(token + "다", RestaurantCategory.KOREAN);
        Restaurant twoReviews = data.restaurant(token + "가", RestaurantCategory.KOREAN);
        Restaurant oneReviewFirst = data.restaurant(token + "나", RestaurantCategory.KOREAN);
        Restaurant oneReviewLater = data.restaurant(token + "라", RestaurantCategory.KOREAN);
        data.review(twoReviews, writer);
        data.review(twoReviews, writer);
        data.review(oneReviewFirst, writer);
        data.review(oneReviewLater, writer);

        List<RestaurantListResponse> list = restaurantService.getRestaurantList(token, null);

        // 따봉은 모두 0 → 리뷰 수 내림차순 → 같으면 먼저 등록한 식당
        assertThat(list).extracting(RestaurantListResponse::restaurantId).containsExactly(
                twoReviews.getRestaurantId(), oneReviewFirst.getRestaurantId(),
                oneReviewLater.getRestaurantId(), noReview.getRestaurantId());
        assertThat(list).extracting(RestaurantListResponse::reviewCount).containsExactly(2L, 1L, 1L, 0L);
        assertThat(list).allSatisfy(r -> {
            assertThat(r.thumbsCount30d()).isZero();
            assertThat(r.lat()).isNotNull();
            assertThat(r.lng()).isNotNull();
        });
    }

    @Test
    void 목록에는_구내식당도_나온다() {
        List<RestaurantListResponse> list = restaurantService.getRestaurantList(null, null);

        RestaurantListResponse cafeteria = list.stream()
                .filter(RestaurantListResponse::cafeteria)
                .findFirst()
                .orElseThrow();
        assertThat(cafeteria.category()).isEqualTo(RestaurantCategory.CAFETERIA);
    }

    @Test
    void 목록은_검색어와_카테고리로_거른다() {
        String token = "거름" + TestData.uniqueNumber();
        Restaurant sushi = data.restaurant(token + " 스시", RestaurantCategory.JAPANESE);
        Restaurant ramen = data.restaurant(token + " 라멘", RestaurantCategory.JAPANESE);
        Restaurant bap = data.restaurant(token + " 반점", RestaurantCategory.CHINESE);

        List<RestaurantListResponse> japanese = restaurantService.getRestaurantList(token, "JAPANESE");
        assertThat(japanese).extracting(RestaurantListResponse::restaurantId)
                .containsExactlyInAnyOrder(sushi.getRestaurantId(), ramen.getRestaurantId());

        List<RestaurantListResponse> keyword = restaurantService.getRestaurantList("  " + token + " 반점 ", null);
        assertThat(keyword).extracting(RestaurantListResponse::restaurantId).containsExactly(bap.getRestaurantId());

        assertThat(restaurantService.getRestaurantList("없는식당" + TestData.uniqueNumber(), null)).isEmpty();
    }

    @Test
    void 목록_검색어의_퍼센트는_글자_그대로_찾는다() {
        // '%'를 특수문자로 처리하면 전체 식당이 나온다
        assertThat(restaurantService.getRestaurantList("%", null)).isEmpty();
    }

    @Test
    void 목록_잘못된_조건이면_400() {
        assertInvalidRequest(() -> restaurantService.getRestaurantList(null, "PIZZA"), "검색 조건을 확인해 주세요.");
        assertInvalidRequest(() -> restaurantService.getRestaurantList("가".repeat(31), null), "검색 조건을 확인해 주세요.");
    }

    // ===== 상세 =====

    @Test
    void 상세는_태그TOP3_따봉_관심_리뷰수와_내_관심여부를_준다() {
        Restaurant restaurant = data.restaurant("상세" + TestData.uniqueNumber(), RestaurantCategory.CHINESE);
        Member me = data.member();
        Member other = data.member();
        // 태그 1(점심추천) 2회, 6·8·9 각 1회 → TOP 3 = 1, 6, 8 (동수면 tagId 오름차순)
        data.review(restaurant, me, 1L, 6L);
        data.review(restaurant, other, 1L, 8L, 9L);
        interestRepository.saveAndFlush(Interest.create(restaurant, me));

        RestaurantDetailResponse detail = restaurantService.getRestaurant(restaurant.getRestaurantId(), me.getMemberId());

        assertThat(detail.name()).isEqualTo(restaurant.getName());
        assertThat(detail.category()).isEqualTo(RestaurantCategory.CHINESE);
        assertThat(detail.cafeteria()).isFalse();
        assertThat(detail.mapUrl()).isEqualTo(restaurant.getMapUrl());
        assertThat(detail.thumbsCount30d()).isZero();
        assertThat(detail.reviewCount()).isEqualTo(2);
        assertThat(detail.interestCount()).isEqualTo(1);
        assertThat(detail.interested()).isTrue();

        assertThat(detail.topTags()).extracting(TopTagResponse::tagId).containsExactly(1L, 6L, 8L);
        assertThat(detail.topTags().get(0).name()).isEqualTo("점심추천");
        assertThat(detail.topTags().get(0).count()).isEqualTo(2);

        // 다른 회원은 관심 여부 false
        assertThat(restaurantService.getRestaurant(restaurant.getRestaurantId(), other.getMemberId()).interested()).isFalse();
    }

    @Test
    void 리뷰가_없는_식당은_태그가_빈_배열() {
        Restaurant cafeteria = data.restaurant("구내식당" + TestData.uniqueNumber(), RestaurantCategory.CAFETERIA);

        RestaurantDetailResponse detail = restaurantService.getRestaurant(cafeteria.getRestaurantId(), 0L);

        assertThat(detail.cafeteria()).isTrue();
        assertThat(detail.topTags()).isEmpty();
        assertThat(detail.reviewCount()).isZero();
        assertThat(detail.interested()).isFalse();
    }

    @Test
    void 없는_식당이면_404() {
        assertThatThrownBy(() -> restaurantService.getRestaurant(-1L, 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESTAURANT_NOT_FOUND);
    }

    // ===== 자동완성 =====

    @Test
    void 자동완성은_시작일치_우선_가나다순_최대5개() {
        String token = "자완" + TestData.uniqueNumber();
        data.restaurant("나" + token, RestaurantCategory.KOREAN);
        data.restaurant(token + "시작", RestaurantCategory.KOREAN);
        data.restaurant("가" + token, RestaurantCategory.KOREAN);

        // 시작 일치(token시작)가 먼저, 그다음 들어간 식당을 가나다순
        List<RestaurantAutocompleteResponse> result = restaurantService.getAutocompleteList(token);
        assertThat(result).extracting(RestaurantAutocompleteResponse::name)
                .containsExactly(token + "시작", "가" + token, "나" + token);

        // 6곳이 맞아도 최대 5개
        for (String prefix : List.of("다", "라", "마")) {
            data.restaurant(prefix + token, RestaurantCategory.KOREAN);
        }
        assertThat(restaurantService.getAutocompleteList(token)).hasSize(5);
    }

    @Test
    void 자동완성_검색어가_없거나_30자_초과면_400() {
        assertInvalidRequest(() -> restaurantService.getAutocompleteList(null), "검색어를 입력해 주세요.");
        assertInvalidRequest(() -> restaurantService.getAutocompleteList("   "), "검색어를 입력해 주세요.");
        assertInvalidRequest(() -> restaurantService.getAutocompleteList("가".repeat(31)), "검색어를 입력해 주세요.");
    }

    // ===== 도우미 =====

    private void assertInvalidRequest(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
