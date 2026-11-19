package com.sseukbap.restaurant.dto;

import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.RestaurantListRow;

/** GET /api/restaurants/list 응답 data의 항목 1개 */
public record RestaurantListResponse(
        Long restaurantId,
        String name,
        RestaurantCategory category,
        boolean cafeteria,
        long thumbsCount30d,
        long reviewCount,
        Double lat,
        Double lng
) {

    public static RestaurantListResponse from(RestaurantListRow row) {
        RestaurantCategory category = RestaurantCategory.valueOf(row.getCategory());
        return new RestaurantListResponse(
                row.getRestaurantId(),
                row.getName(),
                category,
                category == RestaurantCategory.CAFETERIA,
                row.getThumbsCount30d(),
                row.getReviewCount(),
                row.getLat(),
                row.getLng()
        );
    }
}
