package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.common.enums.MealType;
import com.sseukbap.party.entity.MealParty;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * GET /api/parties/my/action-pending 응답 data의 한 줄 (헤더 종 알림, 2026-11-23)
 *
 * action
 *   - SET_SCHEDULE: 내가 방장인데 아직 일정을 정하지 않은 팟 → "일정을 정해 주세요"
 *   - CONFIRM:      일정이 정해졌는데 내가 아직 참석 확정을 안 한 팟 → "참석을 확정해 주세요"
 */
public record PartyActionPendingResponse(
        Long partyId,
        PartyAction action,
        String restaurantName,
        LocalDate partyDate,
        MealType mealType,
        @JsonFormat(pattern = "HH:mm")
        LocalTime time,          // 기준 시간: 확정 시간이 있으면 확정 시간, 없으면 예정 시간
        String meetPlace         // 일정 미확정이면 null
) {

    public enum PartyAction {
        SET_SCHEDULE,
        CONFIRM
    }

    public static PartyActionPendingResponse of(MealParty party, PartyAction action) {
        return new PartyActionPendingResponse(
                party.getPartyId(),
                action,
                party.getRestaurant().getName(),
                party.getPartyDate(),
                party.getMealType(),
                party.getBaseTime(),
                party.getMeetPlace()
        );
    }
}
