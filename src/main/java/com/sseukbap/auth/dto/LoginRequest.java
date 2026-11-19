package com.sseukbap.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/auth/login 요청.
 * 필수값만 검사한다. (형식이 틀린 아이디는 어차피 회원이 없으므로 LOGIN_FAILED로 처리됨)
 */
public record LoginRequest(

        @NotBlank(message = "아이디와 비밀번호를 입력해 주세요.")
        String loginId,

        @NotBlank(message = "아이디와 비밀번호를 입력해 주세요.")
        String password
) {
}
