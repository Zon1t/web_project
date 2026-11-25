package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyActionPendingResponse;
import com.sseukbap.party.dto.PartyActionPendingResponse.PartyAction;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
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

/**
 * 할 일 알림 (GET /api/parties/my/action-pending) 확인 (실제 DB, 끝나면 롤백)
 * user01(방장)이 오늘 12:00 점심 팟을 만들고 user02가 참여한다. "지금 = 오늘 09:00"
 */
@SpringBootTest
@Transactional
class PartyActionPendingTest {

    @Autowired PartyService partyService;
    @Autowired PartyHostService partyHostService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private static final LocalDateTime NINE_AM = LocalDate.now().atTime(9, 0);

    private Member host;     // user01
    private Member user02;
    private Long partyId;

    @BeforeEach
    void setUp() {
        host = memberRepository.findByLoginId("user01").orElseThrow();
        user02 = memberRepository.findByLoginId("user02").orElseThrow();
        PartyTestSupport.hideExistingParties(em);   // 더미데이터 팟 숨김

        PartyCreateRequest request = new PartyCreateRequest(restaurantRepository.findAll().get(0).getRestaurantId(),
                MealType.LUNCH, LocalTime.of(12, 0), 4, null);
        partyId = partyService.createParty(request, host.getMemberId(), NINE_AM).partyId();
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);
    }

    @Test
    void 일정이_없으면_방장에게만_일정_정하기_알림() {
        List<PartyActionPendingResponse> hostItems = pending(host);

        assertThat(hostItems).hasSize(1);
        assertThat(hostItems.get(0).partyId()).isEqualTo(partyId);
        assertThat(hostItems.get(0).action()).isEqualTo(PartyAction.SET_SCHEDULE);
        assertThat(hostItems.get(0).time()).isEqualTo(LocalTime.of(12, 0));
        assertThat(hostItems.get(0).meetPlace()).isNull();
        assertThat(pending(user02)).isEmpty();
    }

    @Test
    void 일정이_정해지면_확정_안_한_참여자에게_참석_확정_알림_확정하면_사라진다() {
        schedule("12:30", "정문");

        assertThat(pending(host)).isEmpty();   // 방장은 자동 확정
        List<PartyActionPendingResponse> items = pending(user02);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).action()).isEqualTo(PartyAction.CONFIRM);
        assertThat(items.get(0).time()).isEqualTo(LocalTime.of(12, 30));
        assertThat(items.get(0).meetPlace()).isEqualTo("정문");

        partyMemberService.confirmParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);
        assertThat(pending(user02)).isEmpty();
    }

    @Test
    void 기준_시간이_지났거나_삭제된_팟은_알림에서_빠진다() {
        assertThat(partyService.getActionPendingList(host.getMemberId(), LocalDate.now().atTime(12, 1))).isEmpty();

        partyService.deleteParty(partyId, host.getMemberId());
        assertThat(pending(host)).isEmpty();
    }

    private List<PartyActionPendingResponse> pending(Member member) {
        em.flush();
        em.clear();
        return partyService.getActionPendingList(member.getMemberId(), NINE_AM);
    }

    private void schedule(String time, String place) {
        partyHostService.updateSchedule(partyId,
                new PartyScheduleUpdateRequest(LocalTime.parse(time), place, null), host.getMemberId(), NINE_AM);
    }
}
