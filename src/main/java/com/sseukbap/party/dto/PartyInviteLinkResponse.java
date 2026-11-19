package com.sseukbap.party.dto;

/**
 * GET /api/parties/{partyId}/invite-link 응답 data
 * FE는 'text + 사이트 주소 + path'를 클립보드에 복사한다.
 */
public record PartyInviteLinkResponse(
        String text,     // 예: "점심요정님이 한솥가득 백반 점심 식사에 초대하셨습니다~"
        String path      // 예: "/party-detail.html?partyId=31"
) {
}
