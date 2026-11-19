package com.sseukbap.restaurant.dto;

import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;

/** GET /api/restaurants/autocomplete 응답 data의 항목 1개 */
public record RestaurantAutocompleteResponse(
        Long restaurantId,
        String name,
        RestaurantCategory category
) {

    public static RestaurantAutocompleteResponse from(Restaurant restaurant) {
        return new RestaurantAutocompleteResponse(
                restaurant.getRestaurantId(),
                restaurant.getName(),
                restaurant.getCategory()
        );
    }
}
