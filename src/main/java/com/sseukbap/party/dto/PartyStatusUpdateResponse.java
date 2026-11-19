package com.sseukbap.party.dto;

import com.sseukbap.party.entity.PartyStatus;

/** PATCH /api/parties/{partyId}/status 응답 data. 변경 후 상태 */
public record PartyStatusUpdateResponse(
        PartyStatus status
) {
}
