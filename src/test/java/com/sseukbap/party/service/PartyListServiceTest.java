package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyListItemResponse;
import com.sseukbap.party.dto.PartyListResponse;
import com.sseukbap.party.dto.PartyMyTodayResponse;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.dto.PartyStatusUpdateRequest;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * 식사팟 목록·내 오늘 식사팟 확인 (실제 DB, 끝나면 롤백)
 *
 * 스케줄러가 시간이 지난 더미 팟을 FINISHED로 바꾸기 때문에 더미 팟 상태에 기대지 않는다.
 * 테스트마다 오늘 ~ 14일 후의 기존 팟을 잠시 DELETED로 숨기고(롤백됨), 아래 팟을 직접 만든다. "지금 = 오늘 09:00"
 *   점심: cafe  11:00 RECRUITING (user04)
 *         hansot 11:30 RECRUITING (user01 방장, user02)
 *         sushi 12:00 SCHEDULED  (user03 방장·확정, user04 확정, user05) 만날 장소 "본관 1층 로비"
 *         burger 12:30 DELETED    (user05)
 *   저녁: pasta 17:30 CLOSED     (user04 방장, user03)
 *         hong  18:00 FULL       (user02 방장, user01, user05) 정원 3
 */
@SpringBootTest
@Transactional
class PartyListServiceTest {

    @Autowired PartyService partyService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyHostService partyHostService;
    @Autowired MemberRepository memberRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired EntityManager em;
    @Autowired JsonMapper jsonMapper;

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDateTime NINE_AM = TODAY.atTime(9, 0);

    private Member user01;
    private Member user02;
    private Member user03;
    private Member user04;
    private Member user05;
    private Restaurant restaurant;

    private Long cafe;
    private Long hansot;
    private Long sushi;
    private Long burger;
    private Long pasta;
    private Long hong;

    @BeforeEach
    void setUp() {
        user01 = member("user01");
        user02 = member("user02");
        user03 = member("user03");
        user04 = member("user04");
        user05 = member("user05");
        restaurant = restaurantRepository.findAll().get(0);

        // 오늘 ~ 14일 후의 기존 팟(더미 등)을 숨긴다. 내 식사팟(my/today)이 이 범위를 보기 때문.
        // 테스트가 끝나면 롤백되어 원래대로 돌아온다.
        em.createQuery("update MealParty p set p.status = :deleted where p.partyDate between :today and :lastDay")
                .setParameter("deleted", PartyStatus.DELETED)
                .setParameter("today", TODAY)
                .setParameter("lastDay", TODAY.plusDays(PartyTimePolicy.MAX_DAYS_AHEAD))
                .executeUpdate();
        em.clear();

        cafe = create(user04, MealType.LUNCH, "11:00", 4);

        hansot = create(user01, MealType.LUNCH, "11:30", 4);
        join(hansot, user02);

        sushi = create(user03, MealType.LUNCH, "12:00", 4);
        join(sushi, user04);
        join(sushi, user05);
        partyHostService.updateSchedule(sushi, new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "본관 1층 로비", null),
                user03.getMemberId(), NINE_AM);
        partyMemberService.confirmParty(sushi, user03.getMemberId());
        partyMemberService.confirmParty(sushi, user04.getMemberId());

        burger = create(user05, MealType.LUNCH, "12:30", 4);
        partyService.deleteParty(burger, user05.getMemberId());

        pasta = create(user04, MealType.DINNER, "17:30", 6);
        join(pasta, user03);
        partyHostService.updateStatus(pasta, new PartyStatusUpdateRequest("CLOSE"), user04.getMemberId());

        hong = create(user02, MealType.DINNER, "18:00", 3);
        join(hong, user01);
        join(hong, user05);   // 정원 도달 → FULL

