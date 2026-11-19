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
import com.sseukbap.party.entity.PartyStatus;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 식사팟 목록·내 오늘 식사팟 확인 (실제 DB, 끝나면 롤백)
 *
 * 기대값은 docs/sql/04_dummy_data.sql 기준 (오늘 날짜로 들어간 팟, DELETED 제외)
 *   점심: 계절밥상 11:00 RECRUITING(user04) / 한솥가득 백반 11:30 RECRUITING(user01 방장, user02)
 *         스시하루 12:00 SCHEDULED(user03 방장·확정, user04 확정, user05)
 *   저녁: 파스타앤코 17:30 CLOSED(user04 방장, user03) / 홍반점 18:00 FULL(user02 방장, user01, user05)
 * 더미데이터를 넣은 날이 지나면 00 → 04를 다시 실행해야 맞는다.
 */
@SpringBootTest
@Transactional
class PartyListServiceTest {

    @Autowired PartyService partyService;
    @Autowired MemberRepository memberRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired EntityManager em;
    @Autowired JsonMapper jsonMapper;

    private Member user01;
    private Member user03;
    private Member user05;

    @BeforeEach
    void setUp() {
        user01 = memberRepository.findByLoginId("user01").orElseThrow();
        user03 = memberRepository.findByLoginId("user03").orElseThrow();
        user05 = memberRepository.findByLoginId("user05").orElseThrow();
    }

    // ===== 목록 =====

    @Test
    void 조건이_없으면_오늘_진행중인_팟을_기준시간순으로() {
        PartyListResponse response = partyService.getPartyList(null, null, null, null, user01.getMemberId());

        assertThat(response.lunchCount()).isEqualTo(3);
        assertThat(response.dinnerCount()).isEqualTo(2);
        assertThat(names(response)).containsExactly("계절밥상", "한솥가득 백반", "스시하루", "파스타앤코", "홍반점");
        assertThat(response.parties()).extracting(PartyListItemResponse::status)
                .doesNotContain(PartyStatus.FINISHED, PartyStatus.DELETED);
    }

    @Test
    void 인원수와_내_참여여부가_나온다() {
        PartyListResponse response = partyService.getPartyList(null, null, null, null, user01.getMemberId());

        PartyListItemResponse hansot = find(response, "한솥가득 백반");
        assertThat(hansot.memberCount()).isEqualTo(2);
        assertThat(hansot.joined()).isTrue();

        PartyListItemResponse sushi = find(response, "스시하루");
        assertThat(sushi.memberCount()).isEqualTo(3);
        assertThat(sushi.joined()).isFalse();
        assertThat(sushi.time()).isEqualTo(LocalTime.of(12, 0));   // 확정 시간
    }

    @Test
    void 끼니_필터를_걸어도_탭_숫자는_그대로() {
        PartyListResponse response = partyService.getPartyList("LUNCH", null, null, null, user01.getMemberId());

        assertThat(names(response)).containsExactly("계절밥상", "한솥가득 백반", "스시하루");
        assertThat(response.lunchCount()).isEqualTo(3);
        assertThat(response.dinnerCount()).isEqualTo(2);
    }

    @Test
    void 상태_필터는_탭_숫자에도_적용된다() {
        PartyListResponse response = partyService.getPartyList(null, "RECRUITING", null, null, user01.getMemberId());

        assertThat(names(response)).containsExactly("계절밥상", "한솥가득 백반");
        assertThat(response.lunchCount()).isEqualTo(2);
        assertThat(response.dinnerCount()).isZero();
    }

    @Test
    void 내가_참여한_팟_제외() {
        // user01은 한솥가득 백반(방장), 홍반점에 참여 중
        PartyListResponse response = partyService.getPartyList(null, null, "true", null, user01.getMemberId());

        assertThat(names(response)).containsExactly("계절밥상", "스시하루", "파스타앤코");
        assertThat(response.parties()).noneMatch(PartyListItemResponse::joined);
        assertThat(response.lunchCount()).isEqualTo(2);
        assertThat(response.dinnerCount()).isEqualTo(1);
    }

    @Test
    void 메인_오늘의_식사팟_조건() {
        // 메인: excludeJoined=true, status=RECRUITING, limit=3
        PartyListResponse response = partyService.getPartyList(null, "RECRUITING", "true", "3", user01.getMemberId());

        assertThat(names(response)).containsExactly("계절밥상");
    }

    @Test
    void limit만큼_자르고_탭_숫자는_그대로() {
        PartyListResponse response = partyService.getPartyList(null, null, null, "2", user01.getMemberId());

        assertThat(response.parties()).hasSize(2);
        assertThat(response.lunchCount()).isEqualTo(3);
        assertThat(response.dinnerCount()).isEqualTo(2);
    }

