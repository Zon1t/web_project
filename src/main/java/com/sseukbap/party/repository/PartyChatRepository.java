package com.sseukbap.party.repository;

import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 식사팟 대화 Repository
 */
public interface PartyChatRepository extends JpaRepository<PartyChat, Long> {

    /**
     * 폴링용: afterId보다 큰 메시지만 오래된 순으로 (처음 조회는 afterId = 0)
     * 시스템 메시지는 작성자가 없으므로 left join으로 가져온다.
     */
    @Query("select c from PartyChat c left join fetch c.member "
            + "where c.party.partyId = :partyId and c.chatId > :afterId order by c.chatId")
    List<PartyChat> findAllAfter(@Param("partyId") Long partyId, @Param("afterId") Long afterId);

    /**
     * 위와 같고 개수 제한만 붙인 버전 (대화 조회 API는 최대 200개)
     * 사용 예: findAllAfter(partyId, afterId, PageRequest.of(0, 200))
     */
    @Query("select c from PartyChat c left join fetch c.member "
            + "where c.party.partyId = :partyId and c.chatId > :afterId order by c.chatId")
    List<PartyChat> findAllAfter(@Param("partyId") Long partyId, @Param("afterId") Long afterId, Pageable pageable);

    /**
     * 대화 기록 삭제 (매일 0시 스케줄러): 식사 날짜가 beforeDate 이전이고 statuses(FINISHED·DELETED)인 팟의 대화
     * DELETE 한 번으로 처리한다. 팟·참여 이력(MEAL_PARTY, PARTY_MEMBER)은 지우지 않는다.
     * @return 지운 메시지 수
     */
    @Modifying
    @Query("delete from PartyChat c where c.party.partyId in ("
            + "select p.partyId from MealParty p where p.partyDate < :beforeDate and p.status in :statuses)")
    int deleteAllOfEndedParties(@Param("beforeDate") LocalDate beforeDate,
                                @Param("statuses") Collection<PartyStatus> statuses);
}
