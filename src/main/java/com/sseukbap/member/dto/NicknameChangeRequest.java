package com.sseukbap.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PATCH /api/users/me/nickname 요청 (2026-11-24, C1). 회원가입과 같은 규칙: 앞뒤 공백 제거 후 2~10자 */
public record NicknameChangeRequest(
        @NotBlank(message = "닉네임은 2~10자로 입력해 주세요.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자로 입력해 주세요.")
        String nickname
) {

    /** 앞뒤 공백을 제거한 값으로 검증·저장한다. (검증보다 먼저 실행됨) */
    public NicknameChangeRequest {
        if (nickname != null) {
            nickname = nickname.trim();
        }
    }
}
