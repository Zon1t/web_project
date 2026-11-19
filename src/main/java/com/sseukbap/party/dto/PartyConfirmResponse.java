package com.sseukbap.party.dto;

/** 참여 확정·확정 취소 응답 data. confirmedCount = 처리 후 이 팟의 확정 인원 */
public record PartyConfirmResponse(
        boolean confirmed,
        int confirmedCount
) {
}
