package com.sseukbap.admin.dto;

import java.util.List;

/** GET /api/admin/restaurants 응답 data. radiusKm: 식당 등록 반경 (2026-11-24 A3) */
public record AdminRestaurantListResponse(double radiusKm, List<AdminRestaurantResponse> items) {
}
