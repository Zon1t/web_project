package com.sseukbap.restaurant;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.dto.RestaurantAutocompleteResponse;
import com.sseukbap.restaurant.dto.RestaurantDetailResponse;
import com.sseukbap.restaurant.dto.RestaurantListResponse;
import com.sseukbap.restaurant.dto.TopTagResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.restaurant.service.RestaurantService;
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
 * 기대값은 docs/sql/04_dummy_data.sql 맨 위 주석 기준이다.
 *   최근 30일 따봉: 홍반점 4 > 스시하루 3 > 한솥가득 백반 2 = 라멘공방 2 (동점은 리뷰 수 순)
 * 더미데이터 날짜는 스크립트를 실행한 날 기준이라, 며칠 지나면 00 → 04를 다시 실행해야 맞는다.
 */
@SpringBootTest
@Transactional
class RestaurantServiceTest {

    @Autowired RestaurantService restaurantService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;

    // ===== 목록 =====

    @Test
    void 목록은_따봉수_리뷰수_등록순으로_정렬된다() {
        List<RestaurantListResponse> list = restaurantService.getRestaurantList(null, null);

        assertThat(list).extracting(RestaurantListResponse::name)
                .startsWith("홍반점", "스시하루", "한솥가득 백반", "라멘공방");
        assertThat(list).extracting(RestaurantListResponse::thumbsCount30d)
                .startsWith(4L, 3L, 2L, 2L);

        // 따봉 0건 식당과 구내식당도 목록에 나온다
        assertThat(list).hasSizeGreaterThanOrEqualTo(11);
        RestaurantListResponse cafeteria = list.stream()
                .filter(RestaurantListResponse::cafeteria)
                .findFirst()
                .orElseThrow();
        assertThat(cafeteria.category()).isEqualTo(RestaurantCategory.CAFETERIA);

        // 홍반점: 리뷰 2개, 좌표 있음
        RestaurantListResponse hong = list.get(0);
        assertThat(hong.reviewCount()).isEqualTo(2);
        assertThat(hong.lat()).isNotNull();
        assertThat(hong.lng()).isNotNull();
    }

    @Test
    void 목록은_검색어와_카테고리로_거른다() {
        // 화면에서 등록한 식당이 더해져도 깨지지 않게: 결과는 모두 조건에 맞고, 더미 식당이 순서대로 들어 있으면 된다
        List<RestaurantListResponse> japanese = restaurantService.getRestaurantList("", "JAPANESE");
        assertThat(japanese).allMatch(r -> r.category() == RestaurantCategory.JAPANESE);
        assertThat(japanese).extracting(RestaurantListResponse::name)
                .containsSubsequence("스시하루", "라멘공방");

        List<RestaurantListResponse> keyword = restaurantService.getRestaurantList("  반점 ", null);
        assertThat(keyword).allMatch(r -> r.name().contains("반점"));
        assertThat(keyword).extracting(RestaurantListResponse::name).contains("홍반점");

        assertThat(restaurantService.getRestaurantList("없는식당이름", null)).isEmpty();
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
        Long hongId = findRestaurantId("홍반점");
        Long user01 = memberRepository.findByLoginId("user01").map(Member::getMemberId).orElseThrow();
        Long user03 = memberRepository.findByLoginId("user03").map(Member::getMemberId).orElseThrow();

        RestaurantDetailResponse detail = restaurantService.getRestaurant(hongId, user01);

        assertThat(detail.name()).isEqualTo("홍반점");
        assertThat(detail.category()).isEqualTo(RestaurantCategory.CHINESE);
        assertThat(detail.cafeteria()).isFalse();
        assertThat(detail.mapUrl()).isNotBlank();
        assertThat(detail.thumbsCount30d()).isEqualTo(4);
        assertThat(detail.reviewCount()).isEqualTo(2);
        assertThat(detail.interestCount()).isGreaterThanOrEqualTo(1);
        assertThat(detail.interested()).isTrue();   // user01은 홍반점 관심 등록

        // 홍반점 리뷰 태그: 1(점심추천) 2회, 6·8·9·10 각 1회 → TOP 3 = 1, 6, 8 (동수면 tagId 오름차순)
        assertThat(detail.topTags()).extracting(TopTagResponse::tagId).containsExactly(1L, 6L, 8L);
        assertThat(detail.topTags().get(0).name()).isEqualTo("점심추천");
        assertThat(detail.topTags().get(0).count()).isEqualTo(2);

        // 다른 회원은 관심 여부 false
        assertThat(restaurantService.getRestaurant(hongId, user03).interested()).isFalse();
    }

    @Test
    void 리뷰가_없는_식당은_태그가_빈_배열() {
        Long cafeteriaId = restaurantRepository.findAll().stream()
                .filter(Restaurant::isCafeteria)
                .findFirst()
                .map(Restaurant::getRestaurantId)
                .orElseThrow();

        RestaurantDetailResponse detail = restaurantService.getRestaurant(cafeteriaId, 0L);

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
        // '반'이 들어간 식당: 한솥가득 백반, 홍반점 → 시작 일치 없음 → 가나다순
        List<RestaurantAutocompleteResponse> result = restaurantService.getAutocompleteList("반");
        assertThat(result).extracting(RestaurantAutocompleteResponse::name)
                .containsExactly("한솥가득 백반", "홍반점");

        // '스'로 시작하는 스시하루가 먼저, 그다음 '스'가 들어간 식당을 가나다순 (버거스테이션, 파스타앤코)
        List<RestaurantAutocompleteResponse> su = restaurantService.getAutocompleteList("스");
        assertThat(su).extracting(RestaurantAutocompleteResponse::name)
                .containsExactly("스시하루", "버거스테이션", "파스타앤코");
    }

    @Test
    void 자동완성_검색어가_없거나_30자_초과면_400() {
        assertInvalidRequest(() -> restaurantService.getAutocompleteList(null), "검색어를 입력해 주세요.");
        assertInvalidRequest(() -> restaurantService.getAutocompleteList("   "), "검색어를 입력해 주세요.");
        assertInvalidRequest(() -> restaurantService.getAutocompleteList("가".repeat(31)), "검색어를 입력해 주세요.");
    }

    // ===== 도우미 =====

    private Long findRestaurantId(String name) {
        return restaurantRepository.findAll().stream()
                .filter(r -> r.getName().equals(name))
                .findFirst()
                .map(Restaurant::getRestaurantId)
                .orElseThrow();
    }

    private void assertInvalidRequest(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
