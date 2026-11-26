package com.sseukbap.member.dto;

import com.sseukbap.restaurant.entity.RestaurantCategory;

import java.time.LocalDateTime;

/**
 * 내가 등록한 식당 한 줄 (GET /api/users/me/restaurants)
 */
public record MyRestaurantResponse(
        Long restaurantId,
        String name,
        RestaurantCategory category,
        LocalDateTime createdAt
) {
}
