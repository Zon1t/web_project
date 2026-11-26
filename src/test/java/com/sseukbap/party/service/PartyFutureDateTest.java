package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyListItemResponse;
import com.sseukbap.party.dto.PartyMyTodayResponse;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 11/20 변경: 식사팟 날짜 확대 (당일만 → 오늘 ~ 14일 후) 확인 (실제 DB, 끝나면 롤백)
 * 더미 회원의 오늘 팟과 섞이지 않도록 새 회원을 만들어 쓴다.
 */
@SpringBootTest
@Transactional
class PartyFutureDateTest {

    @Autowired PartyService partyService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyHostService partyHostService;
    @Autowired PartyBatchService partyBatchService;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;
    @Autowired JsonMapper jsonMapper;

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final LocalDateTime LATE_NIGHT = TODAY.atTime(23, 0);   // 오늘 끼니 시간이 모두 지난 시각

    private Member host;
    private Member guest;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        host = memberRepository.save(Member.create("futurehost1", "x".repeat(60), "미래방장"));
        guest = memberRepository.save(Member.create("futureguest1", "x".repeat(60), "미래손님"));
        restaurant = restaurantRepository.findAll().get(0);
    }

    // ===== 생성 =====

    @Test
    void 밤늦게도_내일_점심_팟은_만들_수_있다() {
        Long partyId = create(TOMORROW, MealType.LUNCH, "11:00", LATE_NIGHT);

        MealParty party = mealPartyRepository.findById(partyId).orElseThrow();
        assertThat(party.getPartyDate()).isEqualTo(TOMORROW);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.RECRUITING);
    }

    @Test
    void 날짜가_없으면_오늘_팟이고_오늘은_지난_시간을_막는다() {
        Long partyId = partyService.createParty(
                new PartyCreateRequest(restaurant.getRestaurantId(), MealType.DINNER, LocalTime.of(19, 30), 4, null),
                host.getMemberId(), TODAY.atTime(9, 0)).partyId();
        assertThat(mealPartyRepository.findById(partyId).orElseThrow().getPartyDate()).isEqualTo(TODAY);

        assertErrorCode(() -> create(TODAY, MealType.LUNCH, "12:00", TODAY.atTime(13, 0)), ErrorCode.INVALID_MEAL_TIME);
    }

    @Test
    void 날짜는_오늘부터_14일_후까지만() {
        assertThat(create(TODAY.plusDays(14), MealType.LUNCH, "12:00", LATE_NIGHT)).isNotNull();

        assertErrorCode(() -> create(TODAY.plusDays(15), MealType.LUNCH, "12:00", LATE_NIGHT), ErrorCode.INVALID_REQUEST);
        assertErrorCode(() -> create(TODAY.minusDays(1), MealType.LUNCH, "12:00", LATE_NIGHT), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 미래_날짜도_끼니_시간대_규칙은_그대로() {
        assertErrorCode(() -> create(TOMORROW, MealType.LUNCH, "18:00", LATE_NIGHT), ErrorCode.INVALID_MEAL_TIME);
        assertErrorCode(() -> create(TOMORROW, MealType.DINNER, "17:15", LATE_NIGHT), ErrorCode.INVALID_MEAL_TIME);
    }

    @Test
    void 요청_JSON의_partyDate를_읽는다() throws Exception {
        PartyCreateRequest withDate = jsonMapper.readValue(
                "{\"restaurantId\":1,\"partyDate\":\"" + TOMORROW + "\",\"mealType\":\"LUNCH\",\"mealTime\":\"12:00\",\"capacity\":4}",
                PartyCreateRequest.class);
        PartyCreateRequest withoutDate = jsonMapper.readValue(
                "{\"restaurantId\":1,\"mealType\":\"LUNCH\",\"mealTime\":\"12:00\",\"capacity\":4}",
                PartyCreateRequest.class);

        assertThat(withDate.partyDate()).isEqualTo(TOMORROW);
        assertThat(withDate.mealTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(withoutDate.partyDate()).isNull();
    }

    // ===== 목록 =====

    @Test
    void 목록은_고른_날짜의_팟만_보여_준다() {
        Long tomorrowParty = create(TOMORROW, MealType.LUNCH, "12:00", LATE_NIGHT);

        List<Long> todayIds = ids(partyService.getPartyList(null, null, null, null, null, host.getMemberId()).parties());
        List<PartyListItemResponse> tomorrowList =
                partyService.getPartyList(TOMORROW.toString(), null, null, null, null, host.getMemberId()).parties();

        assertThat(todayIds).doesNotContain(tomorrowParty);
        assertThat(ids(tomorrowList)).contains(tomorrowParty);
        assertThat(tomorrowList).filteredOn(p -> p.partyId().equals(tomorrowParty))
                .allMatch(PartyListItemResponse::joined);
    }

    @Test
    void 목록_날짜가_범위_밖이거나_형식이_틀리면_INVALID_REQUEST() {
        for (String date : new String[]{TODAY.plusDays(15).toString(), TODAY.minusDays(1).toString(), "2026-13-40", "abc"}) {
            assertThatThrownBy(() -> partyService.getPartyList(date, null, null, null, null, host.getMemberId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("조회 조건을 확인해 주세요.")
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        }
    }

    // ===== 참여 중인 식사팟 (메인) =====

    @Test
    void 참여_중인_식사팟은_오늘부터_날짜_시간순으로_2개() {
        Long dayAfter = create(TODAY.plusDays(2), MealType.LUNCH, "11:00", TODAY.atTime(9, 0));
        Long tomorrowDinner = create(TOMORROW, MealType.DINNER, "18:00", TODAY.atTime(9, 0));
        Long tomorrowLunch = create(TOMORROW, MealType.LUNCH, "12:00", TODAY.atTime(9, 0));
        Long todayDinner = create(TODAY, MealType.DINNER, "19:30", TODAY.atTime(9, 0));

        List<PartyMyTodayResponse> mine = partyService.getMyTodayPartyList(host.getMemberId());

        assertThat(mine).extracting(PartyMyTodayResponse::partyId).containsExactly(todayDinner, tomorrowLunch);
        assertThat(mine).extracting(PartyMyTodayResponse::partyDate).containsExactly(TODAY, TOMORROW);
        assertThat(List.of(dayAfter, tomorrowDinner)).doesNotContainAnyElementsOf(
                mine.stream().map(PartyMyTodayResponse::partyId).toList());
    }

    @Test
    void 오늘_식사_완료된_팟은_다가오는_팟보다_뒤로() {
        Long todayLunch = create(TODAY, MealType.LUNCH, "12:00", TODAY.atTime(9, 0));
        Long tomorrow = create(TOMORROW, MealType.LUNCH, "12:00", TODAY.atTime(9, 0));
        partyBatchService.finishParty(todayLunch, TODAY.atTime(12, 1));
        flushAndClear();

        assertThat(partyService.getMyTodayPartyList(host.getMemberId()))
                .extracting(PartyMyTodayResponse::partyId).containsExactly(tomorrow, todayLunch);
    }

    @Test
    void 일주일_넘은_팟이나_지난_팟은_안_나온다() {
        Long yesterday = create(TODAY, MealType.DINNER, "19:30", TODAY.atTime(9, 0));
        setPartyDate(yesterday, TODAY.minusDays(1));

        assertThat(partyService.getMyTodayPartyList(host.getMemberId())).isEmpty();
    }

    // ===== 참여·일정·초대·스케줄러 =====

    @Test
    void 내일_팟도_참여하고_일정을_확정할_수_있다() {
        Long partyId = create(TOMORROW, MealType.LUNCH, "12:00", LATE_NIGHT);

        partyMemberService.joinParty(partyId, guest.getMemberId(), PartyTestSupport.MORNING);
        // 오늘 밤이라도 내일 11:30 일정은 '지금 이후'라 가능
        partyHostService.updateSchedule(partyId, new PartyScheduleUpdateRequest(LocalTime.of(11, 30), "정문", null),
                host.getMemberId(), LATE_NIGHT);
        flushAndClear();

        MealParty party = mealPartyRepository.findById(partyId).orElseThrow();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.SCHEDULED);
        assertThat(party.getConfirmedTime()).isEqualTo(LocalTime.of(11, 30));
    }

    @Test
    void 내일_팟_초대_문구에는_날짜가_들어간다() {
        Long tomorrowParty = create(TOMORROW, MealType.LUNCH, "12:00", LATE_NIGHT);
        Long todayParty = create(TODAY, MealType.DINNER, "19:30", TODAY.atTime(9, 0));

        assertThat(partyService.getInviteLink(tomorrowParty, host.getMemberId()).text())
                .isEqualTo(host.getNickname() + "님이 " + TOMORROW.getMonthValue() + "월 " + TOMORROW.getDayOfMonth()
                        + "일 " + restaurant.getName() + " 점심 식사에 초대하셨습니다~");
        assertThat(partyService.getInviteLink(todayParty, host.getMemberId()).text())
                .isEqualTo(host.getNickname() + "님이 " + restaurant.getName() + " 저녁 식사에 초대하셨습니다~");
    }

    @Test
    void 스케줄러는_내일_팟을_완료_처리하지_않는다() {
        Long tomorrowParty = create(TOMORROW, MealType.LUNCH, "11:00", LATE_NIGHT);

        assertThat(partyBatchService.findFinishTargetIds(LATE_NIGHT)).doesNotContain(tomorrowParty);
        assertThat(partyBatchService.findFinishTargetIds(TOMORROW.atTime(11, 1))).contains(tomorrowParty);
    }

    // ===== 도우미 =====

    private Long create(LocalDate partyDate, MealType mealType, String mealTime, LocalDateTime now) {
        return partyService.createParty(
                new PartyCreateRequest(restaurant.getRestaurantId(), partyDate, mealType, LocalTime.parse(mealTime), 4, null),
                host.getMemberId(), now).partyId();
    }

    private void setPartyDate(Long partyId, LocalDate date) {
        em.flush();
        em.createQuery("update MealParty p set p.partyDate = :date where p.partyId = :id")
                .setParameter("date", date)
                .setParameter("id", partyId)
                .executeUpdate();
        em.clear();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static List<Long> ids(List<PartyListItemResponse> items) {
        return items.stream().map(PartyListItemResponse::partyId).toList();
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
