package com.sseukbap.party.service;

import com.sseukbap.party.dto.PartyUnreadChatResponse;
import com.sseukbap.party.entity.MessageType;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * 식사팟 대화 '새 메시지' 알림 (C, 2026-11-26)
 *
 * 읽은 위치는 DB에 두지 않고 브라우저가 기억한다. (발표 당일 DB 변경을 피하려고)
 * 화면이 보낸 seen(팟번호 → 마지막으로 본 메시지 번호)보다 뒤에 온, 다른 사람의 일반 메시지만 센다.
 * seen에 없는 팟은 내가 참여한 뒤에 온 메시지를 센다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyUnreadChatService {

    private static final int DAYS_AHEAD = 14;
    private static final LocalDateTime BEGINNING = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final PartyMemberRepository partyMemberRepository;
    private final PartyChatRepository partyChatRepository;

    /** 참여 중인 팟(오늘 ~ 14일 후, 삭제·식사 완료 제외) 중 새 메시지가 있는 것만, 날짜·시간 순 */
    public List<PartyUnreadChatResponse> getUnreadChats(Long memberId, Map<Long, Long> seen) {
        LocalDate today = PartyTimePolicy.today();
        List<PartyMember> mine = partyMemberRepository.findAllByMemberIdAndPartyDateBetween(
                memberId, today, today.plusDays(DAYS_AHEAD), EnumSet.of(PartyStatus.DELETED, PartyStatus.FINISHED));

        List<PartyUnreadChatResponse> result = new ArrayList<>();
        mine.stream()
                .sorted(Comparator.comparing((PartyMember pm) -> pm.getParty().getPartyDate())
                        .thenComparing(pm -> pm.getParty().getBaseTime()))
                .forEach(pm -> {
                    Long partyId = pm.getParty().getPartyId();
                    Long afterId = seen == null ? null : seen.get(partyId);
                    LocalDateTime since = afterId == null ? pm.getJoinedAt() : BEGINNING;
                    long count = partyChatRepository.countUnread(
                            partyId, memberId, MessageType.TEXT, afterId == null ? 0L : afterId, since);
                    if (count > 0) {
                        result.add(new PartyUnreadChatResponse(
                                partyId,
                                pm.getParty().getRestaurant().getName(),
                                pm.getParty().getPartyDate(),
                                pm.getParty().getMealType(),
                                count,
                                partyChatRepository.findLastChatId(partyId)));
                    }
                });
        return result;
    }
}
