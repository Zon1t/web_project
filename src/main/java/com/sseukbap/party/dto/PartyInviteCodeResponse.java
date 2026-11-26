package com.sseukbap.party.dto;

/**
 * POST /api/parties/{partyId}/invite-code 응답 data (비공개 식사팟 초대 코드 새로 만들기, 2026-11-25 T4)
 * FE는 '사이트 주소 + path'를 새 초대 링크로 쓴다.
 */
public record PartyInviteCodeResponse(
        String path      // 예: "/party-detail.html?partyId=31&code=Xk3v9QpL0aZ7mN2bC4dE5f"
) {
}
