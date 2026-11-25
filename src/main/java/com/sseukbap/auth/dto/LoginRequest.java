package com.sseukbap.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/auth/login 요청. (2026-11-25 T3: 아이디 또는 회사 이메일)
 * '@'가 있으면 이메일로, 없으면 아이디로 찾는다.
 * 필수값만 검사한다. (형식이 틀린 값은 어차피 회원이 없으므로 LOGIN_FAILED로 처리됨)
 */
public record LoginRequest(

        @NotBlank(message = "아이디와 비밀번호를 입력해 주세요.")
        String loginId,

        @NotBlank(message = "아이디와 비밀번호를 입력해 주세요.")
        String password
) {
}
