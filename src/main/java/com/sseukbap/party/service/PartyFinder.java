package com.sseukbap.party.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.repository.MealPartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 식사팟 서비스들이 함께 쓰는 조회 + 공통 오류 처리.
 * - 팟이 없으면 404 PARTY_NOT_FOUND, DELETED면 404 PARTY_DELETED (모든 식사팟 API 공통 규칙)
 * - 트랜잭션은 이 클래스를 부르는 Service 메서드의 것을 그대로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class PartyFinder {

    private final MealPartyRepository mealPartyRepository;
    private final MemberRepository memberRepository;

    /** 일반 조회 (상세 등 읽기 전용) */
    public MealParty getParty(Long partyId) {
        MealParty party = mealPartyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        validateNotDeleted(party);
        return party;
    }

    /**
     * 행 잠금 조회 (SELECT ... FOR UPDATE)
     * 참여·나가기·확정·일정·상태 변경·삭제·따봉처럼 동시에 바뀔 수 있는 작업에서 쓴다.
     * 반드시 @Transactional(쓰기) 메서드 안에서 호출할 것. 트랜잭션이 끝나면 잠금이 풀린다.
     */
    public MealParty getPartyForUpdate(Long partyId) {
        MealParty party = mealPartyRepository.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        validateNotDeleted(party);
        return party;
    }

    /** 로그인 회원. 세션의 회원이 DB에 없으면(탈퇴 등) 로그인부터 다시 하도록 401 */
    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private void validateNotDeleted(MealParty party) {
        if (party.isDeleted()) {
            throw new BusinessException(ErrorCode.PARTY_DELETED);
        }
    }
}
