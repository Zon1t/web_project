package com.sseukbap.restaurant.dto;

import com.sseukbap.restaurant.entity.RestaurantCategory;

/**
 * 룰렛 당첨 식당 요약 (결과 카드용)
 */
public record RouletteWinnerResponse(
        Long restaurantId,
        String name,
        RestaurantCategory category,
        long thumbsCount30d
) {
}
