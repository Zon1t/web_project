package com.sseukbap.restaurant;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.client.NaverLocalSearchClient;
import com.sseukbap.restaurant.client.NaverPlace;
import com.sseukbap.restaurant.dto.MapUrlCheckResponse;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 식당 등록·URL 검증 확인.
 *
 * 네이버 검색은 실제로 부르지 않고 가짜(@MockitoBean)로 바꾼다. → 키가 없는 PC에서도 테스트가 통과한다.
 * 더미데이터 식당의 MAP_URL은 https://map.naver.com/p/entry/place/9000000001 ~ 0010 (이미 등록됨)
 */
@SpringBootTest
@Transactional
class RestaurantRegisterServiceTest {

    // 실제 네이버에 없는 장소 ID (9로 시작하는 10자리 더미 번호대와도 겹치지 않게). 실제 식당 링크를 쓰면
    // 누군가 그 식당을 화면에서 등록했을 때 테스트가 '이미 등록됨'으로 깨진다.
    private static final String NEW_URL =
            "https://map.naver.com/p/search/%EB%8C%80%EC%BF%A0%EC%9D%B4/place/123000000999?c=15.00,0,0,0,dh";
    private static final String NEW_STANDARD_URL = "https://map.naver.com/p/entry/place/123000000999";
    private static final String REGISTERED_URL = "https://map.naver.com/p/entry/place/9000000001";
    private static final NaverPlace PLACE =
            new NaverPlace("대쿠이 본점", "대구광역시 남구 중앙대로51길 38-2", 35.8563052, 128.5885117);

    @Autowired RestaurantRegisterService registerService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;

    @MockitoBean NaverLocalSearchClient naverLocalSearchClient;

    Long user01;

    @BeforeEach
    void setUp() {
        user01 = memberRepository.findByLoginId("user01").map(Member::getMemberId).orElseThrow();
        given(naverLocalSearchClient.searchFirst(anyString())).willReturn(Optional.of(PLACE));
    }

    // ===== URL 검증 =====

    @Test
    void 검증_정상이면_찾은_장소를_준다() {
        MapUrlCheckResponse result = registerService.checkMapUrl(NEW_URL, " 대쿠이 ");

        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.place().name()).isEqualTo("대쿠이 본점");
        assertThat(result.place().lat()).isEqualTo(35.8563052);
    }

    @Test
    void 검증_실패는_reason으로_구분한다() {
        assertReason(registerService.checkMapUrl("https://map.kakao.com/123", "대쿠이"),
                "INVALID_FORMAT", "네이버 지도 URL만 입력할 수 있습니다.");
        assertReason(registerService.checkMapUrl(REGISTERED_URL + "?c=15", "아무 이름"),
                "DUPLICATE", "이미 등록된 식당입니다.");

        given(naverLocalSearchClient.searchFirst(anyString())).willReturn(Optional.empty());
        assertReason(registerService.checkMapUrl(NEW_URL, "없는가게"),
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
    void 등록하면_표준_URL과_찾은_주소_좌표로_저장한다() {
        Long restaurantId = registerService
                .registerRestaurant(new RestaurantRegisterRequest(NEW_URL, "  대쿠이  ", "JAPANESE"), user01)
                .restaurantId();

        Restaurant saved = restaurantRepository.findById(restaurantId).orElseThrow();
        assertThat(saved.getName()).isEqualTo("대쿠이");                 // 사용자가 입력한 이름 (공백 제거)
        assertThat(saved.getCategory()).isEqualTo(RestaurantCategory.JAPANESE);
        assertThat(saved.getMapUrl()).isEqualTo(NEW_STANDARD_URL);
        assertThat(saved.getAddress()).isEqualTo(PLACE.address());
        assertThat(saved.getLat()).isEqualTo(PLACE.lat());
        assertThat(saved.getLng()).isEqualTo(PLACE.lng());
        assertThat(saved.getMember().getMemberId()).isEqualTo(user01);
    }

    @Test
    void 같은_장소는_링크_모양이_달라도_409() {
        registerService.registerRestaurant(new RestaurantRegisterRequest(NEW_URL, "대쿠이", "JAPANESE"), user01);

        assertError(() -> registerService.registerRestaurant(
                        new RestaurantRegisterRequest(NEW_STANDARD_URL + "/home", "대쿠이", "JAPANESE"), user01),
                ErrorCode.RESTAURANT_ALREADY_EXISTS, null);
        assertError(() -> registerService.registerRestaurant(
                        new RestaurantRegisterRequest(REGISTERED_URL, "새 이름", "KOREAN"), user01),
                ErrorCode.RESTAURANT_ALREADY_EXISTS, null);
    }

    @Test
    void 구내식당_없는_카테고리_이름_길이_오류는_400() {
        assertError(() -> registerService.registerRestaurant(
                new RestaurantRegisterRequest(NEW_URL, "대쿠이", "CAFETERIA"), user01), ErrorCode.INVALID_REQUEST, null);
        assertError(() -> registerService.registerRestaurant(
                new RestaurantRegisterRequest(NEW_URL, "대쿠이", "PIZZA"), user01), ErrorCode.INVALID_REQUEST, null);
        assertError(() -> registerService.registerRestaurant(
                new RestaurantRegisterRequest(NEW_URL, "가".repeat(31), "KOREAN"), user01), ErrorCode.INVALID_REQUEST, null);
    }

    @Test
    void 등록_URL_형식_오류는_INVALID_MAP_URL_검색_결과_없으면_MAP_PLACE_NOT_FOUND() {
        assertError(() -> registerService.registerRestaurant(
                        new RestaurantRegisterRequest("https://map.kakao.com/123", "대쿠이", "KOREAN"), user01),
                ErrorCode.INVALID_MAP_URL, null);

        given(naverLocalSearchClient.searchFirst(anyString())).willReturn(Optional.empty());
        assertError(() -> registerService.registerRestaurant(
                        new RestaurantRegisterRequest(NEW_URL, "없는가게", "KOREAN"), user01),
                ErrorCode.MAP_PLACE_NOT_FOUND, null);
    }

    // ===== 도우미 =====

    private void assertReason(MapUrlCheckResponse result, String reason, String message) {
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.message()).isEqualTo(message);
        assertThat(result.place()).isNull();
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
