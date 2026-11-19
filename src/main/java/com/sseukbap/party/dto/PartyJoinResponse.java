package com.sseukbap.party.dto;

import com.sseukbap.party.entity.PartyStatus;

/** POST /api/parties/{partyId}/members 응답 data. 정원에 도달했으면 status = FULL */
public record PartyJoinResponse(
        Long partyId,
        int memberCount,
        PartyStatus status
) {
}
