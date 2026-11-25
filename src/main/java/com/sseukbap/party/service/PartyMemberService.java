package com.sseukbap.party.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.party.dto.PartyConfirmResponse;
import com.sseukbap.party.dto.PartyJoinResponse;
import com.sseukbap.party.dto.PartyLeaveResponse;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 식사팟 참여하기(1차)·나가기·참여 확정(2차)·확정 취소.
 * 기준: docs/API_SPEC.md 6장 (각 메서드의 번호는 명세의 "시스템 처리" 순서)
 *
 * 모든 메서드는 MEAL_PARTY 행을 먼저 잠근다(SELECT ... FOR UPDATE).
 * 같은 팟에 대한 요청이 동시에 와도 한 번에 하나씩 처리되어 정원 초과·상태 꼬임이 생기지 않는다.
 * (명세의 확정·확정 취소에는 잠금이 없지만, 일정 수정의 "확정 초기화"와 겹치지 않도록 같이 잠근다)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PartyMemberService {

    private final PartyFinder partyFinder;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyChatRepository partyChatRepository;

    // ===== 참여하기 (1차) =====

    public PartyJoinResponse joinParty(Long partyId, Long memberId) {
        return joinParty(partyId, memberId, PartyTimePolicy.now());
    }

    /** now를 넘겨받는 버전 (테스트에서 시각을 정할 때 사용). 공개 식사팟용 (초대 코드 없음) */
    public PartyJoinResponse joinParty(Long partyId, Long memberId, LocalDateTime now) {
        return joinParty(partyId, memberId, null, now);
    }

    /**
     * 참여하기. 비공개 식사팟은 초대 코드(inviteCode)가 맞아야 한다. (2026-11-25 T4)
     */
    public PartyJoinResponse joinParty(Long partyId, Long memberId, String inviteCode, LocalDateTime now) {
        // 1~2. 행 잠금 조회. 없거나 비공개인데 코드가 틀리면 PARTY_NOT_FOUND, DELETED면 PARTY_DELETED
        MealParty party = partyFinder.getVisiblePartyForUpdate(partyId, memberId, inviteCode);

        // 3. 식사 완료 (스케줄러가 아직 FINISHED로 바꾸지 않았어도 기준 시간이 지났으면 완료로 본다)
        validateNotFinished(party, now);

        // 4. 이미 참여 중
        if (partyMemberRepository.existsByPartyIdAndMemberId(partyId, memberId)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }

        // 4-1. 같은 날짜·같은 끼니의 다른 팟에 이미 들어가 있음 (확정 여부 무관, 삭제된 팟 제외)
        if (partyMemberRepository.existsSameMealParty(
                memberId, party.getPartyDate(), party.getMealType(), PartyStatus.DELETED)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED_SAME_MEAL);
        }

        // 5. FULL·CLOSED이거나 정원이 찼으면 중단
        int memberCount = (int) partyMemberRepository.countByPartyId(partyId);
        if (!party.getStatus().isJoinable() || memberCount >= party.getCapacity()) {
            throw new BusinessException(ErrorCode.PARTY_FULL);
        }

        // 6. 참여 저장 (확정 N)
        Member member = partyFinder.getMember(memberId);
        partyMemberRepository.save(PartyMember.create(party, member));

        // 7. 정원에 도달하면 FULL
        int memberCountAfter = memberCount + 1;
        party.join(memberCountAfter);

        // 8. 시스템 메시지
        addSystemMessage(party, member.getNickname() + "님이 참여했어요");

        return new PartyJoinResponse(partyId, memberCountAfter, party.getStatus());
    }

    // ===== 나가기 =====

    public PartyLeaveResponse leaveParty(Long partyId, Long memberId) {
        return leaveParty(partyId, memberId, PartyTimePolicy.now());
    }

    /** 현재 시각을 밖에서 받는 버전 (테스트에서 마감 시각 전후를 확인하려고 따로 둠) */
    PartyLeaveResponse leaveParty(Long partyId, Long memberId, LocalDateTime now) {
        // 1. 행 잠금 조회. 없거나 DELETED면 중단
        MealParty party = partyFinder.getPartyForUpdate(partyId);

        // 2. 식사 완료
        validateNotFinished(party);

        // 3. 참여자가 아니면 중단
        PartyMember partyMember = getPartyMember(partyId, memberId);

        // 4. 방장이 나가면 식사팟 삭제와 같은 처리 (DELETED)
        if (party.isHost(memberId)) {
            party.delete();
            return new PartyLeaveResponse(true);
        }

        // 5. 확정자는 기준 시간 30분 전까지만 나갈 수 있다
        if (partyMember.isConfirmed()
                && !PartyTimePolicy.isBeforeDeadline(party.getPartyDate(), party.getBaseTime(), now)) {
            throw new BusinessException(ErrorCode.LEAVE_DEADLINE_PASSED);
        }

        // 6. 참여 행 삭제 (대화는 MEMBER를 직접 참조하므로 남는다)
        int memberCountAfter = (int) partyMemberRepository.countByPartyId(partyId) - 1;
        partyMemberRepository.delete(partyMember);

        // 7. FULL이었다면 정원 미만이 되어 다시 모집 (CLOSED는 유지)
        party.leave(memberCountAfter);

        // 8. 시스템 메시지
        addSystemMessage(party, partyMember.getMember().getNickname() + "님이 나갔어요");

        return new PartyLeaveResponse(false);
    }

    // ===== 참여 확정 (2차) =====

    public PartyConfirmResponse confirmParty(Long partyId, Long memberId) {
        return confirmParty(partyId, memberId, PartyTimePolicy.now());
    }

    /** now를 넘겨받는 버전 (테스트에서 시각을 정할 때 사용) */
    public PartyConfirmResponse confirmParty(Long partyId, Long memberId, LocalDateTime now) {
        // 1. 팟 조회 (행 잠금). 없거나 DELETED면 중단
        MealParty party = partyFinder.getPartyForUpdate(partyId);

        // 2. 식사 완료 (기준 시간이 지났으면 스케줄러 처리 전이어도 완료로 본다)
        validateNotFinished(party, now);

        // 3. 참여자 확인
        PartyMember partyMember = getPartyMember(partyId, memberId);

        // 4. 방장이 일정을 확정해야 참여 확정 가능
        if (!party.hasSchedule()) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_SET);
        }

        // 5. 이미 확정
        if (partyMember.isConfirmed()) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }

        // 6. 확정 (Y)
        int confirmedCountAfter = (int) partyMemberRepository.countConfirmedByPartyId(partyId) + 1;
        partyMember.confirm();

        // 7. 시스템 메시지
        addSystemMessage(party, partyMember.getMember().getNickname() + "님이 참석을 확정했어요");

        // 8. 확정 인원 수
        return new PartyConfirmResponse(true, confirmedCountAfter);
    }

    // ===== 참여 확정 취소 =====

    public PartyConfirmResponse cancelConfirmParty(Long partyId, Long memberId) {
        return cancelConfirmParty(partyId, memberId, PartyTimePolicy.now());
    }

    /** 현재 시각을 밖에서 받는 버전 (테스트용) */
    PartyConfirmResponse cancelConfirmParty(Long partyId, Long memberId, LocalDateTime now) {
        // 1. 팟 조회 (행 잠금). 없거나 DELETED면 중단
        MealParty party = partyFinder.getPartyForUpdate(partyId);

        // 2. 식사 완료
        validateNotFinished(party);

        // 3. 참여자이고 확정 상태인지
        PartyMember partyMember = getPartyMember(partyId, memberId);
        if (!partyMember.isConfirmed()) {
            throw new BusinessException(ErrorCode.NOT_CONFIRMED);
        }

        // 4. 기준 시간 30분 전까지만 취소 가능
        if (!PartyTimePolicy.isBeforeDeadline(party.getPartyDate(), party.getBaseTime(), now)) {
            throw new BusinessException(ErrorCode.CANCEL_DEADLINE_PASSED);
        }

        // 5. 확정 취소 (N). 참여자 자격·대화 참여는 유지
        int confirmedCountAfter = (int) partyMemberRepository.countConfirmedByPartyId(partyId) - 1;
        partyMember.cancelConfirm();

        // 6. 시스템 메시지
        addSystemMessage(party, partyMember.getMember().getNickname() + "님이 참석 확정을 취소했어요");

        // 7. 확정 인원 수
        return new PartyConfirmResponse(false, confirmedCountAfter);
    }

    // ===== 공통 =====

    private void validateNotFinished(MealParty party) {
        if (party.isFinished()) {
            throw new BusinessException(ErrorCode.PARTY_FINISHED);
        }
    }

    /**
     * 식사 완료 + 기준 시간 경과 확인 (참여하기·참석 확정용, 2026-11-25 T6 P1)
     * 스케줄러는 5분마다 돌기 때문에, 기준 시간이 지난 뒤 FINISHED가 되기 전 잠깐 동안 참여·확정되는 것을 막는다.
     */
    private void validateNotFinished(MealParty party, LocalDateTime now) {
        validateNotFinished(party);
        if (PartyTimePolicy.isMealTimePassed(party.getPartyDate(), party.getBaseTime(), now)) {
            throw new BusinessException(ErrorCode.PARTY_FINISHED);
        }
    }

    /** 참여자가 아니면 409 NOT_JOINED (나가기·확정·확정 취소용) */
    private PartyMember getPartyMember(Long partyId, Long memberId) {
        return partyMemberRepository.findByPartyIdAndMemberId(partyId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_JOINED));
    }

    private void addSystemMessage(MealParty party, String content) {
        partyChatRepository.save(PartyChat.createSystem(party, content));
    }
}
