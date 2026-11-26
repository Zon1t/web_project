package com.sseukbap.party.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.party.dto.PartyThumbsPendingResponse;
import com.sseukbap.party.dto.PartyThumbsResponse;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * 따봉: 요청 대상 조회(메인 배너), 따봉 등록.
 * 기준: docs/API_SPEC.md 6장 + 공통 규칙 '따봉 집계'
 *
 * 따봉은 PARTY_MEMBER.THUMBS_UP_YN에 저장한다. (THUMBS_UP 테이블 없음)
 * 최근 30일 따봉 수는 B의 식당 목록·상세와 같은 값이 나오도록 RestaurantRepository.countThumbsUp을 그대로 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartyThumbsService {

    /** 따봉 요청 대상: 식사 후 7일 이내 (오늘-6 ~ 오늘) */
    private static final int PENDING_DAYS = 7;
    /** 따봉 집계: 최근 30일 (오늘-29 ~ 오늘, 식사일 기준) */
    private static final int THUMBS_PERIOD_DAYS = 30;

    private final PartyFinder partyFinder;
    private final PartyMemberRepository partyMemberRepository;
    private final RestaurantRepository restaurantRepository;

    // ===== 따봉 요청 대상 =====

    /**
     * 따봉 요청 대상 1건: FINISHED 팟 중 내가 참여 확정했고 아직 따봉을 안 남긴, 최근 7일 이내 식사.
     * 대상이 없으면 null (FE는 배너 숨김). [다음에]로 하루 숨기기는 FE 브라우저 저장소에서 처리한다.
     */
    public PartyThumbsPendingResponse getThumbsPending(Long memberId) {
        LocalDate today = PartyTimePolicy.today();

        // 1. 조건에 맞는 팟 조회
        // 2. 정렬: 식사 날짜 최신 → 기준 시간 늦은 순 → partyId 큰 순
        // 3. 첫 1건 (없으면 null)
        return partyMemberRepository.findThumbsPendingCandidates(
                        memberId, PartyStatus.FINISHED, today.minusDays(PENDING_DAYS - 1), today)
                .stream()
                .map(PartyMember::getParty)
                .max(Comparator.comparing(MealParty::getPartyDate)
                        .thenComparing(MealParty::getBaseTime)
                        .thenComparing(MealParty::getPartyId))
                .map(PartyThumbsPendingResponse::from)
                .orElse(null);
    }

    // ===== 따봉 등록 =====

    /** 따봉 등록. 식사 완료된 팟의 참여 확정자만, 팟 1건당 1회. 대상 식당 = 팟의 식당 */
    @Transactional
    public PartyThumbsResponse createThumbs(Long partyId, Long memberId) {
        // 1. 팟 조회. 없거나 DELETED면 중단
        MealParty party = partyFinder.getParty(partyId);

        // 2. 식사가 끝나야 따봉 가능
        if (!party.isFinished()) {
            throw new BusinessException(ErrorCode.PARTY_NOT_FINISHED);
        }

        // 3. 내 참여 행을 잠금 조회. 참여자가 아니거나 미확정이면 중단 (403)
        PartyMember partyMember = partyMemberRepository.findByPartyIdAndMemberIdForUpdate(partyId, memberId)
                .filter(PartyMember::isConfirmed)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONFIRMED_MEMBER));

        // 4. 이미 남겼으면 중단 (행 잠금 덕분에 동시에 두 번 눌러도 두 번째는 여기서 걸린다)
        if (partyMember.isThumbsUp()) {
            throw new BusinessException(ErrorCode.ALREADY_THUMBED);
        }

        // 5. 따봉 Y. 집계 쿼리가 방금 바꾼 값을 보도록 먼저 DB에 반영(flush)한다.
        partyMember.thumbsUp();
        partyMemberRepository.flush();

        // 6. 그 식당의 최근 30일 따봉 수 (B의 식당 목록·상세와 같은 집계)
        Long restaurantId = party.getRestaurant().getRestaurantId();
        LocalDate today = PartyTimePolicy.today();
        long thumbsCount30d = restaurantRepository.countThumbsUp(
                restaurantId, today.minusDays(THUMBS_PERIOD_DAYS - 1), today.plusDays(1));

        return new PartyThumbsResponse(restaurantId, thumbsCount30d);
    }
}
