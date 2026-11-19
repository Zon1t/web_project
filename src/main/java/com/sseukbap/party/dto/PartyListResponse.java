package com.sseukbap.party.dto;

import java.util.List;

/**
 * GET /api/parties/list 응답 data
 * lunchCount·dinnerCount는 탭 숫자 표시용. mealType·limit과 관계없이 같은 status·excludeJoined 조건으로 센다.
 */
public record PartyListResponse(
        int lunchCount,
        int dinnerCount,
        List<PartyListItemResponse> parties
) {
}
