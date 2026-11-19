package com.sseukbap.party.repository;

import com.sseukbap.party.entity.MealParty;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