        em.flush();
        em.clear();
    }

    // ===== 목록 =====

    @Test
    void 조건이_없으면_오늘_진행중인_팟을_기준시간순으로() {
        PartyListResponse response = partyService.getPartyList(null, null, null, null, null, user01.getMemberId());

        assertThat(response.lunchCount()).isEqualTo(3);
        assertThat(response.dinnerCount()).isEqualTo(2);
        assertThat(ids(response)).containsExactly(cafe, hansot, sushi, pasta, hong);   // burger(DELETED) 제외
        assertThat(response.parties()).extracting(PartyListItemResponse::status)
                .containsExactly(PartyStatus.RECRUITING, PartyStatus.RECRUITING, PartyStatus.SCHEDULED,
                        PartyStatus.CLOSED, PartyStatus.FULL);
    }

    @Test
    void 인원수와_내_참여여부가_나온다() {
        PartyListResponse response = partyService.getPartyList(null, null, null, null, null, user01.getMemberId());

        PartyListItemResponse hansotItem = find(response, hansot);
        assertThat(hansotItem.memberCount()).isEqualTo(2);
        assertThat(hansotItem.joined()).isTrue();
        assertThat(hansotItem.restaurantName()).isEqualTo(restaurant.getName());

        PartyListItemResponse sushiItem = find(response, sushi);
        assertThat(sushiItem.memberCount()).isEqualTo(3);
        assertThat(sushiItem.joined()).isFalse();
        assertThat(sushiItem.time()).isEqualTo(LocalTime.of(12, 0));   // 확정 시간
    }

    @Test
    void 끼니_필터를_걸어도_탭_숫자는_그대로() {
        PartyListResponse response = partyService.getPartyList(null, "LUNCH", null, null, null, user01.getMemberId());

        assertThat(ids(response)).containsExactly(cafe, hansot, sushi);
        assertThat(response.lunchCount()).isEqualTo(3);
        assertThat(response.dinnerCount()).isEqualTo(2);
    }

    @Test
    void 상태_필터는_탭_숫자에도_적용된다() {
        PartyListResponse response = partyService.getPartyList(null, null, "RECRUITING", null, null, user01.getMemberId());

        assertThat(ids(response)).containsExactly(cafe, hansot);
        assertThat(response.lunchCount()).isEqualTo(2);
        assertThat(response.dinnerCount()).isZero();
    }

    @Test
    void 내가_참여한_팟_제외() {
        // user01은 hansot(방장), hong에 참여 중
        PartyListResponse response = partyService.getPartyList(null, null, null, "true", null, user01.getMemberId());

        assertThat(ids(response)).containsExactly(cafe, sushi, pasta);
        assertThat(response.parties()).noneMatch(PartyListItemResponse::joined);
        assertThat(response.lunchCount()).isEqualTo(2);
        assertThat(response.dinnerCount()).isEqualTo(1);
    }

    @Test
    void 메인_오늘의_식사팟_조건() {
        // 메인: excludeJoined=true, status=RECRUITING, limit=3
        PartyListResponse response = partyService.getPartyList(null, null, "RECRUITING", "true", "3", user01.getMemberId());

        assertThat(ids(response)).containsExactly(cafe);
    }

    @Test
    void limit만큼_자르고_탭_숫자는_그대로() {
        PartyListResponse response = partyService.getPartyList(null, null, null, null, "2", user01.getMemberId());

        assertThat(ids(response)).containsExactly(cafe, hansot);
        assertThat(response.lunchCount()).isEqualTo(3);
        assertThat(response.dinnerCount()).isEqualTo(2);
    }

    @Test
    void 식사_완료된_팟은_목록에_안_나온다() {
        setStatus(cafe, PartyStatus.FINISHED);

        PartyListResponse response = partyService.getPartyList(null, null, null, null, null, user01.getMemberId());

        assertThat(ids(response)).doesNotContain(cafe);
        assertThat(response.lunchCount()).isEqualTo(2);
    }

    @ParameterizedTest(name = "mealType={0}, status={1}, excludeJoined={2}, limit={3}")
    @CsvSource(value = {
            "BRUNCH, , , ",
            "lunch, , , ",           // 대소문자 구분
            ", FINISHED, , ",        // 목록에서 고를 수 없는 상태
            ", DELETED, , ",
            ", WAITING, , ",
            ", , yes, ",
            ", , , 0",
            ", , , 51",
            ", , , abc",
    })
    void 잘못된_조건은_INVALID_REQUEST(String mealType, String status, String excludeJoined, String limit) {
        assertThatThrownBy(() -> partyService.getPartyList(null, mealType, status, excludeJoined, limit, user01.getMemberId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건을 확인해 주세요.")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 목록_JSON의_time은_HH_mm() throws Exception {
        PartyListResponse response = partyService.getPartyList(null, "LUNCH", null, null, "1", user01.getMemberId());

        String json = jsonMapper.writeValueAsString(response);
        assertThat(json).contains("\"time\":\"11:00\"", "\"lunchCount\":3", "\"joined\":false");
    }

    // ===== 내 오늘 식사팟 =====

    @Test
    void 내_오늘_식사팟은_기준시간순_최대_2개() {
        List<PartyMyTodayResponse> mine = partyService.getMyTodayPartyList(user01.getMemberId());

        assertThat(mine).extracting(PartyMyTodayResponse::partyId).containsExactly(hansot, hong);
        assertThat(mine.get(1).memberCount()).isEqualTo(3);
        assertThat(mine.get(1).status()).isEqualTo(PartyStatus.FULL);
        assertThat(mine.get(0).meetPlace()).isNull();   // 일정 미확정
    }

    @Test
    void 확정_인원과_내_확정여부_만날장소가_나온다() {
        List<PartyMyTodayResponse> mine = partyService.getMyTodayPartyList(user03.getMemberId());

        assertThat(mine).extracting(PartyMyTodayResponse::partyId).containsExactly(sushi, pasta);
        PartyMyTodayResponse sushiCard = mine.get(0);
        assertThat(sushiCard.memberCount()).isEqualTo(3);
        assertThat(sushiCard.confirmedCount()).isEqualTo(2);
        assertThat(sushiCard.myConfirmed()).isTrue();
        assertThat(sushiCard.meetPlace()).isEqualTo("본관 1층 로비");
    }

    @Test
    void 삭제된_팟은_빠진다() {
        // user05는 삭제된 burger(12:30)에도 들어 있다
        List<PartyMyTodayResponse> mine = partyService.getMyTodayPartyList(user05.getMemberId());

        assertThat(mine).extracting(PartyMyTodayResponse::partyId).containsExactly(sushi, hong);
    }

    @Test
    void 식사_완료된_팟도_나오지만_뒤로_밀린다() {
        // user03에게 가장 이른 11:00 팟을 만들고 FINISHED로 → 앞의 2개는 그대로
        Long finished = create(user03, MealType.LUNCH, "11:00", 4);
        setStatus(finished, PartyStatus.FINISHED);
        assertThat(partyService.getMyTodayPartyList(user03.getMemberId()))
                .extracting(PartyMyTodayResponse::partyId).containsExactly(sushi, pasta);

        // 진행 중인 팟이 하나뿐이면 완료된 팟이 두 번째로 나온다
        setStatus(pasta, PartyStatus.DELETED);
        assertThat(partyService.getMyTodayPartyList(user03.getMemberId()))
                .extracting(PartyMyTodayResponse::partyId).containsExactly(sushi, finished);
    }

    @Test
    void 참여한_팟이_없으면_빈_배열() {
        Member newbie = memberRepository.save(Member.create("listtest01", "x".repeat(60), "목록테스트"));

        assertThat(partyService.getMyTodayPartyList(newbie.getMemberId())).isEmpty();
    }

    // ===== 도우미 =====

    private Member member(String loginId) {
        return memberRepository.findByLoginId(loginId).orElseThrow();
    }

    private Long create(Member host, MealType mealType, String mealTime, int capacity) {
        PartyCreateRequest request = new PartyCreateRequest(
                restaurant.getRestaurantId(), mealType, LocalTime.parse(mealTime), capacity, null);
        return partyService.createParty(request, host.getMemberId(), NINE_AM).partyId();
    }

    private void join(Long partyId, Member member) {
        partyMemberService.joinParty(partyId, member.getMemberId());
    }

    private void setStatus(Long partyId, PartyStatus status) {
        em.flush();
        em.createQuery("update MealParty p set p.status = :status where p.partyId = :id")
                .setParameter("status", status)
                .setParameter("id", partyId)
                .executeUpdate();
        em.clear();
    }

    private static List<Long> ids(PartyListResponse response) {
        return response.parties().stream().map(PartyListItemResponse::partyId).toList();
    }

    private static PartyListItemResponse find(PartyListResponse response, Long partyId) {
        return response.parties().stream()
                .filter(p -> p.partyId().equals(partyId))
                .findFirst()
                .orElseThrow();
    }
}
