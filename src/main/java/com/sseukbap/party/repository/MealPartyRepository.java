package com.sseukbap.party.repository;

import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 식사팟 Repository
 * 목록·집계용 조회는 각 기능 브랜치에서 추가한다. (모든 조회에 STATUS <> 'DELETED' 조건 필수)
 */
public interface MealPartyRepository extends JpaRepository<MealParty, Long> {

    /**
     * 행 잠금 조회 (SELECT ... FOR UPDATE)
     * 참여·나가기·일정·상태 변경·따봉처럼 동시에 여러 명이 바꿀 수 있는 작업에서 사용한다.
     * 먼저 잠근 트랜잭션이 끝날 때까지 다른 요청은 기다린다. 반드시 @Transactional 안에서 호출할 것.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MealParty p where p.partyId = :partyId")
    Optional<MealParty> findByIdForUpdate(@Param("partyId") Long partyId);

    /**
     * 특정 날짜의 팟 중 excludedStatuses를 뺀 것 (식당 정보까지 한 번에)
     * 목록: excludedStatuses = FINISHED, DELETED
     */
    @Query("select p from MealParty p join fetch p.restaurant "
            + "where p.partyDate = :partyDate and p.status not in :excludedStatuses")
    List<MealParty> findAllByPartyDateExcludingStatuses(@Param("partyDate") LocalDate partyDate,
                                                        @Param("excludedStatuses") Collection<PartyStatus> excludedStatuses);

    /**
     * 식사 완료 처리 후보: 식사 날짜가 untilDate 이하이고 statuses(진행 중 상태)인 팟
     * 기준 시간이 지났는지는 자바에서 거른다. (시간이 'HH:mm' 문자열이라 날짜+시간 비교를 SQL로 하기 번거로움)
     */
    @Query("select p from MealParty p where p.partyDate <= :untilDate and p.status in :statuses")
    List<MealParty> findAllByPartyDateUntilAndStatusIn(@Param("untilDate") LocalDate untilDate,
                                                       @Param("statuses") Collection<PartyStatus> statuses);
}
