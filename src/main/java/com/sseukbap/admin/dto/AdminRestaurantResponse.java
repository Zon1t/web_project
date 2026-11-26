package com.sseukbap.admin.dto;

import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;

import java.time.LocalDateTime;

/**
 * 관리자 식당 목록의 한 줄 (GET /api/admin/restaurants, 2026-11-24 A3)
 *
 * @param distanceKm   회사(구내식당 좌표)까지 거리, 소수 둘째 자리. 좌표가 없으면 null
 * @param outOfRange   식당 등록 반경(4km) 밖이면 true
 * @param registeredBy 등록자 닉네임. 구내식당 초기 데이터는 null
 */
public record AdminRestaurantResponse(
        Long restaurantId,
        String name,
        RestaurantCategory category,
        String address,
        Double distanceKm,
        boolean outOfRange,
        boolean hidden,
        boolean cafeteria,
        long reviewCount,
        String registeredBy,
        LocalDateTime createdAt
) {

    public static AdminRestaurantResponse of(Restaurant r, Double distanceKm, double radiusKm, long reviewCount) {
        return new AdminRestaurantResponse(
                r.getRestaurantId(), r.getName(), r.getCategory(), r.getAddress(),
                distanceKm, distanceKm != null && distanceKm > radiusKm,
                r.isHidden(), r.isCafeteria(), reviewCount,
                r.getMember() == null ? null : r.getMember().getNickname(),
                r.getCreatedAt());
    }
}
