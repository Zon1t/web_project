package com.sseukbap.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 식당 등록 요청
 *
 * 필수값은 여기서 확인하고(@Valid), 이름 길이(앞뒤 공백 제거 후 1~30자)·카테고리 값은 Service에서 확인한다.
 *
 * @param mapUrl   네이버 지도 URL (naver.me 또는 map.naver.com)
 * @param name     식당 이름
 * @param category 카테고리 값 (CAFETERIA 제외)
 */
public record RestaurantRegisterRequest(
        @NotBlank(message = "입력값을 확인해 주세요.") String mapUrl,
        @NotBlank(message = "입력값을 확인해 주세요.") String name,
        @NotBlank(message = "입력값을 확인해 주세요.") String category
) {
}
