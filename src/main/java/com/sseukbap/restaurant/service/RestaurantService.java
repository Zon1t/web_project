package com.sseukbap.restaurant.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.restaurant.dto.RestaurantAutocompleteResponse;
import com.sseukbap.restaurant.dto.RestaurantDetailResponse;
import com.sseukbap.restaurant.dto.RestaurantListResponse;
import com.sseukbap.restaurant.dto.TopTagResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.InterestRepository;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.review.repository.ReviewRepository;
import com.sseukbap.review.repository.ReviewTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 식당 조회: 목록(검색·필터), 상세, 이름 자동완성.
 * 기준: docs/API_SPEC.md 5장
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {

    private static final int KEYWORD_MAX = 30;
    private static final int AUTOCOMPLETE_LIMIT = 5;
    private static final int TOP_TAG_LIMIT = 3;
    private static final int THUMBS_PERIOD_DAYS = 30;   // 최근 30일 (오늘 포함)

    private final RestaurantRepository restaurantRepository;
    private final InterestRepository interestRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewTagRepository reviewTagRepository;

    /** 식당 목록 조회 (검색·필터·지도). 페이징 없이 전체를 돌려준다. */
    public List<RestaurantListResponse> getRestaurantList(String keyword, String category) {
        // 1. 검색 조건 검증: 없는 카테고리, 검색어 30자 초과
        String trimmedKeyword = (keyword == null) ? "" : keyword.trim();
        if (trimmedKeyword.length() > KEYWORD_MAX || !isValidCategory(category)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "검색 조건을 확인해 주세요.");
        }

        // 2~4. 조건 조회 + 30일 따봉 수·리뷰 수 집계 + 정렬 (쿼리 한 번)
        LocalDate today = LocalDate.now();
        return restaurantRepository.findListRows(
                        trimmedKeyword.isEmpty() ? null : escapeLike(trimmedKeyword),
                        isBlank(category) ? null : category,
                        thumbsFromDate(today),
                        today.plusDays(1))
                .stream()
                .map(RestaurantListResponse::from)
                .toList();
    }

    /** 식당 상세 조회. 리뷰 목록은 별도 API(페이징)로 조회한다. */
    public RestaurantDetailResponse getRestaurant(Long restaurantId, Long memberId) {
        // 1. 식당 조회
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        // 2. 태그 TOP 3 (동수면 tagId 오름차순, 선택된 적 없는 태그 제외)
        List<TopTagResponse> topTags =
                reviewTagRepository.findTopTags(restaurantId, PageRequest.of(0, TOP_TAG_LIMIT));

        // 3. 30일 따봉 수, 관심 수, 리뷰 수
        LocalDate today = LocalDate.now();
        long thumbsCount30d = restaurantRepository.countThumbsUp(
                restaurantId, thumbsFromDate(today), today.plusDays(1));
        long interestCount = interestRepository.countByRestaurant_RestaurantId(restaurantId);
        long reviewCount = reviewRepository.countByRestaurant_RestaurantId(restaurantId);

        // 4. 내 관심 여부
        boolean interested =
                interestRepository.existsByRestaurant_RestaurantIdAndMember_MemberId(restaurantId, memberId);

        return RestaurantDetailResponse.of(
                restaurant, topTags, thumbsCount30d, interestCount, reviewCount, interested);
    }

    /** 식당 이름 자동완성 (식당 등록 중복 방지, 식사팟 생성 식당 선택에서 사용) */
    public List<RestaurantAutocompleteResponse> getAutocompleteList(String keyword) {
        // 1. 검색어 검증: 앞뒤 공백 제거 후 1~30자
        String trimmed = (keyword == null) ? "" : keyword.trim();
        if (trimmed.isEmpty() || trimmed.length() > KEYWORD_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "검색어를 입력해 주세요.");
        }

        // 2~4. 부분 일치 조회, 시작 일치 우선 → 가나다순, 최대 5개
        return restaurantRepository
                .findForAutocomplete(escapeLike(trimmed), PageRequest.of(0, AUTOCOMPLETE_LIMIT))
                .stream()
                .map(RestaurantAutocompleteResponse::from)
                .toList();
    }

    // ===== 내부 도우미 =====

    /** 최근 30일(오늘 포함)의 시작일 = 오늘 - 29일 */
    private LocalDate thumbsFromDate(LocalDate today) {
        return today.minusDays(THUMBS_PERIOD_DAYS - 1);
    }

    /** 카테고리가 비어 있거나(전체) RestaurantCategory 값 중 하나면 true */
    private boolean isValidCategory(String category) {
        if (isBlank(category)) {
            return true;
        }
        return Arrays.stream(RestaurantCategory.values())
                .anyMatch(value -> value.name().equals(category));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * LIKE 검색에서 %, _ 를 특수문자가 아닌 글자로 찾도록 '\'를 붙인다.
     * 예) "100%" → "100\%"  (쿼리에서 ESCAPE '\' 사용)
     */
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
