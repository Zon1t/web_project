package com.sseukbap.party.dto;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.party.entity.MealParty;

import java.time.LocalDate;

/**
 * GET /api/parties/my/thumbs-pending 응답 data (메인 상단 따봉 배너)
 * 대상이 없으면 data 자체가 null이다. (FE는 배너를 숨김)
 */
public record PartyThumbsPendingResponse(
        Long partyId,
        Long restaurantId,
        String restaurantName,
        LocalDate partyDate,
        MealType mealType
) {

    public static PartyThumbsPendingResponse from(MealParty party) {
        return new PartyThumbsPendingResponse(
                party.getPartyId(),
                party.getRestaurant().getRestaurantId(),
                party.getRestaurant().getName(),
                party.getPartyDate(),
                party.getMealType()
        );
    }
}
