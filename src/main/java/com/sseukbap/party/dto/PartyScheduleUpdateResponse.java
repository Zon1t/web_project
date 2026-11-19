package com.sseukbap.party.dto;

import com.sseukbap.party.entity.PartyStatus;

/**
 * PATCH /api/parties/{partyId}/schedule 응답 data
 * confirmationsReset: 기존 일정을 수정해서 모든 참여자의 확정이 초기화됐으면 true
 */
public record PartyScheduleUpdateResponse(
        PartyStatus status,
        boolean confirmationsReset
) {
}
