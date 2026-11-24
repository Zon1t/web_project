package com.sseukbap.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/find-id 요청 (2026-11-24). 형식 검사는 서비스(EmailPolicy)에서 한다 */
public record FindIdRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        String email
) {
}
