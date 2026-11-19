package com.sseukbap.party.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyDetailResponse;
import com.sseukbap.party.dto.PartyMemberResponse;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.common.enums.MealType;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyRole;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 식사팟 생성·상세·삭제 확인 (실제 DB, 끝나면 롤백)
 *
 * 실행 전 준비: 로컬 DB에 docs/sql 01 ~ 04 실행, application-local.yml 작성
 * 생성 테스트는 "지금 = 오늘 09:00"으로 시각을 고정해서, 언제 실행해도 결과가 같다.
 */
@SpringBootTest
@Transactional
class PartyServiceTest {

    @Autowired PartyService partyService;
    @Autowired PartyHostService partyHostService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired PartyChatRepository partyChatRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;
    @Autowired JsonMapper jsonMapper;

    private static final LocalDateTime NINE_AM = LocalDate.now().atTime(9, 0);

    private Member user01;   // 방장 역할
    private Member user02;   // 참여자 역할
    private Member user03;   // 미참여자 역할
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        user01 = memberRepository.findByLoginId("user01").orElseThrow();
        user02 = memberRepository.findByLoginId("user02").orElseThrow();
        user03 = memberRepository.findByLoginId("user03").orElseThrow();
        restaurant = restaurantRepository.findAll().get(0);
    }

    // ===== 생성 =====

    @Test
    void 생성하면_방장이_첫_참여자가_되고_시스템_메시지가_남는다() {
        PartyCreateRequest request = new PartyCreateRequest(
                restaurant.getRestaurantId(), MealType.LUNCH, LocalTime.of(12, 0), 4, "같이 드실 분!");

        Long partyId = partyService.createParty(request, user01.getMemberId(), NINE_AM).partyId();
        em.flush();
        em.clear();

        MealParty party = mealPartyRepository.findById(partyId).orElseThrow();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(party.getPartyDate()).isEqualTo(LocalDate.now());
        assertThat(party.getMealTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(party.isHost(user01.getMemberId())).isTrue();

        // 방장 = 첫 참여자, 확정은 N
        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(1);
        assertThat(partyMemberRepository.findByPartyIdAndMemberId(partyId, user01.getMemberId()))
                .hasValueSatisfying(pm -> assertThat(pm.isConfirmed()).isFalse());

        // 시스템 메시지
        List<PartyChat> chats = partyChatRepository.findAllAfter(partyId, 0L);
        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).isSystem()).isTrue();
        assertThat(chats.get(0).getContent()).isEqualTo(user01.getNickname() + "님이 식사팟을 만들었습니다");
    }

    @Test
    void 한마디가_공백뿐이면_null로_저장한다() {
        PartyCreateRequest request = new PartyCreateRequest(
                restaurant.getRestaurantId(), MealType.DINNER, LocalTime.of(18, 0), 4, "   ");

        Long partyId = partyService.createParty(request, user01.getMemberId(), NINE_AM).partyId();

        assertThat(mealPartyRepository.findById(partyId).orElseThrow().getMemo()).isNull();
    }

    @Test
    void 끼니_시간대가_아니거나_30분_단위가_아니면_INVALID_MEAL_TIME() {
        assertErrorCode(() -> partyService.createParty(
                createRequest(MealType.LUNCH, LocalTime.of(18, 0)), user01.getMemberId(), NINE_AM),
                ErrorCode.INVALID_MEAL_TIME);
        assertErrorCode(() -> partyService.createParty(
                createRequest(MealType.LUNCH, LocalTime.of(12, 15)), user01.getMemberId(), NINE_AM),
                ErrorCode.INVALID_MEAL_TIME);
    }

    @Test
    void 지난_시간이면_INVALID_MEAL_TIME() {
        LocalDateTime onePm = LocalDate.now().atTime(13, 0);

        assertErrorCode(() -> partyService.createParty(
                createRequest(MealType.LUNCH, LocalTime.of(12, 30)), user01.getMemberId(), onePm),
                ErrorCode.INVALID_MEAL_TIME);
        assertErrorCode(() -> partyService.createParty(
                createRequest(MealType.LUNCH, LocalTime.of(13, 0)), user01.getMemberId(), onePm),
                ErrorCode.INVALID_MEAL_TIME);   // 같은 시각도 불가
    }

    @Test
    void 없는_식당이면_RESTAURANT_NOT_FOUND() {
        PartyCreateRequest request = new PartyCreateRequest(
                -1L, MealType.LUNCH, LocalTime.of(12, 0), 4, null);

        assertErrorCode(() -> partyService.createParty(request, user01.getMemberId(), NINE_AM),
                ErrorCode.RESTAURANT_NOT_FOUND);
    }

    // ===== 상세 =====

    @Test
    void 상세_역할은_방장_HOST_참여자_MEMBER_미참여자_NONE() {
        Long partyId = createPartyWithGuest();

        PartyDetailResponse byHost = partyService.getParty(partyId, user01.getMemberId());
        PartyDetailResponse byGuest = partyService.getParty(partyId, user02.getMemberId());
        PartyDetailResponse byOther = partyService.getParty(partyId, user03.getMemberId());

        assertThat(byHost.myRole()).isEqualTo(PartyRole.HOST);
        assertThat(byGuest.myRole()).isEqualTo(PartyRole.MEMBER);
        assertThat(byOther.myRole()).isEqualTo(PartyRole.NONE);
        assertThat(byOther.myConfirmed()).isFalse();
    }

    @Test
    void 상세는_참여자_목록과_인원_일정_마감시각을_돌려준다() {
        Long partyId = createPartyWithGuest();

        PartyDetailResponse detail = partyService.getParty(partyId, user02.getMemberId());

        assertThat(detail.restaurantName()).isEqualTo(restaurant.getName());
        assertThat(detail.memberCount()).isEqualTo(2);
        assertThat(detail.confirmedCount()).isZero();
        assertThat(detail.members()).extracting(PartyMemberResponse::nickname)
                .containsExactly(user01.getNickname(), user02.getNickname());   // 방장이 먼저
        assertThat(detail.members()).extracting(PartyMemberResponse::host)
                .containsExactly(true, false);
        assertThat(detail.schedule()).isNull();                                  // 일정 미확정
        assertThat(detail.cancelDeadline()).isEqualTo(LocalDate.now().atTime(11, 30)); // 12:00 - 30분
    }

    @Test
    void 일정이_확정된_팟은_schedule과_확정_시간_기준_마감시각이_나온다() {
        // 예정 12:00 팟에 방장이 12:30 · 정문으로 일정 확정, 방장·user02 참여 확정
        // (더미 SCHEDULED 팟은 스케줄러가 FINISHED로 바꿀 수 있어서 직접 만든다)
        Long partyId = createPartyWithGuest();
        partyHostService.updateSchedule(partyId,
                new PartyScheduleUpdateRequest(LocalTime.of(12, 30), "정문", "늦으면 채팅"), user01.getMemberId(), NINE_AM);
        partyMemberService.confirmParty(partyId, user01.getMemberId());
        partyMemberService.confirmParty(partyId, user02.getMemberId());
        em.flush();
        em.clear();

        PartyDetailResponse detail = partyService.getParty(partyId, user02.getMemberId());

        assertThat(detail.status()).isEqualTo(PartyStatus.SCHEDULED);
        assertThat(detail.schedule()).isNotNull();
        assertThat(detail.schedule().confirmedTime()).isEqualTo(LocalTime.of(12, 30));
        assertThat(detail.schedule().meetPlace()).isEqualTo("정문");
        assertThat(detail.mealTime()).isEqualTo(LocalTime.of(12, 0));                    // 예정 시간은 그대로
        assertThat(detail.cancelDeadline()).isEqualTo(LocalDate.now().atTime(12, 0));    // 확정 12:30 - 30분
        assertThat(detail.confirmedCount()).isEqualTo(2);
        assertThat(detail.myConfirmed()).isTrue();
    }

    @Test
    void 상세_JSON은_명세_형식이다() throws Exception {
        Long partyId = createPartyWithGuest();

        JsonNode json = jsonMapper.readTree(
                jsonMapper.writeValueAsString(partyService.getParty(partyId, user01.getMemberId())));

        assertThat(json.get("mealTime").asString()).isEqualTo("12:00");
        assertThat(json.get("partyDate").asString()).isEqualTo(LocalDate.now().toString());
        assertThat(json.get("cancelDeadline").asString()).isEqualTo(LocalDate.now() + "T11:30:00");
        assertThat(json.get("schedule").isNull()).isTrue();
        assertThat(json.get("myRole").asString()).isEqualTo("HOST");
        assertThat(json.get("members").get(0).get("host").asBoolean()).isTrue();
    }

    @Test
    void 없는_팟은_PARTY_NOT_FOUND() {
        assertErrorCode(() -> partyService.getParty(-1L, user01.getMemberId()), ErrorCode.PARTY_NOT_FOUND);
    }

    // ===== 삭제 =====

    @Test
    void 방장이_삭제하면_DELETED가_되고_상세는_PARTY_DELETED() {
        Long partyId = createPartyWithGuest();

        partyService.deleteParty(partyId, user01.getMemberId());
        em.flush();
        em.clear();

        assertThat(mealPartyRepository.findById(partyId).orElseThrow().getStatus()).isEqualTo(PartyStatus.DELETED);
        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(2);   // 참여자 행은 그대로
        assertErrorCode(() -> partyService.getParty(partyId, user01.getMemberId()), ErrorCode.PARTY_DELETED);
        assertErrorCode(() -> partyService.deleteParty(partyId, user01.getMemberId()), ErrorCode.PARTY_DELETED);
    }

    @Test
    void 방장이_아니면_NOT_HOST() {
        Long partyId = createPartyWithGuest();

        assertThatThrownBy(() -> partyService.deleteParty(partyId, user02.getMemberId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("방장만 삭제할 수 있습니다.")
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_HOST);
    }

    @Test
    void 식사_완료된_팟은_PARTY_FINISHED() {
        MealParty finished = mealPartyRepository.findAll().stream()
                .filter(MealParty::isFinished)
                .findFirst()
                .orElseThrow();

        assertErrorCode(() -> partyService.deleteParty(finished.getPartyId(), finished.getHost().getMemberId()),
                ErrorCode.PARTY_FINISHED);
    }

    // ===== 도우미 =====

    private PartyCreateRequest createRequest(MealType mealType, LocalTime mealTime) {
        return new PartyCreateRequest(restaurant.getRestaurantId(), mealType, mealTime, 4, null);
    }

    /** user01이 오늘 12:00 점심 팟을 만들고 user02가 참여한 상태 */
    private Long createPartyWithGuest() {
        Long partyId = partyService.createParty(
                createRequest(MealType.LUNCH, LocalTime.of(12, 0)), user01.getMemberId(), NINE_AM).partyId();
        MealParty party = mealPartyRepository.findById(partyId).orElseThrow();
        partyMemberRepository.save(PartyMember.create(party, user02));
        em.flush();
        em.clear();
        return partyId;
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
