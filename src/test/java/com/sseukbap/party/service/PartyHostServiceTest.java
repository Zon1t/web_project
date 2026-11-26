package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.dto.PartyScheduleUpdateResponse;
import com.sseukbap.party.dto.PartyStatusUpdateRequest;
import com.sseukbap.party.dto.PartyStatusUpdateResponse;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 방장 기능: 일정 확정·수정, 모집 마감·재개 확인 (실제 DB, 끝나면 롤백)
 * 테스트마다 user01이 오늘 12:00 점심 팟을 새로 만들고, "지금 = 오늘 09:00"으로 시각을 고정한다.
 */
@SpringBootTest
@Transactional
class PartyHostServiceTest {

    @Autowired PartyHostService partyHostService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyService partyService;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired PartyChatRepository partyChatRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private static final LocalDateTime NINE_AM = LocalDate.now().atTime(9, 0);
    private static final PartyStatusUpdateRequest CLOSE = new PartyStatusUpdateRequest("CLOSE");
    private static final PartyStatusUpdateRequest REOPEN = new PartyStatusUpdateRequest("REOPEN");

    private Member host;     // user01
    private Member user02;

    @BeforeEach
    void setUp() {
        host = memberRepository.findByLoginId("user01").orElseThrow();
        user02 = memberRepository.findByLoginId("user02").orElseThrow();
        PartyTestSupport.hideExistingParties(em);   // 더미데이터 팟 숨김 ('한 끼에 팟 하나' 규칙)
    }

    // ===== 일정 확정 (최초) =====

    @Test
    void 처음_확정하면_SCHEDULED가_되고_일정이_저장된다() {
        Long partyId = createParty(4);

        PartyScheduleUpdateResponse response = updateSchedule(partyId, "12:00", "1층 로비", "늦으면 채팅 남겨 주세요");
        flushAndClear();

        assertThat(response.status()).isEqualTo(PartyStatus.SCHEDULED);
        assertThat(response.confirmationsReset()).isFalse();
        MealParty party = mealPartyRepository.findById(partyId).orElseThrow();
        assertThat(party.getConfirmedTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(party.getMeetPlace()).isEqualTo("1층 로비");
        assertThat(party.getConfirmMemo()).isEqualTo("늦으면 채팅 남겨 주세요");
        assertThat(lastChat(partyId)).isEqualTo("방장이 일정을 확정했어요 · 12:00 · 1층 로비");
    }

    @Test
    void 일정을_정하면_방장은_자동으로_참석_확정된다() {
        Long partyId = createParty(4);
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);

        updateSchedule(partyId, "12:00", "정문", null);
        flushAndClear();

        assertThat(partyService.getParty(partyId, host.getMemberId()).myConfirmed()).isTrue();
        assertThat(partyService.getParty(partyId, user02.getMemberId()).myConfirmed()).isFalse();
        assertThat(partyMemberRepository.countConfirmedByPartyId(partyId)).isEqualTo(1);
    }

    @Test
    void 확정_후에는_참여_확정을_할_수_있다() {
        Long partyId = createParty(4);
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);
        updateSchedule(partyId, "12:00", "정문", null);

