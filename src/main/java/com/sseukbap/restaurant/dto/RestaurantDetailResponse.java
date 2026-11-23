package com.sseukbap.restaurant.dto;

import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;

import java.util.List;

/** GET /api/restaurants/{restaurantId} 응답 data */
public record RestaurantDetailResponse(
        Long restaurantId,
        String name,
        RestaurantCategory category,
        boolean cafeteria,
        String mapUrl,
        String address,
        Double lat,                     // 위도, 좌표가 없으면 null (2026-11-23, 상세 작은 지도용)
        Double lng,                     // 경도
        List<TopTagResponse> topTags,   // 0~3개
        long thumbsCount30d,
        long interestCount,
        long reviewCount,
        boolean interested              // 로그인 회원의 관심 등록 여부
) {

    public static RestaurantDetailResponse of(Restaurant restaurant, List<TopTagResponse> topTags,
                                              long thumbsCount30d, long interestCount,
                                              long reviewCount, boolean interested) {
        return new RestaurantDetailResponse(
                restaurant.getRestaurantId(),
                restaurant.getName(),
                restaurant.getCategory(),
                restaurant.isCafeteria(),
                restaurant.getMapUrl(),
                restaurant.getAddress(),
                restaurant.getLat(),
                restaurant.getLng(),
                topTags,
                thumbsCount30d,
                interestCount,
                reviewCount,
                interested
        );
    }
}
