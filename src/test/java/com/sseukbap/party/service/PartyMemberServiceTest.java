package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyConfirmResponse;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyJoinResponse;
import com.sseukbap.party.dto.PartyLeaveResponse;
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
 * 참여하기·나가기·참여 확정·확정 취소 확인 (실제 DB, 끝나면 롤백)
 * 테스트마다 user01이 오늘 12:00 점심 팟을 새로 만들어 쓰므로 더미 팟 상태와 관계없이 동작한다.
 * 동시 참여는 PartyJoinConcurrencyTest에서 따로 확인한다.
 */
@SpringBootTest
@Transactional
class PartyMemberServiceTest {

    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyService partyService;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired PartyChatRepository partyChatRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private static final LocalTime NOON = LocalTime.of(12, 0);
    private static final LocalDateTime BEFORE_DEADLINE = LocalDate.now().atTime(11, 30);   // 12:00 - 30분 (가능)
    private static final LocalDateTime AFTER_DEADLINE = LocalDate.now().atTime(11, 31);    // 마감 지남

    private Member host;     // user01
    private Member user02;
    private Member user03;

    @BeforeEach
    void setUp() {
        host = memberRepository.findByLoginId("user01").orElseThrow();
        user02 = memberRepository.findByLoginId("user02").orElseThrow();
        user03 = memberRepository.findByLoginId("user03").orElseThrow();
    }

    // ===== 참여하기 =====

    @Test
    void 참여하면_인원이_늘고_시스템_메시지가_남는다() {
        Long partyId = createParty(4);

        PartyJoinResponse response = partyMemberService.joinParty(partyId, user02.getMemberId());
        flushAndClear();

        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(partyMemberRepository.findByPartyIdAndMemberId(partyId, user02.getMemberId()))
                .hasValueSatisfying(pm -> assertThat(pm.isConfirmed()).isFalse());
        assertThat(lastChat(partyId)).isEqualTo(user02.getNickname() + "님이 참여했습니다");
    }