        // 방장(자동 확정) + user02
        assertThat(partyMemberService.confirmParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING).confirmedCount()).isEqualTo(2);
    }

    @Test
    void FULL이나_CLOSED는_일정만_저장하고_상태_유지() {
        Long full = createParty(2);
        partyMemberService.joinParty(full, user02.getMemberId(), PartyTestSupport.MORNING);   // FULL
        PartyTestSupport.moveToTomorrow(em, full);   // 같은 끼니에 팟 하나 규칙 때문에 비켜 둠
        Long closed = createParty(4);
        partyHostService.updateStatus(closed, CLOSE, host.getMemberId());

        assertThat(updateSchedule(full, "12:00", "정문", null).status()).isEqualTo(PartyStatus.FULL);
        assertThat(updateSchedule(closed, "12:00", "정문", null).status()).isEqualTo(PartyStatus.CLOSED);
    }

    // ===== 일정 수정 =====

    @Test
    void 수정하면_참여자_확정은_초기화되고_방장만_다시_자동_확정된다() {
        Long partyId = createParty(4);
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);
        updateSchedule(partyId, "12:00", "1층 로비", null);   // 방장 자동 확정
        partyMemberService.confirmParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);
        flushAndClear();
        assertThat(partyMemberRepository.countConfirmedByPartyId(partyId)).isEqualTo(2);

        PartyScheduleUpdateResponse response = updateSchedule(partyId, "12:30", "정문", null);
        flushAndClear();

        assertThat(response.confirmationsReset()).isTrue();
        assertThat(response.status()).isEqualTo(PartyStatus.SCHEDULED);
        assertThat(partyMemberRepository.countConfirmedByPartyId(partyId)).isEqualTo(1);
        assertThat(partyService.getParty(partyId, host.getMemberId()).myConfirmed()).isTrue();
        assertThat(partyService.getParty(partyId, user02.getMemberId()).myConfirmed()).isFalse();
        assertThat(mealPartyRepository.findById(partyId).orElseThrow().getConfirmedTime()).isEqualTo(LocalTime.of(12, 30));
        assertThat(lastChat(partyId)).isEqualTo("방장이 일정을 바꿨어요 · 12:30 · 정문. 참석 확정을 다시 해 주세요");
    }

    // ===== 일정 오류 =====

    @Test
    void 끼니_시간대_30분_단위_지난_시간이면_INVALID_MEAL_TIME() {
        Long partyId = createParty(4);

        assertErrorCode(() -> updateSchedule(partyId, "18:00", "정문", null), ErrorCode.INVALID_MEAL_TIME);   // 점심 팟에 저녁 시간
        assertErrorCode(() -> updateSchedule(partyId, "12:15", "정문", null), ErrorCode.INVALID_MEAL_TIME);
        assertErrorCode(() -> partyHostService.updateSchedule(partyId, scheduleRequest("12:00", "정문", null),
                host.getMemberId(), LocalDate.now().atTime(12, 30)), ErrorCode.INVALID_MEAL_TIME);   // 지난 시간
    }

    @Test
    void 방장이_아니면_일정_NOT_HOST() {
        Long partyId = createParty(4);
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);

        assertThatThrownBy(() -> partyHostService.updateSchedule(partyId, scheduleRequest("12:00", "정문", null),
                user02.getMemberId(), NINE_AM))
                .isInstanceOf(BusinessException.class)
                .hasMessage("방장만 일정을 정할 수 있습니다.")
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_HOST);
    }

    @Test
    void 식사_완료_삭제된_팟은_일정을_정할_수_없다() {
        Long finished = createParty(4);
        setStatus(finished, PartyStatus.FINISHED);
        PartyTestSupport.moveToTomorrow(em, finished);   // 같은 끼니에 팟 하나 규칙 때문에 비켜 둠
        Long deleted = createParty(4);
        partyService.deleteParty(deleted, host.getMemberId());

        assertErrorCode(() -> updateSchedule(finished, "12:00", "정문", null), ErrorCode.PARTY_FINISHED);
        assertErrorCode(() -> updateSchedule(deleted, "12:00", "정문", null), ErrorCode.PARTY_DELETED);
    }

    // ===== 모집 마감·재개 =====

    @Test
    void 마감하면_CLOSED_재개하면_RECRUITING() {
        Long partyId = createParty(4);

        PartyStatusUpdateResponse closed = partyHostService.updateStatus(partyId, CLOSE, host.getMemberId());
        assertThat(closed.status()).isEqualTo(PartyStatus.CLOSED);
        assertThat(lastChat(partyId)).isEqualTo("방장이 모집을 마감했어요");

        PartyStatusUpdateResponse reopened = partyHostService.updateStatus(partyId, REOPEN, host.getMemberId());
        assertThat(reopened.status()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(lastChat(partyId)).isEqualTo("방장이 모집을 다시 열었어요");
    }

    @Test
    void 일정이_있으면_재개_후_SCHEDULED() {
        Long partyId = createParty(4);
        updateSchedule(partyId, "12:00", "정문", null);
        partyHostService.updateStatus(partyId, CLOSE, host.getMemberId());

        assertThat(partyHostService.updateStatus(partyId, REOPEN, host.getMemberId()).status())
                .isEqualTo(PartyStatus.SCHEDULED);
    }

    @Test
    void 정원이_차_있으면_재개_후_FULL() {
        Long partyId = createParty(2);
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);   // FULL
        assertThat(partyHostService.updateStatus(partyId, CLOSE, host.getMemberId()).status())
                .isEqualTo(PartyStatus.CLOSED);

        assertThat(partyHostService.updateStatus(partyId, REOPEN, host.getMemberId()).status())
                .isEqualTo(PartyStatus.FULL);
    }

    @Test
    void 이미_마감한_팟을_마감하거나_마감_안_한_팟을_재개하면_INVALID_STATUS() {
        Long partyId = createParty(4);
        assertErrorCode(() -> partyHostService.updateStatus(partyId, REOPEN, host.getMemberId()), ErrorCode.INVALID_STATUS);

        partyHostService.updateStatus(partyId, CLOSE, host.getMemberId());
        assertErrorCode(() -> partyHostService.updateStatus(partyId, CLOSE, host.getMemberId()), ErrorCode.INVALID_STATUS);
    }

    @Test
    void 방장이_아니면_마감_NOT_HOST_식사_완료면_PARTY_FINISHED() {
        Long partyId = createParty(4);
        assertThatThrownBy(() -> partyHostService.updateStatus(partyId, CLOSE, user02.getMemberId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("방장만 변경할 수 있습니다.")
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_HOST);

        setStatus(partyId, PartyStatus.FINISHED);
        assertErrorCode(() -> partyHostService.updateStatus(partyId, CLOSE, host.getMemberId()), ErrorCode.PARTY_FINISHED);
    }

    // ===== 도우미 =====

    /** user01이 오늘 12:00 점심 팟을 만든다 */
    private Long createParty(int capacity) {
        PartyCreateRequest request = new PartyCreateRequest(restaurantRepository.findAll().get(0).getRestaurantId(),
                MealType.LUNCH, LocalTime.of(12, 0), capacity, null);
        return partyService.createParty(request, host.getMemberId(), NINE_AM).partyId();
    }

    /** 방장(user01)이 "지금 = 09:00"에 일정을 정한다 */
    private PartyScheduleUpdateResponse updateSchedule(Long partyId, String time, String place, String memo) {
        return partyHostService.updateSchedule(partyId, scheduleRequest(time, place, memo), host.getMemberId(), NINE_AM);
    }

    private static PartyScheduleUpdateRequest scheduleRequest(String time, String place, String memo) {
        return new PartyScheduleUpdateRequest(LocalTime.parse(time), place, memo);
    }

    private void setStatus(Long partyId, PartyStatus status) {
        em.flush();
        em.createQuery("update MealParty p set p.status = :status where p.partyId = :id")
                .setParameter("status", status)
                .setParameter("id", partyId)
                .executeUpdate();
        em.clear();
    }

    private String lastChat(Long partyId) {
        List<PartyChat> chats = partyChatRepository.findAllAfter(partyId, 0L);
        return chats.get(chats.size() - 1).getContent();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
