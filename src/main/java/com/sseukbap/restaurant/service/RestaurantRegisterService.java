package com.sseukbap.restaurant.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.client.NaverLocalSearchClient;
import com.sseukbap.restaurant.client.NaverPlace;
import com.sseukbap.restaurant.dto.MapPlaceResponse;
import com.sseukbap.restaurant.dto.MapUrlCheckResponse;
import com.sseukbap.restaurant.dto.MapUrlCheckResponse.Reason;
import com.sseukbap.restaurant.dto.RestaurantRegisterRequest;
import com.sseukbap.restaurant.dto.RestaurantRegisterResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;

/**
 * 식당 등록과 네이버 지도 URL 검증. 기준: docs/API_SPEC.md 5장 (2026-11-19 변경 반영)
 *
 * 네이버에는 URL·장소 ID로 장소를 조회하는 공식 API가 없어서
 *   - 중복: URL에서 장소 ID를 찾아 만든 표준 URL로 판단 (MapUrlParser)
 *   - 주소·좌표: 식당 이름으로 지역 검색한 첫 결과 (NaverLocalSearchClient)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantRegisterService {

    private static final int NAME_MAX = 30;

    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;
    private final MapUrlParser mapUrlParser;
    private final NaverLocalSearchClient naverLocalSearchClient;

    /** 네이버 지도 URL 검증 (등록 화면에서 입력 중 호출). 실패도 200 + reason */
    public MapUrlCheckResponse checkMapUrl(String url, String name) {
        // 1. url, name 필수값·길이
        String trimmedName = (name == null) ? "" : name.trim();
        if (url == null || url.isBlank() || trimmedName.isEmpty() || trimmedName.length() > NAME_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "URL과 식당 이름을 입력해 주세요.");
        }

        // 2. 형식: 장소 ID를 찾을 수 있는 네이버 지도 링크인지
        Optional<String> standardUrl = mapUrlParser.toStandardUrl(url);
        if (standardUrl.isEmpty()) {
            return MapUrlCheckResponse.fail(Reason.INVALID_FORMAT);
        }

        // 3. 중복: 같은 장소가 이미 등록됐는지
        if (restaurantRepository.existsByMapUrl(standardUrl.get())) {
            return MapUrlCheckResponse.fail(Reason.DUPLICATE);
        }

        // 4~5. 이름으로 지역 검색 → 첫 결과
        return naverLocalSearchClient.searchFirst(trimmedName)
                .map(place -> MapUrlCheckResponse.ok(MapPlaceResponse.from(place)))
                .orElseGet(() -> MapUrlCheckResponse.fail(Reason.NOT_FOUND));
    }

    /** 식당 등록. 검증을 서버에서 다시 하고, 지역 검색으로 찾은 주소·좌표를 함께 저장한다. */
    @Transactional
    public RestaurantRegisterResponse registerRestaurant(RestaurantRegisterRequest request, Long memberId) {
        // 1. 이름 길이, 카테고리 (구내식당은 초기 데이터로만 제공 → 등록 불가)
        String name = request.name().trim();
        RestaurantCategory category = parseRegistrableCategory(request.category());
        if (name.isEmpty() || name.length() > NAME_MAX || category == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 2. 형식 → 표준 URL
        String standardUrl = mapUrlParser.toStandardUrl(request.mapUrl())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_MAP_URL));

        // 3. 중복
        if (restaurantRepository.existsByMapUrl(standardUrl)) {
            throw new BusinessException(ErrorCode.RESTAURANT_ALREADY_EXISTS);
        }

        // 4. 이름으로 지역 검색
        NaverPlace place = naverLocalSearchClient.searchFirst(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.MAP_PLACE_NOT_FOUND));

        // 5. 저장. 같은 장소를 동시에 등록하면 DB의 MAP_URL 중복 방지 규칙(UK)이 막아 준다 → 409
        Member member = memberRepository.getReferenceById(memberId); // 로그인 회원 (SELECT 없이 참조만)
        Restaurant restaurant = Restaurant.create(
                name, category, standardUrl, place.address(), place.lat(), place.lng(), member);
        try {
            restaurantRepository.saveAndFlush(restaurant);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.RESTAURANT_ALREADY_EXISTS);
        }

        // 6. 생성된 restaurantId
        return new RestaurantRegisterResponse(restaurant.getRestaurantId());
    }

    /** 등록할 수 있는 카테고리면 enum, 아니면(없는 값, CAFETERIA) null */
    private RestaurantCategory parseRegistrableCategory(String value) {
        return Arrays.stream(RestaurantCategory.values())
                .filter(category -> category != RestaurantCategory.CAFETERIA)
                .filter(category -> category.name().equals(value))
                .findFirst()
                .orElse(null);
    }
}
