package com.sseukbap.party.repository;

/**
 * 팟별 인원 수 집계 결과 (PartyMemberRepository.countByPartyIds)
 * 목록에서 팟마다 쿼리를 따로 날리지 않고 한 번에 세기 위해 쓴다.
 */
public record PartyMemberCount(
        Long partyId,
        Long memberCount,      // 참여 인원 (방장 포함)
        Long confirmedCount    // 참여 확정 인원
) {
}
