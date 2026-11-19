package com.sseukbap.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/parties/{partyId}/chats 요청
 * 1~200자, 앞뒤 공백을 지운 뒤 빈 값이면 안 된다. 실패 문구는 API_SPEC대로 "메시지를 입력해 주세요."
 */
public record PartyChatCreateRequest(

        @NotBlank(message = "메시지를 입력해 주세요.")
        @Size(max = 200, message = "메시지를 입력해 주세요.")
        String content
) {
    /** 앞뒤 공백 제거 (검증보다 먼저 실행됨) */
    public PartyChatCreateRequest {
        if (content != null) {
            content = content.trim();
        }
    }
}
