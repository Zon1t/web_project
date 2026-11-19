package com.sseukbap.party.repository;

import com.sseukbap.party.entity.PartyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 식사팟 참여자 Repository
 */
public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {

    /** 이 팟에서 이 회원의 참여 정보 (나가기·확정·따봉에서 사용) */
    @Query("select pm from PartyMember pm where pm.party.partyId = :partyId and pm.member.memberId = :memberId")
    Optional<PartyMember> findByPartyIdAndMemberId(@Param("partyId") Long partyId,
                                                   @Param("memberId") Long memberId);

    /** 이 회원이 이 팟에 참여 중인지 (참여하기의 ALREADY_JOINED 판단) */
    @Query("select count(pm) > 0 from PartyMember pm where pm.party.partyId = :partyId and pm.member.memberId = :memberId")
    boolean existsByPartyIdAndMemberId(@Param("partyId") Long partyId,
                                       @Param("memberId") Long memberId);

    /** 이 팟의 현재 인원 (방장 포함) */
    @Query("select count(pm) from PartyMember pm where pm.party.partyId = :partyId")
    long countByPartyId(@Param("partyId") Long partyId);

    /** 이 팟의 참여자 목록, 참여 순 (회원 정보까지 한 번에 가져온다) */
    @Query("select pm from PartyMember pm join fetch pm.member where pm.party.partyId = :partyId order by pm.joinedAt, pm.partyMemberId")
    List<PartyMember> findAllWithMemberByPartyId(@Param("partyId") Long partyId);
}
