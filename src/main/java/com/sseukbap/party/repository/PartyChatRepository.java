package com.sseukbap.party.repository;

import com.sseukbap.party.entity.PartyChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
