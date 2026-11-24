package com.sseukbap.restaurant.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.client.NaverLocalSearchClient;
import com.sseukbap.restaurant.client.NaverPlace;
import com.sseukbap.restaurant.dto.MapUrlCheckResponse;
import com.sseukbap.restaurant.dto.MapUrlCheckResponse.Reason;
import com.sseukbap.restaurant.dto.PlaceCandidateResponse;
import com.sseukbap.restaurant.dto.PlaceCandidateResponse.BlockReason;
import com.sseukbap.restaurant.dto.RestaurantRegisterRequest;
import com.sseukbap.restaurant.dto.RestaurantRegisterResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 식당 등록과 네이버 지도 URL 검증. 기준: docs/API_SPEC.md 5장 (2026-11-24 변경 반영)
 *
 * 네이버에는 URL·장소 ID로 장소를 조회하는 공식 API가 없어서
 *   - 중복: URL에서 장소 ID를 찾아 만든 표준 URL로 판단 (MapUrlParser)
 *   - 주소·좌표: 식당 이름으로 지역 검색한 후보(최대 5개) 중 사용자가 고른 곳 (NaverLocalSearchClient)
 *
 * 등록할 수 없는 후보 (2026-11-24)
 *   - 네이버 분류가 음식점 계열이 아님 (PlaceCategoryPolicy)
 *   - 회사(구내식당 좌표)에서 restaurant.register-radius-km(기본 4km) 밖 (GeoDistance)
 */
@Service
@Transactional(readOnly = true)
public class RestaurantRegisterService {

    private static final int NAME_MAX = 30;

    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;
    private final MapUrlParser mapUrlParser;
    private final NaverLocalSearchClient naverLocalSearchClient;
    private final double radiusKm;

    public RestaurantRegisterService(RestaurantRepository restaurantRepository,
                                     MemberRepository memberRepository,
                                     MapUrlParser mapUrlParser,
                                     NaverLocalSearchClient naverLocalSearchClient,
                                     @Value("${restaurant.register-radius-km:4}") double radiusKm) {
        this.restaurantRepository = restaurantRepository;
        this.memberRepository = memberRepository;
        this.mapUrlParser = mapUrlParser;
        this.naverLocalSearchClient = naverLocalSearchClient;
        this.radiusKm = radiusKm;
    }

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
            return MapUrlCheckResponse.fail(Reason.INVALID_FORMAT, radiusKm);
        }

        // 3. 중복: 같은 장소가 이미 등록됐는지
        if (restaurantRepository.existsByMapUrl(standardUrl.get())) {
            return MapUrlCheckResponse.fail(Reason.DUPLICATE, radiusKm);
        }

        // 4. 이름으로 지역 검색 (최대 5개)
        List<NaverPlace> places = naverLocalSearchClient.search(trimmedName);
        if (places.isEmpty()) {
            return MapUrlCheckResponse.fail(Reason.NOT_FOUND, radiusKm);
        }

        // 5~6. 후보마다 거리·분류·등록 가능 여부
        Optional<double[]> office = officeLocation();
        List<PlaceCandidateResponse> candidates = places.stream()
                .map(place -> toCandidate(place, office))
                .toList();
        return MapUrlCheckResponse.of(radiusKm, candidates);
    }

    /** 식당 등록. 검증을 서버에서 다시 하고, 사용자가 고른 후보의 주소·좌표를 저장한다. */
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

        // 4. 이름으로 다시 검색해 고른 후보(이름·주소가 같은 곳)를 찾는다. 화면이 보낸 좌표는 믿지 않는다
        NaverPlace place = naverLocalSearchClient.search(name).stream()
                .filter(p -> Objects.equals(p.name(), request.placeName().trim())
                        && Objects.equals(p.address(), request.placeAddress().trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MAP_PLACE_NOT_FOUND));

        // 5. 음식점인지, 회사에서 반경 안인지 (check-map-url과 같은 규칙)
        PlaceCandidateResponse candidate = toCandidate(place, officeLocation());
        if (BlockReason.NOT_A_RESTAURANT.name().equals(candidate.blockReason())) {
            throw new BusinessException(ErrorCode.NOT_A_RESTAURANT);
        }
        if (BlockReason.OUT_OF_RANGE.name().equals(candidate.blockReason())) {
            throw new BusinessException(ErrorCode.RESTAURANT_OUT_OF_RANGE,
                    "회사에서 " + formatKm(radiusKm) + "km 안의 식당만 등록할 수 있습니다.");
        }

        // 6. 저장. 같은 장소를 동시에 등록하면 DB의 MAP_URL 중복 방지 규칙(UK)이 막아 준다 → 409
        Member member = memberRepository.getReferenceById(memberId); // 로그인 회원 (SELECT 없이 참조만)
        Restaurant restaurant = Restaurant.create(
                name, category, standardUrl, place.address(), place.lat(), place.lng(), member);
        try {
            restaurantRepository.saveAndFlush(restaurant);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.RESTAURANT_ALREADY_EXISTS);
        }

        // 7. 생성된 restaurantId
        return new RestaurantRegisterResponse(restaurant.getRestaurantId());
    }

    /** 검색 결과 1곳 → 후보 (거리, 추천 카테고리, 등록 가능 여부). 음식점이 아닌 이유를 거리보다 먼저 본다 */
    private PlaceCandidateResponse toCandidate(NaverPlace place, Optional<double[]> office) {
        Double distanceKm = office
                .map(o -> GeoDistance.round2(GeoDistance.km(o[0], o[1], place.lat(), place.lng())))
                .orElse(null);
        RestaurantCategory suggested = PlaceCategoryPolicy.suggest(place.category());

        BlockReason block = null;
        if (!PlaceCategoryPolicy.isFoodPlace(place.category())) {
            block = BlockReason.NOT_A_RESTAURANT;
        } else if (distanceKm != null && distanceKm > radiusKm) {
            block = BlockReason.OUT_OF_RANGE;
        }

        return new PlaceCandidateResponse(
                place.name(), place.address(), place.lat(), place.lng(), place.category(),
                distanceKm, suggested == null ? null : suggested.name(),
                block == null, block == null ? null : block.name());
    }

    /** 회사 위치 = 구내식당(CAFETERIA) 좌표. 없으면 거리 검사를 하지 않는다 */
    private Optional<double[]> officeLocation() {
        return restaurantRepository.findFirstByCategoryOrderByRestaurantIdAsc(RestaurantCategory.CAFETERIA)
                .filter(r -> r.getLat() != null && r.getLng() != null)
                .map(r -> new double[]{r.getLat(), r.getLng()});
    }

    /** 4.0 → "4", 2.5 → "2.5" */
    private static String formatKm(double km) {
        return (km == Math.floor(km)) ? String.valueOf((long) km) : String.valueOf(km);
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
