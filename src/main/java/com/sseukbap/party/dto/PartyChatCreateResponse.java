package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/** POST /api/parties/{partyId}/chats 응답 data. FE는 전송 후 바로 조회(폴링 1회)를 한다. */
public record PartyChatCreateResponse(
        Long chatId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {
}
