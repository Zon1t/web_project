package com.sseukbap.restaurant.dto;

/** 식당 등록 응답. FE는 이 식당의 상세 화면으로 이동한다. */
public record RestaurantRegisterResponse(
        Long restaurantId
) {
}