    @Test
    void 삭제한_팟은_목록에_안_나온다() {
        Long partyId = createParty(user01, MealType.DINNER, LocalTime.of(19, 0));
        assertThat(ids(partyService.getPartyList(null, null, null, null, user01.getMemberId()))).contains(partyId);

        partyService.deleteParty(partyId, user01.getMemberId());
        em.flush();
        em.clear();

        assertThat(ids(partyService.getPartyList(null, null, null, null, user01.getMemberId()))).doesNotContain(partyId);
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
        assertThatThrownBy(() -> partyService.getPartyList(mealType, status, excludeJoined, limit, user01.getMemberId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건을 확인해 주세요.")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 목록_JSON의_time은_HH_mm() throws Exception {
        PartyListResponse response = partyService.getPartyList("LUNCH", null, null, "1", user01.getMemberId());

        String json = jsonMapper.writeValueAsString(response);
        assertThat(json).contains("\"time\":\"11:00\"", "\"lunchCount\":3", "\"joined\":false");
    }

    // ===== 내 오늘 식사팟 =====

    @Test
    void 내_오늘_식사팟은_기준시간순_최대_2개() {
        List<PartyMyTodayResponse> mine = partyService.getMyTodayPartyList(user01.getMemberId());

        assertThat(mine).extracting(PartyMyTodayResponse::restaurantName).containsExactly("한솥가득 백반", "홍반점");
        assertThat(mine.get(1).memberCount()).isEqualTo(3);
        assertThat(mine.get(1).status()).isEqualTo(PartyStatus.FULL);
        assertThat(mine.get(0).meetPlace()).isNull();   // 일정 미확정
    }

    @Test
    void 확정_인원과_내_확정여부_만날장소가_나온다() {
        List<PartyMyTodayResponse> mine = partyService.getMyTodayPartyList(user03.getMemberId());

        PartyMyTodayResponse sushi = mine.get(0);
        assertThat(sushi.restaurantName()).isEqualTo("스시하루");
        assertThat(sushi.memberCount()).isEqualTo(3);
        assertThat(sushi.confirmedCount()).isEqualTo(2);
        assertThat(sushi.myConfirmed()).isTrue();
        assertThat(sushi.meetPlace()).isEqualTo("본관 1층 로비");
    }

    @Test
    void 삭제된_팟은_빠진다() {
        // user05는 삭제된 버거스테이션(12:30)에도 들어 있다
        List<PartyMyTodayResponse> mine = partyService.getMyTodayPartyList(user05.getMemberId());

        assertThat(mine).extracting(PartyMyTodayResponse::restaurantName).containsExactly("스시하루", "홍반점");
    }

    @Test
    void 식사_완료된_팟은_뒤로_밀린다() {
        // user03에게 가장 이른 11:00 팟을 만들고 FINISHED로 바꾼다 → 앞의 2개는 그대로
        Long finishedId = createParty(user03, MealType.LUNCH, LocalTime.of(11, 0));
        em.flush();
        em.createQuery("update MealParty p set p.status = :status where p.partyId = :id")
                .setParameter("status", PartyStatus.FINISHED)
                .setParameter("id", finishedId)
                .executeUpdate();
        em.clear();

        List<PartyMyTodayResponse> mine = partyService.getMyTodayPartyList(user03.getMemberId());

        assertThat(mine).extracting(PartyMyTodayResponse::restaurantName).containsExactly("스시하루", "파스타앤코");
    }

    @Test
    void 참여한_팟이_없으면_빈_배열() {
        Member newbie = memberRepository.save(Member.create("listtest01", "x".repeat(60), "목록테스트"));

        assertThat(partyService.getMyTodayPartyList(newbie.getMemberId())).isEmpty();
    }

    // ===== 도우미 =====

    private Long createParty(Member host, MealType mealType, LocalTime mealTime) {
        PartyCreateRequest request = new PartyCreateRequest(
                restaurantRepository.findAll().get(0).getRestaurantId(), mealType, mealTime, 4, null);
        return partyService.createParty(request, host.getMemberId(), LocalDate.now().atTime(9, 0)).partyId();
    }

    private static List<String> names(PartyListResponse response) {
        return response.parties().stream().map(PartyListItemResponse::restaurantName).toList();
    }

    private static List<Long> ids(PartyListResponse response) {
        return response.parties().stream().map(PartyListItemResponse::partyId).toList();
    }

    private static PartyListItemResponse find(PartyListResponse response, String restaurantName) {
        return response.parties().stream()
                .filter(p -> p.restaurantName().equals(restaurantName))
                .findFirst()
                .orElseThrow();
    }
}
