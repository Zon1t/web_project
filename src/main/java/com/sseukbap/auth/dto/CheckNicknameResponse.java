package com.sseukbap.auth.dto;

/** GET /api/auth/check-nickname 응답 data. nickname은 앞뒤 공백을 제거한 값 */
public record CheckNicknameResponse(String nickname, boolean available) {
}
