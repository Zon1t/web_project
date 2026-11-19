package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.common.enums.MealType;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.restaurant.entity.RestaurantCategory;

import java.time.LocalTime;

/**
 * 식사팟 목록의 한 줄 (GET /api/parties/list 의 parties[])
 * FE 버튼: joined=true → [상세 보기] / RECRUITING·SCHEDULED → [참여하기] / FULL·CLOSED → [마감]
 */
public record PartyListItemResponse(
        Long partyId,
        Long restaurantId,
        String restaurantName,
        RestaurantCategory category,
        MealType mealType,
        @JsonFormat(pattern = "HH:mm")
        LocalTime time,          // 기준 시간: 확정 시간이 있으면 확정 시간, 없으면 예정 시간
        int memberCount,
        int capacity,
        PartyStatus status,
        String memo,
        boolean joined           // 로그인 회원이 참여 중인지
) {

    public static PartyListItemResponse of(MealParty party, int memberCount, boolean joined) {
        return new PartyListItemResponse(
                party.getPartyId(),
                party.getRestaurant().getRestaurantId(),
                party.getRestaurant().getName(),
                party.getRestaurant().getCategory(),
                party.getMealType(),
                party.getBaseTime(),
                memberCount,
                party.getCapacity(),
                party.getStatus(),
                party.getMemo(),
                joined
        );
    }
}
