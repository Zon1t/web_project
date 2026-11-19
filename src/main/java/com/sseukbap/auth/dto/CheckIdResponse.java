package com.sseukbap.auth.dto;

/** GET /api/auth/check-id 응답 data */
public record CheckIdResponse(String loginId, boolean available) {
}
