package com.sseukbap.party.service;

import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * 스케줄러가 부르는 식사팟 일괄 처리. (API 없음, 기준: docs/API_SPEC.md "서버 내부 처리")
 * - 식사 완료 처리: 기준 시간이 지난 진행 중 팟 → FINISHED + 시스템 메시지
 * - 대화 기록 삭제: 전날까지 끝난(FINISHED·DELETED) 팟의 대화 삭제
 *
 * 현재 시각은 PartyScheduler가 한국 시간으로 넘겨준다. (테스트에서는 원하는 시각을 넘긴다)
 */
@Service
@RequiredArgsConstructor
public class PartyBatchService {

    /** 진행 중 상태 = 식사 완료 처리 대상 */
    private static final EnumSet<PartyStatus> ACTIVE_STATUSES =
            EnumSet.of(PartyStatus.RECRUITING, PartyStatus.SCHEDULED, PartyStatus.FULL, PartyStatus.CLOSED);

    /** 끝난 상태 = 대화 삭제 대상 */
    private static final EnumSet<PartyStatus> ENDED_STATUSES = EnumSet.of(PartyStatus.FINISHED, PartyStatus.DELETED);

    private final MealPartyRepository mealPartyRepository;
    private final PartyChatRepository partyChatRepository;

    // ===== 식사 완료 처리 =====

    /**
     * 식사 완료 대상 팟 ID 목록: 진행 중이고 기준 시간(확정 시간, 없으면 예정 시간)이 지난 팟.
     * 오늘뿐 아니라 이전 날짜도 본다. (서버가 꺼져 있던 동안 밀린 팟도 처리)
     */
    @Transactional(readOnly = true)
    public List<Long> findFinishTargetIds(LocalDateTime now) {
        return mealPartyRepository.findAllByPartyDateUntilAndStatusIn(now.toLocalDate(), ACTIVE_STATUSES).stream()
                .filter(party -> PartyTimePolicy.isMealTimePassed(party.getPartyDate(), party.getBaseTime(), now))
                .map(MealParty::getPartyId)
                .toList();
    }

    /**
     * 팟 하나를 식사 완료 처리한다. 팟마다 트랜잭션을 따로 열어서 한 팟의 오류가 다른 팟에 번지지 않게 한다.
     * 행을 잠근 뒤 다시 확인한다. 대상을 찾은 뒤 그 사이에 방장이 삭제했거나 일정을 늦췄을 수 있기 때문이다.
     *
     * @return 실제로 완료 처리했으면 true, 그 사이 상태가 바뀌어 건너뛰었으면 false
     */
    @Transactional
    public boolean finishParty(Long partyId, LocalDateTime now) {
        MealParty party = mealPartyRepository.findByIdForUpdate(partyId).orElse(null);
        if (party == null
                || !party.getStatus().isActive()
                || !PartyTimePolicy.isMealTimePassed(party.getPartyDate(), party.getBaseTime(), now)) {
            return false;
        }

        party.finish();
        partyChatRepository.save(PartyChat.createSystem(party, "식사가 끝났어요"));
        return true;
    }

    // ===== 대화 기록 삭제 =====

    /**
     * 식사 날짜가 오늘 이전이고 끝난(FINISHED·DELETED) 팟의 대화를 지운다. 팟·참여 이력은 남긴다.
     * DB에 "끝난 시각" 컬럼이 없어서 식사 날짜로 판단한다. 식사팟은 당일만 있어서 결과는 같다.
     *
     * @param today 오늘 (한국 시간)
     * @return 지운 메시지 수
     */
    @Transactional
    public int deleteOldChats(LocalDate today) {
        return partyChatRepository.deleteAllOfEndedParties(today, ENDED_STATUSES);
    }
}
