package com.sseukbap.member.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PATCH /api/users/me/password 요청 (2026-11-24, C1).
 * 필수값만 여기서 보고, 규칙·일치 여부는 AccountService에서 검사한다. (규칙별 문구를 주기 위해)
 */
public record PasswordChangeRequest(
        @NotBlank(message = "입력값을 확인해 주세요.")
        String currentPassword,

        @NotBlank(message = "입력값을 확인해 주세요.")
        String newPassword,

        @NotBlank(message = "입력값을 확인해 주세요.")
        String newPasswordConfirm
) {
}
