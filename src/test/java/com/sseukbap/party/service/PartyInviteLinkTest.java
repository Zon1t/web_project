package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyInviteLinkResponse;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.dto.PartyStatusUpdateRequest;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 초대 링크 확인 (실제 DB, 끝나면 롤백)
 * 테스트마다 user01이 오늘 팟을 새로 만든다. "지금 = 오늘 09:00"
 */
@SpringBootTest
@Transactional
class PartyInviteLinkTest {

    @Autowired PartyService partyService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyHostService partyHostService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private static final LocalDateTime NINE_AM = LocalDate.now().atTime(9, 0);

    private Member host;     // user01
    private Member user02;
    private Member user03;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        host = memberRepository.findByLoginId("user01").orElseThrow();
        user02 = memberRepository.findByLoginId("user02").orElseThrow();
        user03 = memberRepository.findByLoginId("user03").orElseThrow();
        PartyTestSupport.hideExistingParties(em);   // 더미데이터 팟 숨김 ('한 끼에 팟 하나' 규칙)
        restaurant = restaurantRepository.findAll().get(0);
    }

    @Test
    void 방장이_요청하면_방장_닉네임으로_문구와_상세_경로가_나온다() {
        Long partyId = createParty(MealType.LUNCH, LocalTime.of(12, 0), 4);

        PartyInviteLinkResponse response = partyService.getInviteLink(partyId, host.getMemberId());

        assertThat(response.text())
                .isEqualTo(host.getNickname() + "님이 " + restaurant.getName() + " 점심 식사에 초대하셨습니다~");
        assertThat(response.path()).isEqualTo("/party-detail.html?partyId=" + partyId);
    }

    @Test
    void 참여자도_초대할_수_있고_요청한_사람_닉네임이_들어간다_저녁이면_저녁() {
        Long partyId = createParty(MealType.DINNER, LocalTime.of(18, 0), 4);
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);

        assertThat(partyService.getInviteLink(partyId, user02.getMemberId()).text())
                .isEqualTo(user02.getNickname() + "님이 " + restaurant.getName() + " 저녁 식사에 초대하셨습니다~");
    }

    @Test
    void 일정이_확정된_팟도_초대할_수_있다() {
        Long partyId = createParty(MealType.LUNCH, LocalTime.of(12, 0), 4);
        partyHostService.updateSchedule(partyId, new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "정문", null),
                host.getMemberId(), NINE_AM);

        assertThat(partyService.getInviteLink(partyId, host.getMemberId()).path()).endsWith("partyId=" + partyId);
    }

    @Test
    void 정원_마감_방장_마감_식사_완료면_INVALID_STATUS() {
        Long full = createParty(MealType.LUNCH, LocalTime.of(12, 0), 2);
        partyMemberService.joinParty(full, user02.getMemberId(), PartyTestSupport.MORNING);   // FULL
        PartyTestSupport.moveToTomorrow(em, full);   // 같은 끼니에 팟 하나 규칙 때문에 비켜 둠
        Long closed = createParty(MealType.LUNCH, LocalTime.of(12, 0), 4);
        partyHostService.updateStatus(closed, new PartyStatusUpdateRequest("CLOSE"), host.getMemberId());
        PartyTestSupport.moveToTomorrow(em, closed);
        Long finished = createParty(MealType.LUNCH, LocalTime.of(12, 0), 4);
        setStatus(finished, PartyStatus.FINISHED);

        for (Long partyId : new Long[]{full, closed, finished}) {
            assertThatThrownBy(() -> partyService.getInviteLink(partyId, host.getMemberId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("마감되었거나 완료된 식사팟은 초대할 수 없습니다.")
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STATUS);
        }
    }

    @Test
    void 참여자가_아니면_403_NOT_JOINED() {
        Long partyId = createParty(MealType.LUNCH, LocalTime.of(12, 0), 4);

        assertThatThrownBy(() -> partyService.getInviteLink(partyId, user03.getMemberId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("참여 중인 식사팟만 초대할 수 있습니다.")
                .satisfies(e -> {
                    ErrorCode code = ((BusinessException) e).getErrorCode();
                    assertThat(code.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(code.getCode()).isEqualTo("NOT_JOINED");
                });
    }

    @Test
    void 삭제되거나_없는_팟() {
        Long partyId = createParty(MealType.LUNCH, LocalTime.of(12, 0), 4);
        partyService.deleteParty(partyId, host.getMemberId());

        assertErrorCode(() -> partyService.getInviteLink(partyId, host.getMemberId()), ErrorCode.PARTY_DELETED);
        assertErrorCode(() -> partyService.getInviteLink(-1L, host.getMemberId()), ErrorCode.PARTY_NOT_FOUND);
    }

    // ===== 도우미 =====

    private Long createParty(MealType mealType, LocalTime mealTime, int capacity) {
        return partyService.createParty(
                new PartyCreateRequest(restaurant.getRestaurantId(), mealType, mealTime, capacity, null),
                host.getMemberId(), NINE_AM).partyId();
    }

    private void setStatus(Long partyId, PartyStatus status) {
        em.flush();
        em.createQuery("update MealParty p set p.status = :status where p.partyId = :id")
                .setParameter("status", status)
                .setParameter("id", partyId)
                .executeUpdate();
        em.clear();
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
