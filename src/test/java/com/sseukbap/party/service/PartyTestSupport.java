package com.sseukbap.party.service;

import com.sseukbap.party.entity.PartyStatus;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 식사팟 테스트 공용 도우미
 *
 * 테스트는 더미데이터 회원(user01~05)을 쓰는데, 이 회원들은 더미데이터에서 이미 오늘·내일 팟에 들어가 있다.
 * '한 끼에 팟 하나' 규칙(2026-11-23) 때문에 그대로 두면 테스트에서 팟을 만들거나 참여할 때 409가 난다.
 */
final class PartyTestSupport {

    private PartyTestSupport() {
    }

    /**
     * 참여하기·참석 확정 테스트에 넘기는 시각: 오늘 09:00 (2026-11-25 T6 P1)
     * 기준 시간이 지난 팟은 참여·확정이 막히므로, 테스트를 언제 돌려도 같은 결과가 나오게 식사 시간 전 시각으로 고정한다.
     */
    static final LocalDateTime MORNING = LocalDate.now(PartyTimePolicy.ZONE).atTime(9, 0);

    /**
     * 오늘 ~ 14일 후의 기존 팟을 테스트 트랜잭션 안에서만 DELETED로 숨긴다.
     * 삭제된 팟은 규칙 검사·목록에서 빠지므로 테스트가 만든 팟만 남는다. 테스트가 끝나면 롤백되어 원래대로 돌아온다.
     * 반드시 @Transactional 테스트에서만 쓴다. (트랜잭션이 없으면 더미데이터가 실제로 바뀐다)
     */
    static void hideExistingParties(EntityManager em) {
        LocalDate today = LocalDate.now();
        em.createQuery("update MealParty p set p.status = :deleted where p.partyDate between :today and :lastDay")
                .setParameter("deleted", PartyStatus.DELETED)
                .setParameter("today", today)
                .setParameter("lastDay", today.plusDays(PartyTimePolicy.MAX_DAYS_AHEAD))
                .executeUpdate();
        em.clear();
    }

    /**
     * 팟을 내일로 옮긴다. 한 테스트에서 같은 방장이 오늘 점심 팟을 여러 개 만들어야 할 때,
     * 날짜와 상관없는 앞의 팟을 비켜 두는 데 쓴다. ('한 끼에 팟 하나' 규칙)
     */
    static void moveToTomorrow(EntityManager em, Long partyId) {
        em.flush();
        em.createQuery("update MealParty p set p.partyDate = :date where p.partyId = :id")
                .setParameter("date", LocalDate.now().plusDays(1))
                .setParameter("id", partyId)
                .executeUpdate();
        em.clear();
    }
}
