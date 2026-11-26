package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.party.service.PartyService.PartyWithdrawalResult;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
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
 * 회원 탈퇴 시 식사팟 정리 (PartyService.cleanUpForWithdrawal) 확인. 실제 DB, 끝나면 롤백.
 * 더미·시연 데이터에 기대지 않도록 회원·식당을 테스트 안에서 만든다.
 */
@SpringBootTest
@Transactional
class PartyWithdrawalTest {

    @Autowired PartyService partyService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyHostService partyHostService;
    @Autowired PartyBatchService partyBatchService;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDateTime NINE_AM = TODAY.atTime(9, 0);

    private Member host;
    private Member guest;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        host = memberRepository.save(Member.create("wdhost1", "x".repeat(60), "탈퇴방장"));
        guest = memberRepository.save(Member.create("wdguest1", "x".repeat(60), "탈퇴손님"));
        restaurant = restaurantRepository.save(Restaurant.create("탈퇴테스트식당", RestaurantCategory.KOREAN,
                "https://map.naver.com/p/entry/place/wd-test-1", null, null, null, host));
    }

    @Test
    void 방장인_팟은_DELETED가_되고_시스템_메시지가_남는다() {
        Long partyId = createParty(host, LocalTime.of(12, 0), 4);
        partyMemberService.joinParty(partyId, guest.getMemberId(), PartyTestSupport.MORNING);

        PartyWithdrawalResult result = partyService.cleanUpForWithdrawal(host.getMemberId());
        em.flush();
        em.clear();

        assertThat(result.deletedPartyCount()).isEqualTo(1);
        assertThat(result.leftPartyCount()).isZero();
        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.DELETED);
        assertThat(chatsOf(partyId)).contains("방장이 탈퇴해 식사팟이 삭제됐어요");
    }

    @Test
    void 참여한_팟은_나가고_FULL이면_일정_유무로_돌아간다_30분_전_제한은_무시() {
        Long partyId = createParty(host, LocalTime.of(12, 0), 2);
        partyMemberService.joinParty(partyId, guest.getMemberId(), PartyTestSupport.MORNING);          // 2/2 → FULL
        partyHostService.updateSchedule(partyId, new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "정문", null),
                host.getMemberId(), NINE_AM);
        partyMemberService.confirmParty(partyId, guest.getMemberId(), PartyTestSupport.MORNING);       // 확정자 (원래는 30분 전까지만 나갈 수 있음)
        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.FULL);

        PartyWithdrawalResult result = partyService.cleanUpForWithdrawal(guest.getMemberId());
        em.flush();
        em.clear();

        assertThat(result.leftPartyCount()).isEqualTo(1);
        assertThat(result.deletedPartyCount()).isZero();
        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.SCHEDULED);    // 일정이 있으니 SCHEDULED
        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(1);
        assertThat(partyMemberRepository.existsByPartyIdAndMemberId(partyId, guest.getMemberId())).isFalse();
        assertThat(chatsOf(partyId)).contains("탈퇴손님님이 나갔어요");
    }

    @Test
    void 일정이_없는_FULL_팟은_RECRUITING으로() {
        Long partyId = createParty(host, LocalTime.of(18, 0), 2);
        partyMemberService.joinParty(partyId, guest.getMemberId(), PartyTestSupport.MORNING);

        partyService.cleanUpForWithdrawal(guest.getMemberId());
        em.flush();
        em.clear();

        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.RECRUITING);
    }

    @Test
    void 끝난_팟과_삭제된_팟은_그대로() {
        // 식사 완료 팟 (손님 확정) → 어제로 옮겨 오늘 점심과 겹치지 않게
        Long finishedId = createParty(host, LocalTime.of(12, 0), 4);
        partyMemberService.joinParty(finishedId, guest.getMemberId(), PartyTestSupport.MORNING);
        partyHostService.updateSchedule(finishedId, new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "정문", null),
                host.getMemberId(), NINE_AM);
        partyMemberService.confirmParty(finishedId, guest.getMemberId(), PartyTestSupport.MORNING);
        partyBatchService.finishParty(finishedId, TODAY.atTime(23, 59));
        em.flush();
        em.createQuery("update MealParty p set p.partyDate = :date where p.partyId = :id")
                .setParameter("date", TODAY.minusDays(1))
                .setParameter("id", finishedId)
                .executeUpdate();
        em.clear();

        // 삭제된 팟
        Long deletedId = createParty(host, LocalTime.of(18, 0), 4);
        partyMemberService.joinParty(deletedId, guest.getMemberId(), PartyTestSupport.MORNING);
        partyService.deleteParty(deletedId, host.getMemberId());
        em.flush();
        em.clear();

        PartyWithdrawalResult guestResult = partyService.cleanUpForWithdrawal(guest.getMemberId());
        PartyWithdrawalResult hostResult = partyService.cleanUpForWithdrawal(host.getMemberId());
        em.flush();
        em.clear();

        assertThat(guestResult).isEqualTo(new PartyWithdrawalResult(0, 0));
        assertThat(hostResult).isEqualTo(new PartyWithdrawalResult(0, 0));
        assertThat(statusOf(finishedId)).isEqualTo(PartyStatus.FINISHED);
        assertThat(statusOf(deletedId)).isEqualTo(PartyStatus.DELETED);
        // 끝난 팟의 참여 이력(따봉 대상)은 남는다
        assertThat(partyMemberRepository.existsByPartyIdAndMemberId(finishedId, guest.getMemberId())).isTrue();
    }

    @Test
    void 팟이_없으면_아무것도_안_한다() {
        assertThat(partyService.cleanUpForWithdrawal(guest.getMemberId()))
                .isEqualTo(new PartyWithdrawalResult(0, 0));
    }

    // ===== 도우미 =====

    private Long createParty(Member owner, LocalTime mealTime, int capacity) {
        MealType mealType = mealTime.getHour() < 15 ? MealType.LUNCH : MealType.DINNER;
        return partyService.createParty(
                new PartyCreateRequest(restaurant.getRestaurantId(), mealType, mealTime, capacity, null),
                owner.getMemberId(), NINE_AM).partyId();
    }

    private PartyStatus statusOf(Long partyId) {
        em.flush();
        em.clear();
        return mealPartyRepository.findById(partyId).map(MealParty::getStatus).orElseThrow();
    }

    private List<String> chatsOf(Long partyId) {
        return em.createQuery("select c.content from PartyChat c where c.party.partyId = :id", String.class)
                .setParameter("id", partyId)
                .getResultList();
    }
}
