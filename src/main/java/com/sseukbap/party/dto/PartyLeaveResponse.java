package com.sseukbap.party.dto;

/** DELETE /api/parties/{partyId}/members/me 응답 data. 방장이 나가서 팟이 삭제됐으면 true */
public record PartyLeaveResponse(
        boolean partyDeleted
) {
}
