package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.party.entity.MessageType;
import com.sseukbap.party.entity.PartyChat;

import java.time.LocalDateTime;

/** 대화 메시지 한 개 (GET /api/parties/{partyId}/chats 의 chats[]) */
public record PartyChatResponse(
        Long chatId,
        MessageType type,         // TEXT / SYSTEM
        String nickname,          // 시스템 메시지는 null
        String content,
        boolean mine,             // 로그인 회원이 보낸 메시지인지
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {

    public static PartyChatResponse of(PartyChat chat, Long loginMemberId) {
        boolean isSystem = chat.getMember() == null;
        return new PartyChatResponse(
                chat.getChatId(),
                chat.getMessageType(),
                isSystem ? null : chat.getMember().getNickname(),
                chat.getContent(),
                !isSystem && chat.getMember().getMemberId().equals(loginMemberId),
                chat.getCreatedAt()
        );
    }
}
