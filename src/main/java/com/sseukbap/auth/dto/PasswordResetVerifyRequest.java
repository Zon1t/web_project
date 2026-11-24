package com.sseukbap.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/password-reset/verify 요청 (2026-11-24): 인증번호만 먼저 확인 */
public record PasswordResetVerifyRequest(
        @NotBlank(message = "입력값을 확인해 주세요.")
        String loginId,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        String code
) {

    public PasswordResetVerifyRequest {
        if (code != null) {
            code = code.trim();
        }
    }
}
