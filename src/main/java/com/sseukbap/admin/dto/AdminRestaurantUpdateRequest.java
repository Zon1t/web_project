package com.sseukbap.admin.dto;

/**
 * PATCH /api/admin/restaurants/{id} 요청 (2026-11-24 A3). 보낸 값만 바꾼다. 둘 다 없으면 400
 *
 * @param hidden   true 숨김 / false 숨김 해제
 * @param category 바꿀 카테고리 (KOREAN 등). CAFETERIA는 안 됨
 */
public record AdminRestaurantUpdateRequest(Boolean hidden, String category) {
}
