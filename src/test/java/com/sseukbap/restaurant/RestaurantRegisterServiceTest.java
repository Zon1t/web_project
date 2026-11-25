package com.sseukbap.restaurant;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.client.NaverLocalSearchClient;
import com.sseukbap.restaurant.client.NaverPlace;
import com.sseukbap.restaurant.dto.MapUrlCheckResponse;
import com.sseukbap.restaurant.dto.PlaceCandidateResponse;
import com.sseukbap.restaurant.dto.RestaurantRegisterRequest;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.restaurant.service.RestaurantRegisterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 식당 등록·URL 검증 확인.
 *
 * 네이버 검색은 실제로 부르지 않고 가짜(@MockitoBean)로 바꾼다. → 키가 없는 PC에서도 테스트가 통과한다.
 * 중복 확인은 테스트 안에서 먼저 등록한 장소로 한다. (DB에 어떤 더미·시연 데이터가 있든 통과하도록)
 * 회사 위치 = 초기 데이터 구내식당 좌표 (세종대, 37.5503, 127.0731). 등록 반경 기본 4km
 */
@SpringBootTest
@Transactional
class RestaurantRegisterServiceTest {

    // 실제 네이버에 없는 장소 ID (9로 시작하는 10자리 더미 번호대와도 겹치지 않게). 실제 식당 링크를 쓰면
    // 누군가 그 식당을 화면에서 등록했을 때 테스트가 '이미 등록됨'으로 깨진다.
    private static final String NEW_URL =
            "https://map.naver.com/p/search/%EB%8C%80%EC%BF%A0%EC%9D%B4/place/123000000999?c=15.00,0,0,0,dh";
    private static final String NEW_STANDARD_URL = "https://map.naver.com/p/entry/place/123000000999";

    /** 회사 근처 일식집 (약 0.2km) */
    private static final NaverPlace NEAR =
            new NaverPlace("대쿠이 세종대점", "서울특별시 광진구 능동로 200", 37.5490, 127.0740, "음식점>일식>일본식라면");
    /** 같은 이름의 먼 지점 (대구, 약 240km) */
    private static final NaverPlace FAR =
            new NaverPlace("대쿠이 본점", "대구광역시 남구 중앙대로51길 38-2", 35.8563052, 128.5885117, "음식점>일식");
    /** 회사 근처지만 음식점이 아닌 곳 */
    private static final NaverPlace SHOP =
            new NaverPlace("대쿠이 편의점", "서울특별시 광진구 능동로 201", 37.5495, 127.0735, "생활,편의>편의점");

    @Autowired RestaurantRegisterService registerService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;

    @MockitoBean NaverLocalSearchClient naverLocalSearchClient;

    Long user01;

    @BeforeEach
    void setUp() {
        user01 = memberRepository.findByLoginId("user01").map(Member::getMemberId).orElseThrow();
        given(naverLocalSearchClient.search(anyString())).willReturn(List.of(FAR, NEAR, SHOP));
    }

    // ===== URL 검증 =====

    @Test
    void 검증_후보마다_거리_분류_추천_카테고리와_등록_가능_여부를_준다() {
        MapUrlCheckResponse result = registerService.checkMapUrl(NEW_URL, " 대쿠이 ");

        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.radiusKm()).isEqualTo(4.0);
        assertThat(result.candidates()).hasSize(3);

        PlaceCandidateResponse far = result.candidates().get(0);
        assertThat(far.registrable()).isFalse();
        assertThat(far.blockReason()).isEqualTo("OUT_OF_RANGE");
        assertThat(far.distanceKm()).isGreaterThan(200);

        PlaceCandidateResponse near = result.candidates().get(1);
        assertThat(near.registrable()).isTrue();
        assertThat(near.blockReason()).isNull();
        assertThat(near.distanceKm()).isLessThan(1);
        assertThat(near.naverCategory()).isEqualTo("음식점>일식>일본식라면");
        assertThat(near.suggestedCategory()).isEqualTo("JAPANESE");

