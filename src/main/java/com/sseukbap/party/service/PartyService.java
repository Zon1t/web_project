package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.party.dto.PartyActionPendingResponse;
import com.sseukbap.party.dto.PartyActionPendingResponse.PartyAction;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyCreateResponse;
import com.sseukbap.party.dto.PartyDetailResponse;
import com.sseukbap.party.dto.PartyInviteCodeResponse;
import com.sseukbap.party.dto.PartyInviteLinkResponse;
import com.sseukbap.party.dto.PartyListItemResponse;
import com.sseukbap.party.dto.PartyListResponse;
import com.sseukbap.party.dto.PartyMyTodayResponse;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberCount;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 식사팟 생성·상세·삭제·목록.
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
    private final PartyFinder partyFinder;

    private static final String LIST_INVALID_MESSAGE = "조회 조건을 확인해 주세요.";
    private static final int LIST_LIMIT_MAX = 50;
    private static final int MY_TODAY_LIMIT = 2;
    /** 식사팟 상세 화면 경로 (B·C와 맞춘 FE 주소 규칙) */
    private static final String PARTY_DETAIL_PATH = "/party-detail.html?partyId=";

    /** 식사팟 생성. 방장이 첫 참여자가 되고 상태는 RECRUITING */
    @Transactional
    public PartyCreateResponse createParty(PartyCreateRequest request, Long memberId) {
        return createParty(request, memberId, PartyTimePolicy.now());
    }

    /**
     * 현재 시각을 밖에서 받는 버전. 테스트에서 시각을 고정하려고 따로 두었다.
     * (Controller는 위의 메서드를 쓴다)
     */
    @Transactional
    PartyCreateResponse createParty(PartyCreateRequest request, Long memberId, LocalDateTime now) {
        // 1. 필수값·형식·범위 검증은 PartyCreateRequest의 @Valid에서 끝났다. 날짜가 없으면 오늘.
        LocalDate today = now.toLocalDate();
        LocalDate partyDate = request.partyDate() != null ? request.partyDate() : today;

        // 2. 날짜는 오늘 ~ 14일 후
        if (!PartyTimePolicy.isWithinDateRange(partyDate, today)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 3. 끼니 시간대 + 30분 단위
        if (!PartyTimePolicy.isValidMealTime(request.mealType(), request.mealTime())) {
            throw new BusinessException(ErrorCode.INVALID_MEAL_TIME);
        }

        // 4. 식사 날짜 + 시간이 현재 시각 이후인지 (미래 날짜는 항상 통과)
        if (!PartyTimePolicy.isAfterNow(partyDate, request.mealTime(), now)) {
            throw new BusinessException(ErrorCode.INVALID_MEAL_TIME);
        }

        // 4-1. 같은 날짜·같은 끼니의 팟에 이미 들어가 있으면 새로 만들 수 없음 (확정 여부 무관, 삭제된 팟 제외)
        if (partyMemberRepository.existsSameMealParty(memberId, partyDate, request.mealType(), PartyStatus.DELETED)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED_SAME_MEAL);
        }

        // 5. 식당 확인
        Restaurant restaurant = restaurantRepository.findVisibleById(request.restaurantId())   // 숨긴 식당은 404 (A3)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        Member host = partyFinder.getMember(memberId);

        // 6~8. 팟 저장 → 방장을 참여자로 저장(확정 N) → 시스템 메시지 (하나의 트랜잭션)
        MealParty party = mealPartyRepository.save(MealParty.create(
                restaurant, host, partyDate, request.mealType(), request.mealTime(),
                request.capacity(), request.memo(), request.privateParty()));
        partyMemberRepository.save(PartyMember.create(party, host));
        partyChatRepository.save(PartyChat.createSystem(party, host.getNickname() + "님이 식사팟을 만들었어요"));

        return new PartyCreateResponse(party.getPartyId());
    }

    /** 식사팟 상세. 미참여자도 조회할 수 있다. (공개 식사팟, 초대 코드 없이) */
    public PartyDetailResponse getParty(Long partyId, Long memberId) {
        return getParty(partyId, memberId, null);
    }

    /**
     * 식사팟 상세. 비공개 식사팟은 참여자이거나 초대 코드(inviteCode)가 맞아야 볼 수 있다. (2026-11-25 T4)
     */
    public PartyDetailResponse getParty(Long partyId, Long memberId, String inviteCode) {
        // 1. 팟 조회 (없거나 비공개인데 코드가 틀리면 PARTY_NOT_FOUND, 삭제됐으면 PARTY_DELETED)
        MealParty party = partyFinder.getVisibleParty(partyId, memberId, inviteCode);

        // 2. 참여자 목록 (참여 순, 회원 정보 포함)
        List<PartyMember> members = partyMemberRepository.findAllWithMemberByPartyId(partyId);

        // 3~5. 내 역할·확정 여부, 확정 취소 마감 시각 계산 → 응답
        return PartyDetailResponse.of(party, members, memberId);
    }

    /** 식사팟 삭제 (방장). 소프트 삭제: status만 DELETED로 바꾼다. */
    @Transactional
    public void deleteParty(Long partyId, Long memberId) {
        // 1. 행 잠금 조회. 없거나 DELETED면 중단
        MealParty party = partyFinder.getPartyForUpdate(partyId);

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

    // ===== 초대 링크 =====

    /**
     * 초대 문구와 상세 화면 경로. 참여자만, 모집 중(RECRUITING·SCHEDULED)인 팟만 초대할 수 있다.
     * 문구의 닉네임은 링크를 요청한 사람(방장이 아니어도 됨)
     */
    public PartyInviteLinkResponse getInviteLink(Long partyId, Long memberId) {
        // 1. 팟 조회. 없거나 DELETED면 중단
        MealParty party = partyFinder.getParty(partyId);

        // 2. 참여자 확인 (403)
        PartyMember me = partyMemberRepository.findByPartyIdAndMemberId(partyId, memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_JOINED_FORBIDDEN, "참여 중인 식사팟만 초대할 수 있습니다."));

        // 3. 마감(FULL·CLOSED)·완료(FINISHED)된 팟은 초대 불가
        if (!party.getStatus().isJoinable()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "마감되었거나 완료된 식사팟은 초대할 수 없습니다.");
        }

        // 4. 문구 + 상세 화면 경로. 오늘이 아닌 팟은 날짜도 넣는다 (예: 11월 21일 홍반점 점심 식사)
        String when = party.getPartyDate().equals(PartyTimePolicy.today())
                ? ""
                : party.getPartyDate().getMonthValue() + "월 " + party.getPartyDate().getDayOfMonth() + "일 ";
        String text = me.getMember().getNickname() + "님이 " + when + party.getRestaurant().getName() + " "
                + toKorean(party.getMealType()) + " 식사에 초대하셨습니다~";
        return new PartyInviteLinkResponse(text, detailPath(party));
    }

    /**
     * 비공개 식사팟 초대 코드 새로 만들기 (방장, 2026-11-25 T4). 예전 초대 링크는 404가 된다.
     */
    @Transactional
    public PartyInviteCodeResponse regenerateInviteCode(Long partyId, Long memberId) {
        // 1. 행 잠금 조회. 없거나 DELETED면 중단
        MealParty party = partyFinder.getPartyForUpdate(partyId);

        // 2. 방장 확인
        if (!party.isHost(memberId)) {
            throw new BusinessException(ErrorCode.NOT_HOST, "방장만 초대 링크를 새로 만들 수 있습니다.");
        }

        // 3. 식사 완료
        if (party.isFinished()) {
            throw new BusinessException(ErrorCode.PARTY_FINISHED);
        }

        // 4. 비공개 식사팟만
        if (!party.isPrivateParty()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "비공개 식사팟만 초대 링크를 새로 만들 수 있습니다.");
        }

        // 5. 새 코드 저장 (커밋 시 UPDATE)
        party.regenerateInviteCode();
        return new PartyInviteCodeResponse(detailPath(party));
    }

    /** 상세 화면 경로. 비공개 식사팟은 초대 코드를 붙인다 (Base64 URL 문자라 따로 인코딩하지 않아도 된다) */
    private static String detailPath(MealParty party) {
        String path = PARTY_DETAIL_PATH + party.getPartyId();
        return party.isPrivateParty() ? path + "&code=" + party.getInviteCode() : path;
    }

    /** 끼니 한글 이름 (MealType은 common 담당이라 여기서 변환) */
    private static String toKorean(MealType mealType) {
        return mealType == MealType.LUNCH ? "점심" : "저녁";
    }

    // ===== 목록 =====

    /**
     * 식사팟 목록 (오늘, FINISHED·DELETED 제외). 식사팟 목록 화면과 메인 '오늘의 식사팟'에서 쓴다.
     * 파라미터는 모두 선택값이라 문자열로 받아 여기서 검사한다. (잘못된 값은 "조회 조건을 확인해 주세요.")
     */
    public PartyListResponse getPartyList(String date, String mealType, String status, String excludeJoined,
                                          String limit, Long memberId) {
        // 1. 파라미터 검증 (date가 없으면 오늘, 오늘 ~ 14일 후만)
        LocalDate partyDate = parseListDate(date, PartyTimePolicy.today());
        MealType mealTypeFilter = parseMealType(mealType);
        PartyStatus statusFilter = parseListStatus(status);
        boolean isExcludeJoined = parseBoolean(excludeJoined);
        Integer limitValue = parseLimit(limit);

        // 2. 그 날짜 + FINISHED·DELETED 제외 + 요청 조건(status, excludeJoined)
        //    하루치라 개수가 적어서 한 번에 가져와 자바에서 거른다.
        List<MealParty> dayParties = mealPartyRepository.findAllByPartyDateExcludingStatuses(
                partyDate, EnumSet.of(PartyStatus.FINISHED, PartyStatus.DELETED));
        Set<Long> joinedPartyIds = getJoinedPartyIds(memberId, partyDate);

        // 3. 정렬: 기준 시간 → partyId
        //    비공개 식사팟은 내가 참여한 것만 보인다 (2026-11-25 T4)
        List<MealParty> filtered = dayParties.stream()
                .filter(p -> !p.isPrivateParty() || joinedPartyIds.contains(p.getPartyId()))
                .filter(p -> statusFilter == null || p.getStatus() == statusFilter)
                .filter(p -> !isExcludeJoined || !joinedPartyIds.contains(p.getPartyId()))
                .sorted(Comparator.comparing(MealParty::getBaseTime).thenComparing(MealParty::getPartyId))
                .toList();

        // 5. 끼니별 개수 (탭 숫자): mealType·limit과 관계없이 센다
        int lunchCount = (int) filtered.stream().filter(p -> p.getMealType() == MealType.LUNCH).count();
        int dinnerCount = (int) filtered.stream().filter(p -> p.getMealType() == MealType.DINNER).count();

        // 6. 끼니 필터 + limit
        List<MealParty> result = filtered.stream()
                .filter(p -> mealTypeFilter == null || p.getMealType() == mealTypeFilter)
                .limit(limitValue == null ? Long.MAX_VALUE : limitValue)
                .toList();

        // 4. 팟별 인원 수 (쿼리 한 번) + 내 참여 여부
        Map<Long, PartyMemberCount> counts = countMembers(result);
        List<PartyListItemResponse> items = result.stream()
                .map(p -> PartyListItemResponse.of(
                        p,
                        memberCountOf(counts, p.getPartyId()),
                        joinedPartyIds.contains(p.getPartyId())))
                .toList();

        return new PartyListResponse(lunchCount, dinnerCount, items);
    }

    /**
     * 내가 참여 중인 오늘 식사팟 (최대 2개). 메인 '참여 중인 식사팟' 카드용.
     * DELETED는 빼고 FINISHED(식사 완료)는 포함한다. 없으면 빈 배열.
     */
    public List<PartyMyTodayResponse> getMyTodayPartyList(Long memberId) {
        // 1. 오늘 ~ 14일 후 + DELETED 제외 + 내가 참여한 팟 (11/20 변경: 오늘만 → 오늘~14일 후)
        LocalDate today = PartyTimePolicy.today();
        List<PartyMember> myParties = partyMemberRepository.findAllByMemberIdAndPartyDateBetween(
                memberId, today, today.plusDays(PartyTimePolicy.MAX_DAYS_AHEAD), EnumSet.of(PartyStatus.DELETED));

        // 2. 정렬: FINISHED가 아닌 팟 먼저 → 날짜 → 기준 시간 → partyId, 4. 최대 2개
        List<PartyMember> top = myParties.stream()
                .sorted(Comparator.comparing((PartyMember pm) -> pm.getParty().isFinished())
                        .thenComparing(pm -> pm.getParty().getPartyDate())
                        .thenComparing(pm -> pm.getParty().getBaseTime())
                        .thenComparing(pm -> pm.getParty().getPartyId()))
                .limit(MY_TODAY_LIMIT)
                .toList();

        // 3. 참여 인원·확정 인원(쿼리 한 번), 내 확정 여부
        Map<Long, PartyMemberCount> counts = countMembers(top.stream().map(PartyMember::getParty).toList());
        return top.stream()
                .map(pm -> {
                    Long partyId = pm.getParty().getPartyId();
                    return PartyMyTodayResponse.of(
                            pm.getParty(),
                            memberCountOf(counts, partyId),
                            confirmedCountOf(counts, partyId),
                            pm.isConfirmed());
                })
                .toList();
    }

    /**
     * 내가 할 일이 있는 식사팟 (헤더 종 알림, 2026-11-23)
     * - 방장: 아직 일정을 안 정한 팟 → SET_SCHEDULE
     * - 참여자(방장 포함): 일정은 정해졌는데 내가 참석 확정을 안 한 팟 → CONFIRM
     * 대상: 오늘 ~ 14일 후, FINISHED·DELETED 제외, 기준 시간이 아직 안 지난 팟. 정렬: 날짜 → 기준 시간 → partyId
     * 조건이 풀리면(일정 확정·참석 확정·삭제·식사 완료) 목록에서 저절로 빠진다. 읽음 표시는 저장하지 않는다.
     */
    public List<PartyActionPendingResponse> getActionPendingList(Long memberId) {
        return getActionPendingList(memberId, PartyTimePolicy.now());
    }

    /** 현재 시각을 밖에서 받는 버전 (테스트용) */
    List<PartyActionPendingResponse> getActionPendingList(Long memberId, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<PartyMember> myParties = partyMemberRepository.findAllByMemberIdAndPartyDateBetween(
                memberId, today, today.plusDays(PartyTimePolicy.MAX_DAYS_AHEAD),
                EnumSet.of(PartyStatus.FINISHED, PartyStatus.DELETED));

        return myParties.stream()
                .filter(pm -> !PartyTimePolicy.isMealTimePassed(
                        pm.getParty().getPartyDate(), pm.getParty().getBaseTime(), now))
                .sorted(Comparator.comparing((PartyMember pm) -> pm.getParty().getPartyDate())
                        .thenComparing(pm -> pm.getParty().getBaseTime())
                        .thenComparing(pm -> pm.getParty().getPartyId()))
                .map(pm -> {
                    MealParty party = pm.getParty();
                    if (!party.hasSchedule()) {
                        return party.isHost(memberId)
                                ? PartyActionPendingResponse.of(party, PartyAction.SET_SCHEDULE)
                                : null;
                    }
                    return pm.isConfirmed() ? null : PartyActionPendingResponse.of(party, PartyAction.CONFIRM);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ===== 회원 탈퇴 (C의 탈퇴 API가 호출, 2026-11-24) =====

    /** 진행 중인 팟 상태 (FINISHED·DELETED가 아닌 것) */
    private static final Set<PartyStatus> ACTIVE_STATUSES =
            EnumSet.of(PartyStatus.RECRUITING, PartyStatus.SCHEDULED, PartyStatus.FULL, PartyStatus.CLOSED);

    /**
     * 탈퇴하는 회원의 식사팟 정리. 회원 정보(닉네임 등)를 바꾸기 전에, 같은 트랜잭션 안에서 호출한다.
     * - 대상: 진행 중(RECRUITING·SCHEDULED·FULL·CLOSED) 팟만. FINISHED·DELETED는 그대로 둔다(이력·따봉 유지).
     * - 방장인 팟 → DELETED + 시스템 메시지 (방장 나가기와 같은 처리)
     * - 참여자인 팟 → 나가기 (30분 전 제한은 무시) + "○○님이 나갔어요". FULL이었다면 SCHEDULED/RECRUITING
     * - 팟마다 행 잠금(SELECT ... FOR UPDATE). 교착을 막으려고 partyId 오름차순으로 잠근다.
     *
     * @return 정리 결과 (삭제한 팟 수, 나간 팟 수)
     */
    @Transactional
    public PartyWithdrawalResult cleanUpForWithdrawal(Long memberId) {
        int deletedCount = 0;
        int leftCount = 0;

        for (Long partyId : mealPartyRepository.findPartyIdsOfMember(memberId, ACTIVE_STATUSES)) {
            MealParty party = mealPartyRepository.findByIdForUpdate(partyId).orElse(null);
            // 잠그기 전에 다른 요청으로 끝났거나 삭제됐으면 건너뛴다
            if (party == null || !party.getStatus().isActive()) {
                continue;
            }

            if (party.isHost(memberId)) {
                party.delete();
                partyChatRepository.save(PartyChat.createSystem(party, "방장이 탈퇴해 식사팟이 삭제됐어요"));
                deletedCount++;
                continue;
            }

            PartyMember partyMember = partyMemberRepository.findByPartyIdAndMemberId(partyId, memberId).orElse(null);
            if (partyMember == null) {
                continue;
            }
            int memberCountAfter = (int) partyMemberRepository.countByPartyId(partyId) - 1;
            partyMemberRepository.delete(partyMember);
            party.leave(memberCountAfter);
            partyChatRepository.save(PartyChat.createSystem(
                    party, partyMember.getMember().getNickname() + "님이 나갔어요"));
            leftCount++;
        }
        return new PartyWithdrawalResult(deletedCount, leftCount);
    }

    /** 탈퇴 정리 결과 (로그·테스트 확인용) */
    public record PartyWithdrawalResult(int deletedPartyCount, int leftPartyCount) {
    }

    // ===== 목록 도우미 =====

    private Set<Long> getJoinedPartyIds(Long memberId, LocalDate date) {
        return partyMemberRepository.findAllByMemberIdAndPartyDate(memberId, date, EnumSet.of(PartyStatus.DELETED))
                .stream()
                .map(pm -> pm.getParty().getPartyId())
                .collect(Collectors.toSet());
    }

    /** 팟 ID → 인원 집계. 팟이 없으면 쿼리를 날리지 않는다. (Oracle은 빈 IN () 불가) */
    private Map<Long, PartyMemberCount> countMembers(List<MealParty> parties) {
        if (parties.isEmpty()) {
            return Map.of();
        }
        List<Long> partyIds = parties.stream().map(MealParty::getPartyId).toList();
        return partyMemberRepository.countByPartyIds(partyIds).stream()
                .collect(Collectors.toMap(PartyMemberCount::partyId, Function.identity()));
    }

    private static int memberCountOf(Map<Long, PartyMemberCount> counts, Long partyId) {
        PartyMemberCount count = counts.get(partyId);
        return count == null ? 0 : count.memberCount().intValue();
    }

    private static int confirmedCountOf(Map<Long, PartyMemberCount> counts, Long partyId) {
        PartyMemberCount count = counts.get(partyId);
        return count == null || count.confirmedCount() == null ? 0 : count.confirmedCount().intValue();
    }

    private static MealType parseMealType(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return MealType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, LIST_INVALID_MESSAGE);
        }
    }

    /** 목록에서 고를 수 있는 상태는 RECRUITING, SCHEDULED, FULL, CLOSED 뿐 */
    private static PartyStatus parseListStatus(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            PartyStatus status = PartyStatus.valueOf(value);
            if (status.isActive()) {
                return status;
            }
        } catch (IllegalArgumentException e) {
            // 아래에서 처리
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, LIST_INVALID_MESSAGE);
    }

    private static boolean parseBoolean(String value) {
        if (isBlank(value) || "false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, LIST_INVALID_MESSAGE);
    }

    /** 목록 날짜: 없으면 오늘. 'YYYY-MM-DD' 형식이고 오늘 ~ 14일 후여야 한다 */
    private static LocalDate parseListDate(String value, LocalDate today) {
        if (isBlank(value)) {
            return today;
        }
        try {
            LocalDate date = LocalDate.parse(value.trim());
            if (PartyTimePolicy.isWithinDateRange(date, today)) {
                return date;
            }
        } catch (DateTimeParseException e) {
            // 아래에서 처리
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, LIST_INVALID_MESSAGE);
    }

    private static Integer parseLimit(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            int limit = Integer.parseInt(value.trim());
            if (limit >= 1 && limit <= LIST_LIMIT_MAX) {
                return limit;
            }
        } catch (NumberFormatException e) {
            // 아래에서 처리
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, LIST_INVALID_MESSAGE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
