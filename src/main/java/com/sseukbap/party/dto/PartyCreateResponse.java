package com.sseukbap.party.dto;

/** POST /api/parties/create 응답 data. FE는 이 partyId로 상세 화면으로 이동한다. */
public record PartyCreateResponse(
        Long partyId
) {
}
