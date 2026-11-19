package com.sseukbap.party.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.dto.PartyScheduleUpdateResponse;
import com.sseukbap.party.dto.PartyStatusUpdateRequest;
import com.sseukbap.party.dto.PartyStatusUpdateResponse;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 방장 전용: 일정 확정·수정, 모집 마감·재개. (삭제는 PartyService)
 * 기준: docs/API_SPEC.md 6장 (각 메서드의 번호는 명세의 "시스템 처리" 순서)
 * 모든 메서드는 MEAL_PARTY 행을 먼저 잠근다(SELECT ... FOR UPDATE).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PartyHostService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final PartyFinder partyFinder;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyChatRepository partyChatRepository;

    // ===== 일정 확정·수정 =====

    public PartyScheduleUpdateResponse updateSchedule(Long partyId, PartyScheduleUpdateRequest request, Long memberId) {
        return updateSchedule(partyId, request, memberId, LocalDateTime.now());
    }

    /** 현재 시각을 밖에서 받는 버전 (테스트에서 시각을 고정하려고 따로 둠) */
    PartyScheduleUpdateResponse updateSchedule(Long partyId, PartyScheduleUpdateRequest request,
                                               Long memberId, LocalDateTime now) {
        // 1. 필수값·형식·길이 검증은 PartyScheduleUpdateRequest의 @Valid에서 끝났다.

        // 2. 행 잠금 조회. 없거나 DELETED면 중단
        MealParty party = partyFinder.getPartyForUpdate(partyId);

        // 3. 방장 확인
        validateHost(party, memberId, "방장만 일정을 정할 수 있습니다.");

        // 4. 식사 완료
        validateNotFinished(party);

        // 5. 팟의 끼니 시간대 + 30분 단위 + 현재 시각 이후
        if (!PartyTimePolicy.isValidMealTime(party.getMealType(), request.confirmedTime())
                || !PartyTimePolicy.isAfterNow(party.getPartyDate(), request.confirmedTime(), now)) {
            throw new BusinessException(ErrorCode.INVALID_MEAL_TIME);
        }

        // 6. 기존 일정이 있었으면 방장 포함 모든 참여자의 확정을 초기화
        boolean isUpdate = party.hasSchedule();
        if (isUpdate) {
            partyMemberRepository.resetConfirmations(partyId);
        }

        // 7~8. 일정 저장 + RECRUITING이면 SCHEDULED (그 외 상태 유지)
        party.updateSchedule(request.confirmedTime(), request.meetPlace(), request.confirmMemo());

        // 9. 시스템 메시지 (최초 / 수정)
        String schedule = request.confirmedTime().format(HH_MM) + " · " + request.meetPlace();
        addSystemMessage(party, isUpdate
                ? "방장이 일정을 변경했습니다 · " + schedule + ". 참여 확정을 다시 해 주세요"
                : "방장이 일정을 확정했습니다 · " + schedule);

        return new PartyScheduleUpdateResponse(party.getStatus(), isUpdate);
    }

    // ===== 모집 마감·재개 =====

    public PartyStatusUpdateResponse updateStatus(Long partyId, PartyStatusUpdateRequest request, Long memberId) {
        // 1. action 값 검증은 PartyStatusUpdateRequest의 @Valid에서 끝났다. (CLOSE / REOPEN)

        // 2. 행 잠금 조회. 없거나 DELETED면 중단
        MealParty party = partyFinder.getPartyForUpdate(partyId);

        // 3. 방장 확인
        validateHost(party, memberId, "방장만 변경할 수 있습니다.");

        // 4. 식사 완료
        validateNotFinished(party);

        if (request.isClose()) {
            // 5. CLOSE: RECRUITING·SCHEDULED·FULL만 가능 → CLOSED
            if (!party.getStatus().isClosable()) {
                throw new BusinessException(ErrorCode.INVALID_STATUS);
            }
            party.close();
            addSystemMessage(party, "방장이 모집을 마감했습니다");
        } else {
            // 6. REOPEN: CLOSED만 가능 → 정원이 차 있으면 FULL, 아니면 일정 유무로 SCHEDULED / RECRUITING
            if (!party.getStatus().isReopenable()) {
                throw new BusinessException(ErrorCode.INVALID_STATUS);
            }
            party.reopen((int) partyMemberRepository.countByPartyId(partyId));
            addSystemMessage(party, "방장이 모집을 다시 열었습니다");
        }

        return new PartyStatusUpdateResponse(party.getStatus());
    }

    // ===== 공통 =====

    private void validateHost(MealParty party, Long memberId, String message) {
        if (!party.isHost(memberId)) {
            throw new BusinessException(ErrorCode.NOT_HOST, message);
        }
    }

    private void validateNotFinished(MealParty party) {
        if (party.isFinished()) {
            throw new BusinessException(ErrorCode.PARTY_FINISHED);
        }
    }

    private void addSystemMessage(MealParty party, String content) {
        partyChatRepository.save(PartyChat.createSystem(party, content));
    }
}
