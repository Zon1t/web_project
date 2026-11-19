package com.sseukbap.party.dto;

import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyMember;

/** 식사팟 상세의 members 한 줄 */
public record PartyMemberResponse(
        Long memberId,
        String nickname,
        boolean host,         // 방장 여부
        boolean confirmed     // 참여 확정 여부
) {

    public static PartyMemberResponse of(MealParty party, PartyMember partyMember) {
        Long memberId = partyMember.getMember().getMemberId();
        return new PartyMemberResponse(
                memberId,
                partyMember.getMember().getNickname(),
                party.isHost(memberId),
                partyMember.isConfirmed()
        );
    }
}
