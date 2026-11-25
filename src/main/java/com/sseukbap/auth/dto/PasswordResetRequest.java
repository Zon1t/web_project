package com.sseukbap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/auth/password-reset/request 요청. (2026-11-25 T3: 아이디 + 이메일 → 이메일만)
 * 등록된 이메일이면 인증번호를 보낸다. 없어도 응답은 같다.
 */
public record PasswordResetRequest(
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
