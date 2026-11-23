package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.dto.PartyThumbsPendingResponse;
import com.sseukbap.party.dto.PartyThumbsResponse;
import com.sseukbap.party.repository.PartyMemberRepository;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 따봉 요청 대상·따봉 등록 확인 (실제 DB, 끝나면 롤백)
 *
 * 더미 회원은 스케줄러가 오늘 팟을 FINISHED로 바꾸면 요청 대상이 달라지므로,
 * 테스트 안에서 새 회원(방장·손님)을 만들어 쓴다.
 * 식사 완료 팟은 "만들기 → 일정 확정 → 참여 확정 → 스케줄러 처리(PartyBatchService)" 순서로 만든다.
 */
@SpringBootTest
@Transactional
class PartyThumbsServiceTest {

    @Autowired PartyThumbsService partyThumbsService;
    @Autowired PartyService partyService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyHostService partyHostService;
    @Autowired PartyBatchService partyBatchService;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;
    @Autowired JsonMapper jsonMapper;

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDateTime NINE_AM = TODAY.atTime(9, 0);

    private Member host;
    private Member guest;
    private Member stranger;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        host = memberRepository.save(Member.create("thumbhost1", "x".repeat(60), "따봉방장"));
        guest = memberRepository.save(Member.create("thumbguest1", "x".repeat(60), "따봉손님"));
        stranger = memberRepository.save(Member.create("thumbnone1", "x".repeat(60), "따봉구경"));
        restaurant = restaurantRepository.findAll().get(0);
    }

    // ===== 따봉 요청 대상 =====

    @Test
    void 대상이_없으면_null() {
        assertThat(partyThumbsService.getThumbsPending(guest.getMemberId())).isNull();
    }

    @Test
    void 가장_최근_식사_1건이_나오고_따봉하면_다음_건이_나온다() {
        Long yesterday = finishedParty(LocalTime.of(12, 0), 1, true);
        Long today = finishedParty(LocalTime.of(12, 0), 0, true);

        PartyThumbsPendingResponse first = partyThumbsService.getThumbsPending(guest.getMemberId());
        assertThat(first.partyId()).isEqualTo(today);
        assertThat(first.restaurantId()).isEqualTo(restaurant.getRestaurantId());
        assertThat(first.restaurantName()).isEqualTo(restaurant.getName());
        assertThat(first.partyDate()).isEqualTo(TODAY);
        assertThat(first.mealType()).isEqualTo(MealType.LUNCH);

        partyThumbsService.createThumbs(today, guest.getMemberId());
        assertThat(partyThumbsService.getThumbsPending(guest.getMemberId()).partyId()).isEqualTo(yesterday);

        partyThumbsService.createThumbs(yesterday, guest.getMemberId());
        assertThat(partyThumbsService.getThumbsPending(guest.getMemberId())).isNull();
    }

    @Test
    void 같은_날이면_기준_시간이_늦은_식사가_먼저() {
        finishedParty(LocalTime.of(12, 0), 0, true);
        Long dinner = finishedParty(LocalTime.of(18, 0), 0, true);

        assertThat(partyThumbsService.getThumbsPending(guest.getMemberId()).partyId()).isEqualTo(dinner);
    }

    @Test
    void 식사_후_7일_이내만_대상() {
        Long sixDaysAgo = finishedParty(LocalTime.of(12, 0), 6, true);
        finishedParty(LocalTime.of(12, 0), 7, true);   // 7일 전 → 대상 아님

        assertThat(partyThumbsService.getThumbsPending(guest.getMemberId()).partyId()).isEqualTo(sixDaysAgo);
        partyThumbsService.createThumbs(sixDaysAgo, guest.getMemberId());
        assertThat(partyThumbsService.getThumbsPending(guest.getMemberId())).isNull();
    }

    @Test
    void 참여_확정을_안_했으면_대상이_아니다() {
        finishedParty(LocalTime.of(12, 0), 0, false);

        assertThat(partyThumbsService.getThumbsPending(guest.getMemberId())).isNull();
    }

    @Test
    void 대상이_없을_때_JSON_data는_null() throws Exception {
        assertThat(jsonMapper.writeValueAsString(
                ApiResponse.success(partyThumbsService.getThumbsPending(guest.getMemberId()))))
                .contains("\"data\":null");
    }

    // ===== 따봉 등록 =====

    @Test
    void 따봉하면_Y가_되고_식당의_30일_따봉_수가_하나_는다() {
        Long partyId = finishedParty(LocalTime.of(12, 0), 0, true);
        long before = thumbsCount30d();

        PartyThumbsResponse response = partyThumbsService.createThumbs(partyId, guest.getMemberId());

        assertThat(response.restaurantId()).isEqualTo(restaurant.getRestaurantId());
        assertThat(response.thumbsCount30d()).isEqualTo(before + 1);
        em.clear();
        assertThat(partyMemberRepository.findByPartyIdAndMemberId(partyId, guest.getMemberId()))
                .hasValueSatisfying(pm -> assertThat(pm.isThumbsUp()).isTrue());
    }

    @Test
    void 이미_남겼으면_ALREADY_THUMBED() {
        Long partyId = finishedParty(LocalTime.of(12, 0), 0, true);
        partyThumbsService.createThumbs(partyId, guest.getMemberId());

        assertErrorCode(() -> partyThumbsService.createThumbs(partyId, guest.getMemberId()), ErrorCode.ALREADY_THUMBED);
    }

    @Test
    void 식사가_안_끝났으면_PARTY_NOT_FINISHED() {
        Long partyId = createParty(LocalTime.of(12, 0));

        assertErrorCode(() -> partyThumbsService.createThumbs(partyId, host.getMemberId()), ErrorCode.PARTY_NOT_FINISHED);
    }

    @Test
    void 참여자가_아니거나_미확정이면_403_NOT_CONFIRMED_MEMBER() {
        Long partyId = finishedParty(LocalTime.of(12, 0), 0, false);   // 손님 미확정 (방장은 확정)

        for (Member member : new Member[]{guest, stranger}) {
            assertThatThrownBy(() -> partyThumbsService.createThumbs(partyId, member.getMemberId()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        ErrorCode code = ((BusinessException) e).getErrorCode();
                        assertThat(code).isEqualTo(ErrorCode.NOT_CONFIRMED_MEMBER);
                        assertThat(code.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    });
        }
        // 확정한 방장은 가능
        assertThat(partyThumbsService.createThumbs(partyId, host.getMemberId()).thumbsCount30d()).isPositive();
    }

    @Test
    void 삭제되거나_없는_팟() {
        Long partyId = createParty(LocalTime.of(12, 0));
        partyService.deleteParty(partyId, host.getMemberId());

        assertErrorCode(() -> partyThumbsService.createThumbs(partyId, host.getMemberId()), ErrorCode.PARTY_DELETED);
        assertErrorCode(() -> partyThumbsService.createThumbs(-1L, host.getMemberId()), ErrorCode.PARTY_NOT_FOUND);
    }

    // ===== 도우미 =====

    private Long createParty(LocalTime mealTime) {
        MealType mealType = mealTime.getHour() < 15 ? MealType.LUNCH : MealType.DINNER;
        return partyService.createParty(
                new PartyCreateRequest(restaurant.getRestaurantId(), mealType, mealTime, 4, null),
                host.getMemberId(), NINE_AM).partyId();
    }

    /**
     * 방장·손님이 함께한 식사 완료 팟 (식사 날짜 = 오늘 - daysAgo)
     * 방장은 항상 확정, 손님은 guestConfirmed일 때만 확정
     */
    private Long finishedParty(LocalTime mealTime, int daysAgo, boolean guestConfirmed) {
        Long partyId = createParty(mealTime);
        partyMemberService.joinParty(partyId, guest.getMemberId());
        partyHostService.updateSchedule(partyId, new PartyScheduleUpdateRequest(mealTime, "정문", null),
                host.getMemberId(), NINE_AM);   // 방장은 일정을 정하면 자동 확정
        if (guestConfirmed) {
            partyMemberService.confirmParty(partyId, guest.getMemberId());
        }
        partyBatchService.finishParty(partyId, TODAY.atTime(23, 59));   // 스케줄러와 같은 처리로 FINISHED

        if (daysAgo > 0) {
            em.flush();
            em.createQuery("update MealParty p set p.partyDate = :date where p.partyId = :id")
                    .setParameter("date", TODAY.minusDays(daysAgo))
                    .setParameter("id", partyId)
                    .executeUpdate();
        }
        em.flush();
        em.clear();
        return partyId;
    }

    private long thumbsCount30d() {
        return restaurantRepository.countThumbsUp(restaurant.getRestaurantId(), TODAY.minusDays(29), TODAY.plusDays(1));
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
