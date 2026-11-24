package com.sseukbap.member.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/users/me/withdraw 요청 (C2, 2026-11-24). 본인 확인용 비밀번호 */
public record WithdrawRequest(
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {
}
