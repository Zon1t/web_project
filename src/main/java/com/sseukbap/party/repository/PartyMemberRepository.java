package com.sseukbap.party.repository;

import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
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

    /**
     * 참여 정보 행 잠금 조회 (SELECT ... FOR UPDATE)
     * 따봉 등록에서 같은 사람이 동시에 두 번 요청해도 한 번만 반영되게 한다. 반드시 @Transactional 안에서 호출할 것.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pm from PartyMember pm where pm.party.partyId = :partyId and pm.member.memberId = :memberId")
    Optional<PartyMember> findByPartyIdAndMemberIdForUpdate(@Param("partyId") Long partyId,
                                                            @Param("memberId") Long memberId);

    /**
     * 따봉 요청 대상 후보: 이 회원이 참여 확정했고 아직 따봉을 안 남긴, 식사 날짜가 fromDate~toDate인 FINISHED 팟
     * (팟·식당까지 한 번에 가져온다. 정렬은 Service에서)
     */
    @Query("select pm from PartyMember pm join fetch pm.party p join fetch p.restaurant "
            + "where pm.member.memberId = :memberId and pm.confirmed = true and pm.thumbsUp = false "
            + "and p.status = :finished and p.partyDate between :fromDate and :toDate")
    List<PartyMember> findThumbsPendingCandidates(@Param("memberId") Long memberId,
                                                  @Param("finished") PartyStatus finished,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("toDate") LocalDate toDate);

    /** 이 회원이 이 팟에 참여 중인지 (참여하기의 ALREADY_JOINED 판단) */
    @Query("select count(pm) > 0 from PartyMember pm where pm.party.partyId = :partyId and pm.member.memberId = :memberId")
    boolean existsByPartyIdAndMemberId(@Param("partyId") Long partyId,
                                       @Param("memberId") Long memberId);

    /** 이 팟의 현재 인원 (방장 포함) */
    @Query("select count(pm) from PartyMember pm where pm.party.partyId = :partyId")
    long countByPartyId(@Param("partyId") Long partyId);

    /**
     * 이 팟의 모든 참여자 확정을 N으로 초기화 (방장의 일정 수정 시)
     * 한 번의 UPDATE로 처리한다. (엔티티를 하나씩 바꾸지 않고 DB에서 바로 변경)
     * 주의: 이미 메모리에 올라온 PartyMember 객체에는 반영되지 않는다.
     *       clearAutomatically를 쓰면 잠금으로 가져온 MealParty까지 관리 대상에서 빠지므로 쓰지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("update PartyMember pm set pm.confirmed = false where pm.party.partyId = :partyId")
    int resetConfirmations(@Param("partyId") Long partyId);

    /** 이 팟의 참여 확정 인원 */
    @Query("select count(pm) from PartyMember pm where pm.party.partyId = :partyId and pm.confirmed = true")
    long countConfirmedByPartyId(@Param("partyId") Long partyId);

    /** 이 팟의 참여자 목록, 참여 순 (회원 정보까지 한 번에 가져온다) */
    @Query("select pm from PartyMember pm join fetch pm.member where pm.party.partyId = :partyId order by pm.joinedAt, pm.partyMemberId")
    List<PartyMember> findAllWithMemberByPartyId(@Param("partyId") Long partyId);

    /**
     * 여러 팟의 인원 수·확정 인원 수를 한 번에 센다. (목록용)
     * partyIds가 비어 있으면 호출하지 말 것 (Oracle은 빈 IN () 를 허용하지 않음)
     */
    @Query("select new com.sseukbap.party.repository.PartyMemberCount("
            + "pm.party.partyId, count(pm), sum(case when pm.confirmed = true then 1 else 0 end)) "
            + "from PartyMember pm where pm.party.partyId in :partyIds group by pm.party.partyId")
    List<PartyMemberCount> countByPartyIds(@Param("partyIds") Collection<Long> partyIds);

    /**
     * 이 회원이 특정 날짜에 참여한 팟의 참여 정보 (팟·식당까지 한 번에)
     * excludedStatuses: 빼고 싶은 팟 상태 (예: DELETED)
     */
    @Query("select pm from PartyMember pm join fetch pm.party p join fetch p.restaurant "
            + "where pm.member.memberId = :memberId and p.partyDate = :partyDate and p.status not in :excludedStatuses")
    List<PartyMember> findAllByMemberIdAndPartyDate(@Param("memberId") Long memberId,
                                                    @Param("partyDate") LocalDate partyDate,
                                                    @Param("excludedStatuses") Collection<PartyStatus> excludedStatuses);

    /**
     * 이 회원이 fromDate ~ toDate(포함)에 참여한 팟의 참여 정보 (팟·식당까지 한 번에)
     * 메인 '참여 중인 식사팟'(오늘 ~ 14일 후)에서 쓴다.
     */
    @Query("select pm from PartyMember pm join fetch pm.party p join fetch p.restaurant "
            + "where pm.member.memberId = :memberId and p.partyDate between :fromDate and :toDate "
            + "and p.status not in :excludedStatuses")
    List<PartyMember> findAllByMemberIdAndPartyDateBetween(@Param("memberId") Long memberId,
                                                           @Param("fromDate") LocalDate fromDate,
                                                           @Param("toDate") LocalDate toDate,
                                                           @Param("excludedStatuses") Collection<PartyStatus> excludedStatuses);
}
