package com.sseukbap.admin.dto;

/** PATCH /api/admin/reviews/{id} 요청: hidden = true 숨김 / false 숨김 해제 (2026-11-24 A3) */
public record AdminReviewUpdateRequest(Boolean hidden) {
}
