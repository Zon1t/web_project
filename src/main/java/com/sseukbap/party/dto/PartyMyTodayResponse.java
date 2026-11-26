package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.common.enums.MealType;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * GET /api/parties/my/today 응답 data의 한 줄 (메인 '참여 중인 식사팟' 카드)
 * 11/20 변경: 오늘 ~ 14일 후 팟이 들어오므로 partyDate를 추가했다. (FE는 오늘 팟을 강조해서 보여 준다)
 */
public record PartyMyTodayResponse(
        Long partyId,
        String restaurantName,
        LocalDate partyDate,
        MealType mealType,
        @JsonFormat(pattern = "HH:mm")
        LocalTime time,          // 기준 시간: 확정 시간이 있으면 확정 시간, 없으면 예정 시간
        PartyStatus status,
        int memberCount,
        int confirmedCount,
        boolean myConfirmed,
        String meetPlace,        // 일정 미확정이면 null
        boolean isPrivate        // 비공개 식사팟인지 (11/25)
) {

    public static PartyMyTodayResponse of(MealParty party, int memberCount, int confirmedCount, boolean myConfirmed) {
        return new PartyMyTodayResponse(
                party.getPartyId(),
                party.getRestaurant().getName(),
                party.getPartyDate(),
                party.getMealType(),
                party.getBaseTime(),
                party.getStatus(),
                memberCount,
                confirmedCount,
                myConfirmed,
                party.getMeetPlace(),
                party.isPrivateParty()
        );
    }
}