        PlaceCandidateResponse shop = result.candidates().get(2);
        assertThat(shop.registrable()).isFalse();
        assertThat(shop.blockReason()).isEqualTo("NOT_A_RESTAURANT");
        assertThat(shop.suggestedCategory()).isNull();
    }

    @Test
    void 검증_이름만으로는_먼_곳만_나오면_회사_지역을_붙여_다시_검색한다() {
        given(naverLocalSearchClient.search(anyString())).willReturn(List.of());
        given(naverLocalSearchClient.search(eq("대쿠이"))).willReturn(List.of(FAR));
        given(naverLocalSearchClient.search(eq("대쿠이 광진구"))).willReturn(List.of(NEAR));

        MapUrlCheckResponse result = registerService.checkMapUrl(NEW_URL, "대쿠이");

        assertThat(result.valid()).isTrue();
        assertThat(result.candidates()).extracting(PlaceCandidateResponse::name)
                .containsExactly(NEAR.name(), FAR.name());   // 지역 검색 결과가 앞
    }

    @Test
    void 등록도_회사_지역을_붙여_다시_검색한_후보로_할_수_있다() {
        given(naverLocalSearchClient.search(anyString())).willReturn(List.of());
        given(naverLocalSearchClient.search(eq("대쿠이"))).willReturn(List.of(FAR));
        given(naverLocalSearchClient.search(eq("대쿠이 광진구"))).willReturn(List.of(NEAR));

        Long id = registerService.registerRestaurant(request(NEW_URL, "대쿠이", "JAPANESE", NEAR), user01).restaurantId();

        assertThat(restaurantRepository.findById(id).orElseThrow().getAddress()).isEqualTo(NEAR.address());
    }

    @Test
    void 검증_등록할_수_있는_후보가_없으면_NO_ELIGIBLE_PLACE와_후보를_준다() {
        given(naverLocalSearchClient.search(anyString())).willReturn(List.of(FAR, SHOP));

        MapUrlCheckResponse result = registerService.checkMapUrl(NEW_URL, "대쿠이");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("NO_ELIGIBLE_PLACE");
        assertThat(result.message()).isEqualTo("등록할 수 있는 장소가 없습니다.");
        assertThat(result.candidates()).hasSize(2);   // 이유를 보여 주려고 후보는 그대로 준다
    }

    @Test
    void 검증_실패는_reason으로_구분한다() {
        assertReason(registerService.checkMapUrl("https://map.kakao.com/123", "대쿠이"),
                "INVALID_FORMAT", "네이버 지도 URL만 입력할 수 있습니다.");

        // 이 테스트 안에서 등록한 장소 (DB의 더미 데이터에 기대지 않게)
        registerService.registerRestaurant(request(NEW_URL, "대쿠이", "JAPANESE", NEAR), user01);
        assertReason(registerService.checkMapUrl(NEW_STANDARD_URL + "?c=15", "아무 이름"),
                "DUPLICATE", "이미 등록된 식당입니다.");

        given(naverLocalSearchClient.search(anyString())).willReturn(List.of());
        assertReason(registerService.checkMapUrl("https://map.naver.com/p/entry/place/123000000998", "없는가게"),
                "NOT_FOUND", "존재하지 않는 주소입니다.");
    }

    @Test
    void 검증_url이나_이름이_없으면_400() {
        String message = "URL과 식당 이름을 입력해 주세요.";
        assertError(() -> registerService.checkMapUrl(null, "대쿠이"), ErrorCode.INVALID_REQUEST, message);
        assertError(() -> registerService.checkMapUrl(NEW_URL, "   "), ErrorCode.INVALID_REQUEST, message);
        assertError(() -> registerService.checkMapUrl(NEW_URL, "가".repeat(31)), ErrorCode.INVALID_REQUEST, message);
    }

    // ===== 등록 =====

    @Test
    void 등록하면_고른_후보의_주소_좌표와_표준_URL로_저장한다() {
        Long restaurantId = registerService
                .registerRestaurant(request(NEW_URL, "  대쿠이  ", "JAPANESE", NEAR), user01)
                .restaurantId();

        Restaurant saved = restaurantRepository.findById(restaurantId).orElseThrow();
        assertThat(saved.getName()).isEqualTo("대쿠이 세종대점");        // 검색어가 아니라 네이버 공식 이름 (2026-11-25)
        assertThat(saved.getCategory()).isEqualTo(RestaurantCategory.JAPANESE);
        assertThat(saved.getMapUrl()).isEqualTo(NEW_STANDARD_URL);
        assertThat(saved.getAddress()).isEqualTo(NEAR.address());       // 첫 결과(FAR)가 아니라 고른 후보
        assertThat(saved.getLat()).isEqualTo(NEAR.lat());
        assertThat(saved.getLng()).isEqualTo(NEAR.lng());
        assertThat(saved.getMember().getMemberId()).isEqualTo(user01);
    }

    @Test
    void 등록_반경_밖_후보는_RESTAURANT_OUT_OF_RANGE() {
        assertError(() -> registerService.registerRestaurant(request(NEW_URL, "대쿠이", "JAPANESE", FAR), user01),
                ErrorCode.RESTAURANT_OUT_OF_RANGE, "회사에서 4km 안의 식당만 등록할 수 있습니다.");
    }

    @Test
    void 등록_음식점이_아닌_후보는_NOT_A_RESTAURANT() {
        assertError(() -> registerService.registerRestaurant(request(NEW_URL, "대쿠이", "ETC", SHOP), user01),
                ErrorCode.NOT_A_RESTAURANT, "음식점·카페만 등록할 수 있습니다.");
    }

    @Test
    void 등록_고른_후보를_다시_찾지_못하면_MAP_PLACE_NOT_FOUND() {
        RestaurantRegisterRequest unknown = new RestaurantRegisterRequest(
                NEW_URL, "대쿠이", "JAPANESE", "대쿠이 없는점", "서울특별시 어딘가 1");
        assertError(() -> registerService.registerRestaurant(unknown, user01), ErrorCode.MAP_PLACE_NOT_FOUND, null);

        given(naverLocalSearchClient.search(anyString())).willReturn(List.of());
        assertError(() -> registerService.registerRestaurant(request(NEW_URL, "없는가게", "KOREAN", NEAR), user01),
                ErrorCode.MAP_PLACE_NOT_FOUND, null);
    }

    @Test
    void 같은_장소는_링크_모양이_달라도_409() {
        registerService.registerRestaurant(request(NEW_URL, "대쿠이", "JAPANESE", NEAR), user01);

        assertError(() -> registerService.registerRestaurant(
                        request(NEW_STANDARD_URL + "/home", "대쿠이", "JAPANESE", NEAR), user01),
                ErrorCode.RESTAURANT_ALREADY_EXISTS, null);
        assertError(() -> registerService.registerRestaurant(
                        request(NEW_URL, "새 이름", "KOREAN", NEAR), user01),
                ErrorCode.RESTAURANT_ALREADY_EXISTS, null);
    }

    @Test
    void 구내식당_없는_카테고리_이름_길이_오류는_400() {
        assertError(() -> registerService.registerRestaurant(
                request(NEW_URL, "대쿠이", "CAFETERIA", NEAR), user01), ErrorCode.INVALID_REQUEST, null);
        assertError(() -> registerService.registerRestaurant(
                request(NEW_URL, "대쿠이", "PIZZA", NEAR), user01), ErrorCode.INVALID_REQUEST, null);
        assertError(() -> registerService.registerRestaurant(
                request(NEW_URL, "가".repeat(31), "KOREAN", NEAR), user01), ErrorCode.INVALID_REQUEST, null);
    }

    @Test
    void 등록_URL_형식_오류는_INVALID_MAP_URL() {
        assertError(() -> registerService.registerRestaurant(
                        request("https://map.kakao.com/123", "대쿠이", "KOREAN", NEAR), user01),
                ErrorCode.INVALID_MAP_URL, null);
    }

    @Test
    void 공식_이름이_30자를_넘으면_30자로_자르고_비어_있으면_입력한_이름을_쓴다() {
        String longName = "가".repeat(35);
        assertThat(RestaurantRegisterService.officialName("  " + longName + "  ", "대쿠이")).hasSize(30);
        assertThat(RestaurantRegisterService.officialName("  ", "대쿠이")).isEqualTo("대쿠이");
        assertThat(RestaurantRegisterService.officialName(null, "대쿠이")).isEqualTo("대쿠이");
    }

    // ===== 도우미 =====

    private static RestaurantRegisterRequest request(String url, String name, String category, NaverPlace picked) {
        return new RestaurantRegisterRequest(url, name, category, picked.name(), picked.address());
    }

    private void assertReason(MapUrlCheckResponse result, String reason, String message) {
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.message()).isEqualTo(message);
        assertThat(result.candidates()).isEmpty();
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
