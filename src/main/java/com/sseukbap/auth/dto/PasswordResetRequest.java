package com.sseukbap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/password-reset/request 요청 (2026-11-24).
 * 이메일은 형식만 본다. (회원 이메일 컬럼이 생기면 등록한 이메일과 같은지도 확인할 예정)
 */
public record PasswordResetRequest(
        @NotBlank(message = "아이디를 입력해 주세요.")
        String loginId,

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Size(max = 100, message = "이메일 형식을 확인해 주세요.")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "이메일 형식을 확인해 주세요.")
        String email
) {

    public PasswordResetRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
