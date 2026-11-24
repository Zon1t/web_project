package com.sseukbap.admin.dto;

import com.sseukbap.restaurant.entity.Suggestion;
import com.sseukbap.restaurant.entity.SuggestionStatus;

import java.time.LocalDateTime;

/** 관리자 수정 제안 목록의 한 줄 (2026-11-24 A3) */
public record AdminSuggestionResponse(
        Long suggestionId,
        Long restaurantId,
        String restaurantName,
        String nickname,
        String content,
        SuggestionStatus status,
        LocalDateTime createdAt
) {

    public static AdminSuggestionResponse from(Suggestion s) {
        return new AdminSuggestionResponse(
                s.getSuggestionId(), s.getRestaurant().getRestaurantId(), s.getRestaurant().getName(),
                s.getMember().getNickname(), s.getContent(), s.getStatus(), s.getCreatedAt());
    }
}
