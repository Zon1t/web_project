package com.sseukbap.party.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyCreateResponse;
import com.sseukbap.party.dto.PartyDetailResponse;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 식사팟 생성·상세·삭제.
 * 기준: docs/API_SPEC.md 6장 (각 메서드의 번호는 명세의 "시스템 처리" 순서)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyService {

    private final MealPartyRepository mealPartyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyChatRepository partyChatRepository;
    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;

    /** 식사팟 생성. 방장이 첫 참여자가 되고 상태는 RECRUITING */
    @Transactional
    public PartyCreateResponse createParty(PartyCreateRequest request, Long memberId) {
        return createParty(request, memberId, LocalDateTime.now());
    }

    /**
     * 현재 시각을 밖에서 받는 버전. 테스트에서 시각을 고정하려고 따로 두었다.
     * (Controller는 위의 메서드를 쓴다)
     */
    @Transactional
    PartyCreateResponse createParty(PartyCreateRequest request, Long memberId, LocalDateTime now) {
        // 1. 필수값·형식·범위 검증은 PartyCreateRequest의 @Valid에서 끝났다.

        // 2. 끼니 시간대 + 30분 단위
        if (!PartyTimePolicy.isValidMealTime(request.mealType(), request.mealTime())) {
            throw new BusinessException(ErrorCode.INVALID_MEAL_TIME);
        }

        // 3. 오늘 날짜 + 식사 시간이 현재 시각 이후인지
        LocalDate today = now.toLocalDate();
        if (!PartyTimePolicy.isAfterNow(today, request.mealTime(), now)) {
            throw new BusinessException(ErrorCode.INVALID_MEAL_TIME);
        }

        // 4. 식당 확인
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        Member host = getMember(memberId);

        // 5~7. 팟 저장 → 방장을 참여자로 저장(확정 N) → 시스템 메시지 (하나의 트랜잭션)
        MealParty party = mealPartyRepository.save(MealParty.create(
                restaurant, host, today, request.mealType(), request.mealTime(),
                request.capacity(), request.memo()));
        partyMemberRepository.save(PartyMember.create(party, host));
        partyChatRepository.save(PartyChat.createSystem(party, host.getNickname() + "님이 식사팟을 만들었습니다"));

        return new PartyCreateResponse(party.getPartyId());
    }

    /** 식사팟 상세. 미참여자도 조회할 수 있다. */
    public PartyDetailResponse getParty(Long partyId, Long memberId) {
        // 1. 팟 조회 (없으면 PARTY_NOT_FOUND, 삭제됐으면 PARTY_DELETED)
        MealParty party = mealPartyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        validateNotDeleted(party);

        // 2. 참여자 목록 (참여 순, 회원 정보 포함)
        List<PartyMember> members = partyMemberRepository.findAllWithMemberByPartyId(partyId);

        // 3~5. 내 역할·확정 여부, 확정 취소 마감 시각 계산 → 응답
        return PartyDetailResponse.of(party, members, memberId);
    }

    /** 식사팟 삭제 (방장). 소프트 삭제: status만 DELETED로 바꾼다. */
    @Transactional
    public void deleteParty(Long partyId, Long memberId) {
        // 1. 행 잠금 조회. 없거나 DELETED면 중단
        MealParty party = mealPartyRepository.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        validateNotDeleted(party);

        // 2. 방장 확인
        if (!party.isHost(memberId)) {
            throw new BusinessException(ErrorCode.NOT_HOST, "방장만 삭제할 수 있습니다.");
        }

        // 3. 식사 완료된 팟은 삭제 불가 (마이페이지 이력 유지)
        if (party.isFinished()) {
            throw new BusinessException(ErrorCode.PARTY_FINISHED);
        }

        // 4. DELETED로 변경 (커밋 시 UPDATE 실행)
        party.delete();
    }

    // ===== 공통 =====

    private void validateNotDeleted(MealParty party) {
        if (party.isDeleted()) {
            throw new BusinessException(ErrorCode.PARTY_DELETED);
        }
    }

    /** 세션의 회원이 DB에 없으면(탈퇴 등) 로그인부터 다시 하도록 401 */
    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