    @Test
    void 정원에_도달하면_FULL() {
        Long partyId = createParty(2);

        PartyJoinResponse response = partyMemberService.joinParty(partyId, user02.getMemberId());

        assertThat(response.status()).isEqualTo(PartyStatus.FULL);
        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.FULL);
        assertErrorCode(() -> partyMemberService.joinParty(partyId, user03.getMemberId()), ErrorCode.PARTY_FULL);
    }

    @Test
    void 일정이_확정된_팟도_참여할_수_있다() {
        Long partyId = createParty(4);
        setSchedule(partyId, NOON);
        setStatus(partyId, PartyStatus.SCHEDULED);

        assertThat(partyMemberService.joinParty(partyId, user02.getMemberId()).status())
                .isEqualTo(PartyStatus.SCHEDULED);
    }

    @Test
    void 이미_참여_중이면_ALREADY_JOINED() {
        Long partyId = createParty(4);

        assertErrorCode(() -> partyMemberService.joinParty(partyId, host.getMemberId()), ErrorCode.ALREADY_JOINED);
    }

    @Test
    void 방장이_마감한_팟은_PARTY_FULL() {
        Long partyId = createParty(4);
        setStatus(partyId, PartyStatus.CLOSED);

        assertErrorCode(() -> partyMemberService.joinParty(partyId, user02.getMemberId()), ErrorCode.PARTY_FULL);
    }

    @Test
    void 식사_완료_삭제_없는_팟() {
        Long finished = createParty(4);
        setStatus(finished, PartyStatus.FINISHED);
        Long deleted = createParty(4);
        partyService.deleteParty(deleted, host.getMemberId());

        assertErrorCode(() -> partyMemberService.joinParty(finished, user02.getMemberId()), ErrorCode.PARTY_FINISHED);
        assertErrorCode(() -> partyMemberService.joinParty(deleted, user02.getMemberId()), ErrorCode.PARTY_DELETED);
        assertErrorCode(() -> partyMemberService.joinParty(-1L, user02.getMemberId()), ErrorCode.PARTY_NOT_FOUND);
    }

    // ===== 나가기 =====

    @Test
    void 참여자가_나가면_행이_지워지고_시스템_메시지가_남는다() {
        Long partyId = createParty(4);
        partyMemberService.joinParty(partyId, user02.getMemberId());

        PartyLeaveResponse response = partyMemberService.leaveParty(partyId, user02.getMemberId(), BEFORE_DEADLINE);
        flushAndClear();

        assertThat(response.partyDeleted()).isFalse();
        assertThat(partyMemberRepository.existsByPartyIdAndMemberId(partyId, user02.getMemberId())).isFalse();
        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(1);
        assertThat(lastChat(partyId)).isEqualTo(user02.getNickname() + "님이 나갔습니다");
    }

    @Test
    void 방장이_나가면_팟이_삭제된다() {
        Long partyId = createParty(4);
        partyMemberService.joinParty(partyId, user02.getMemberId());

        PartyLeaveResponse response = partyMemberService.leaveParty(partyId, host.getMemberId(), BEFORE_DEADLINE);

        assertThat(response.partyDeleted()).isTrue();
        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.DELETED);
        assertErrorCode(() -> partyService.getParty(partyId, user02.getMemberId()), ErrorCode.PARTY_DELETED);
    }

    @Test
    void FULL에서_나가면_일정_없으면_RECRUITING() {
        Long partyId = createParty(2);
        partyMemberService.joinParty(partyId, user02.getMemberId());   // FULL

        partyMemberService.leaveParty(partyId, user02.getMemberId(), BEFORE_DEADLINE);

        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.RECRUITING);
    }

    @Test
    void FULL에서_나가면_일정_있으면_SCHEDULED() {
        Long partyId = createParty(2);
        setSchedule(partyId, NOON);
        partyMemberService.joinParty(partyId, user02.getMemberId());   // FULL

        partyMemberService.leaveParty(partyId, user02.getMemberId(), BEFORE_DEADLINE);

        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.SCHEDULED);
    }

    @Test
    void CLOSED는_나가도_CLOSED_유지() {
        Long partyId = createParty(4);
        partyMemberService.joinParty(partyId, user02.getMemberId());
        setStatus(partyId, PartyStatus.CLOSED);

        partyMemberService.leaveParty(partyId, user02.getMemberId(), BEFORE_DEADLINE);

        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.CLOSED);
    }

    @Test
    void 확정자는_마감_후_나갈_수_없고_미확정자는_나갈_수_있다() {
        Long partyId = createParty(4);
        setSchedule(partyId, NOON);
        partyMemberService.joinParty(partyId, user02.getMemberId());
        partyMemberService.joinParty(partyId, user03.getMemberId());
        partyMemberService.confirmParty(partyId, user02.getMemberId());

        assertErrorCode(() -> partyMemberService.leaveParty(partyId, user02.getMemberId(), AFTER_DEADLINE),
                ErrorCode.LEAVE_DEADLINE_PASSED);
        assertThat(partyMemberService.leaveParty(partyId, user03.getMemberId(), AFTER_DEADLINE).partyDeleted())
                .isFalse();
    }

    @Test
    void 확정자도_마감_전이면_나갈_수_있다() {
        Long partyId = createParty(4);
        setSchedule(partyId, NOON);
        partyMemberService.joinParty(partyId, user02.getMemberId());
        partyMemberService.confirmParty(partyId, user02.getMemberId());

        partyMemberService.leaveParty(partyId, user02.getMemberId(), BEFORE_DEADLINE);

        assertThat(partyMemberRepository.existsByPartyIdAndMemberId(partyId, user02.getMemberId())).isFalse();
    }

    @Test
    void 참여자가_아니면_나가기_NOT_JOINED_식사완료면_PARTY_FINISHED() {
        Long partyId = createParty(4);
        assertErrorCode(() -> partyMemberService.leaveParty(partyId, user02.getMemberId(), BEFORE_DEADLINE),
                ErrorCode.NOT_JOINED);

        setStatus(partyId, PartyStatus.FINISHED);
        assertErrorCode(() -> partyMemberService.leaveParty(partyId, host.getMemberId(), BEFORE_DEADLINE),
                ErrorCode.PARTY_FINISHED);
    }

    // ===== 참여 확정 =====

    @Test
    void 일정이_없으면_SCHEDULE_NOT_SET() {
        Long partyId = createParty(4);
        partyMemberService.joinParty(partyId, user02.getMemberId());

        assertErrorCode(() -> partyMemberService.confirmParty(partyId, user02.getMemberId()), ErrorCode.SCHEDULE_NOT_SET);
    }

    @Test
    void 확정하면_확정_인원이_늘고_방장도_확정할_수_있다() {
        Long partyId = createParty(4);
        setSchedule(partyId, NOON);
        partyMemberService.joinParty(partyId, user02.getMemberId());

        PartyConfirmResponse first = partyMemberService.confirmParty(partyId, user02.getMemberId());
        PartyConfirmResponse second = partyMemberService.confirmParty(partyId, host.getMemberId());
        flushAndClear();

        assertThat(first.confirmed()).isTrue();
        assertThat(first.confirmedCount()).isEqualTo(1);
        assertThat(second.confirmedCount()).isEqualTo(2);
        assertThat(partyMemberRepository.countConfirmedByPartyId(partyId)).isEqualTo(2);
        assertThat(lastChat(partyId)).isEqualTo(host.getNickname() + "님이 참여를 확정했습니다");
    }

    @Test
    void 중복_확정은_ALREADY_CONFIRMED_참여자가_아니면_NOT_JOINED() {
        Long partyId = createParty(4);
        setSchedule(partyId, NOON);
        partyMemberService.confirmParty(partyId, host.getMemberId());

        assertErrorCode(() -> partyMemberService.confirmParty(partyId, host.getMemberId()), ErrorCode.ALREADY_CONFIRMED);
        assertErrorCode(() -> partyMemberService.confirmParty(partyId, user02.getMemberId()), ErrorCode.NOT_JOINED);
    }

    // ===== 확정 취소 =====

    @Test
    void 확정_취소하면_확정_인원이_줄고_참여는_유지된다() {
        Long partyId = createParty(4);
        setSchedule(partyId, NOON);
        partyMemberService.joinParty(partyId, user02.getMemberId());
        partyMemberService.confirmParty(partyId, user02.getMemberId());

        PartyConfirmResponse response =
                partyMemberService.cancelConfirmParty(partyId, user02.getMemberId(), BEFORE_DEADLINE);
        flushAndClear();

        assertThat(response.confirmed()).isFalse();
        assertThat(response.confirmedCount()).isZero();
        assertThat(partyMemberRepository.existsByPartyIdAndMemberId(partyId, user02.getMemberId())).isTrue();
        assertThat(lastChat(partyId)).isEqualTo(user02.getNickname() + "님이 참여 확정을 취소했습니다");
    }

    @Test
    void 확정하지_않았으면_NOT_CONFIRMED_마감_후면_CANCEL_DEADLINE_PASSED() {
        Long partyId = createParty(4);
        setSchedule(partyId, NOON);
        partyMemberService.joinParty(partyId, user02.getMemberId());

        assertErrorCode(() -> partyMemberService.cancelConfirmParty(partyId, user02.getMemberId(), BEFORE_DEADLINE),
                ErrorCode.NOT_CONFIRMED);

        partyMemberService.confirmParty(partyId, user02.getMemberId());
        assertErrorCode(() -> partyMemberService.cancelConfirmParty(partyId, user02.getMemberId(), AFTER_DEADLINE),
                ErrorCode.CANCEL_DEADLINE_PASSED);
    }

    // ===== 도우미 =====

    /** user01이 오늘 12:00 점심 팟을 만든다 (방장 = 첫 참여자) */
    private Long createParty(int capacity) {
        PartyCreateRequest request = new PartyCreateRequest(
                restaurantRepository.findAll().get(0).getRestaurantId(), MealType.LUNCH, NOON, capacity, null);
        return partyService.createParty(request, host.getMemberId(), LocalDate.now().atTime(9, 0)).partyId();
    }

    /** 일정 확정 API가 아직 없어서 확정 시간만 직접 넣는다 */
    private void setSchedule(Long partyId, LocalTime confirmedTime) {
        em.flush();
        em.createQuery("update MealParty p set p.confirmedTime = :time where p.partyId = :id")
                .setParameter("time", confirmedTime)
                .setParameter("id", partyId)
                .executeUpdate();
        em.clear();
    }

    private void setStatus(Long partyId, PartyStatus status) {
        em.flush();
        em.createQuery("update MealParty p set p.status = :status where p.partyId = :id")
                .setParameter("status", status)
                .setParameter("id", partyId)
                .executeUpdate();
        em.clear();
    }

    private PartyStatus statusOf(Long partyId) {
        flushAndClear();
        MealParty party = mealPartyRepository.findById(partyId).orElseThrow();
        return party.getStatus();
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
