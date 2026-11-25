package com.sseukbap.auth.dto;

/** GET /api/auth/check-email 응답 data (2026-11-25 T3). email = 소문자로 정리한 값 */
public record CheckEmailResponse(String email, boolean available) {
}
