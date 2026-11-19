package com.sseukbap.member.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.common.enums.MealType;
import com.sseukbap.party.entity.PartyStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 참여한 식사팟 한 줄 (GET /api/users/me/parties)
 * time은 기준 시간 (확정 시간이 있으면 확정 시간, 없으면 예정 시간)
 * host: 내가 방장인지, confirmed: 내가 참여 확정(2차)했는지
 */
public record MyPartyResponse(
        Long partyId,
        LocalDate partyDate,
        MealType mealType,
        @JsonFormat(pattern = "HH:mm") LocalTime time,
        Long restaurantId,
        String restaurantName,
        PartyStatus status,
        boolean host,
        boolean confirmed
) {
}
