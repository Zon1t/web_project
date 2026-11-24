package com.sseukbap.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/password-reset/confirm 요청 (2026-11-24). 규칙 검사는 서비스에서 한다 */
public record PasswordResetConfirmRequest(
        @NotBlank(message = "입력값을 확인해 주세요.")
        String loginId,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        String code,

        @NotBlank(message = "입력값을 확인해 주세요.")
        String newPassword,

        @NotBlank(message = "입력값을 확인해 주세요.")
        String newPasswordConfirm
) {

    public PasswordResetConfirmRequest {
        if (code != null) {
            code = code.trim();
        }
    }
}
