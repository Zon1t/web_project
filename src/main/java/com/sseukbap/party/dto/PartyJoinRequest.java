package com.sseukbap.party.dto;

import jakarta.validation.constraints.Size;

/**
 * POST /api/parties/{partyId}/members 요청 (선택, 2026-11-25 T4)
 * 비공개 식사팟에 참여할 때 초대 링크의 code를 보낸다. 공개 식사팟은 Body 없이 보내도 된다.
 */
public record PartyJoinRequest(
        @Size(max = 32, message = "입력값을 확인해 주세요.")
        String inviteCode
) {
}
