package com.sseukbap.auth.dto;

/**
 * 인증번호 요청 응답 data. 회원이 있든 없든 같은 값을 준다. (아이디가 있는지 드러내지 않음)
 *
 * @param email            보낸 주소 (입력값 그대로, 화면 안내용)
 * @param expiresInSeconds 인증번호 유효 시간(초). 화면의 남은 시간 표시용
 */
public record PasswordResetRequestResponse(String email, long expiresInSeconds) {
}
