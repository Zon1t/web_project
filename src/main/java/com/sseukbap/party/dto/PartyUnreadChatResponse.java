package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.common.enums.MealType;

import java.time.LocalDate;

/**
 * POST /api/parties/my/unread-chats 응답 한 줄 (헤더 종 '새 메시지' 알림, 2026-11-26)
 * 예: 세종원 · 오늘 점심 식사팟에 새 메시지 3개
 */
public record PartyUnreadChatResponse(
        Long partyId,
        String restaurantName,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate partyDate,
        MealType mealType,
        long unreadCount,
        Long lastChatId       // 이 팟의 마지막 메시지 번호 (화면이 '읽음' 위치로 저장)
) {
}
