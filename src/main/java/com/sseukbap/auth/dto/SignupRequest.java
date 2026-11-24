package com.sseukbap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/signup 요청.
 * 검증 실패 시 문구는 API_SPEC대로 모두 "입력값을 확인해 주세요."
 */
public record SignupRequest(

        @NotBlank(message = "입력값을 확인해 주세요.")
        @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "입력값을 확인해 주세요.")
        String loginId,

        @NotBlank(message = "입력값을 확인해 주세요.")
        @Size(min = 8, max = 20, message = "입력값을 확인해 주세요.")
        String password,

        @NotBlank(message = "입력값을 확인해 주세요.")
        String passwordConfirm,

        @NotBlank(message = "입력값을 확인해 주세요.")
        @Size(min = 2, max = 10, message = "입력값을 확인해 주세요.")
        String nickname
) {
    /**
     * 검증보다 먼저 실행된다.
     * - 아이디는 앞뒤 공백 제거 + 소문자로 통일해 저장한다. (2026-11-24: User01과 user01이 따로 가입되던 문제)
     * - 닉네임은 앞뒤 공백을 제거한 값으로 검증·저장한다.
     */
    public SignupRequest {
        if (loginId != null) {
            loginId = loginId.trim().toLowerCase(java.util.Locale.ROOT);
        }
        if (nickname != null) {
            nickname = nickname.trim();
        }
    }
}
