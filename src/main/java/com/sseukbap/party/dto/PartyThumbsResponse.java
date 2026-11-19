package com.sseukbap.party.dto;

/**
 * POST /api/parties/{partyId}/thumbs 응답 data
 * thumbsCount30d: 따봉을 반영한 뒤 그 식당의 최근 30일(오늘-29 ~ 오늘, 식사일 기준) 따봉 수
 */
public record PartyThumbsResponse(
        Long restaurantId,
        long thumbsCount30d
) {
}
