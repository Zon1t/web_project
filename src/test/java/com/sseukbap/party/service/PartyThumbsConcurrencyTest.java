package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 따봉 동시 요청: 같은 사람이 동시에 여러 번 눌러도 따봉은 한 번만 반영되는지 확인한다.
 *
 * 주의: 여러 트랜잭션이 커밋돼야 해서 롤백되지 않는다. 만든 팟·참여자·대화를 @AfterEach에서 직접 지운다.
 */
@SpringBootTest
class PartyThumbsConcurrencyTest {

    @Autowired PartyThumbsService partyThumbsService;
    @Autowired PartyService partyService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyHostService partyHostService;
    @Autowired PartyBatchService partyBatchService;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired EntityManager em;

    private Long partyId;

    @Test
    void 같은_사람이_동시에_3번_요청하면_1번만_성공() throws Exception {
        LocalDate today = LocalDate.now();
        // 트랜잭션 없이 커밋되는 테스트라 더미 팟을 숨길 수 없다. 더미 팟이 없는 14일 후 날짜로 만든다. ('한 끼에 팟 하나' 규칙)
        LocalDate partyDate = today.plusDays(PartyTimePolicy.MAX_DAYS_AHEAD);
        Member host = memberRepository.findByLoginId("user01").orElseThrow();
        Member guest = memberRepository.findByLoginId("user02").orElseThrow();

        // 식사 완료 + 손님 확정 상태 만들기 (각 서비스 호출이 바로 커밋된다)
        partyId = partyService.createParty(
                new PartyCreateRequest(restaurantRepository.findAll().get(0).getRestaurantId(),
                        partyDate, MealType.LUNCH, LocalTime.of(12, 0), 4, null),
                host.getMemberId(), today.atTime(9, 0)).partyId();
        partyMemberService.joinParty(partyId, guest.getMemberId(), PartyTestSupport.MORNING);
        partyHostService.updateSchedule(partyId, new PartyScheduleUpdateRequest(LocalTime.of(12, 0), "정문", null),
                host.getMemberId(), today.atTime(9, 0));
        partyMemberService.confirmParty(partyId, guest.getMemberId(), PartyTestSupport.MORNING);
        partyBatchService.finishParty(partyId, partyDate.atTime(23, 59));

        AtomicInteger success = new AtomicInteger();
        AtomicInteger already = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    partyThumbsService.createThumbs(partyId, guest.getMemberId());
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.ALREADY_THUMBED) {
                        already.incrementAndGet();
                    } else {
                        throw e;
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(already.get()).isEqualTo(2);
        assertThat(partyMemberRepository.findByPartyIdAndMemberId(partyId, guest.getMemberId()))
                .hasValueSatisfying(pm -> assertThat(pm.isThumbsUp()).isTrue());
    }

    /** 커밋된 테스트 데이터 정리: 대화 → 참여자 → 팟 순서 (FK 때문). 따봉 집계에서도 함께 빠진다. */
    @AfterEach
    void cleanUp() {
        if (partyId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from PartyChat c where c.party.partyId = :id").setParameter("id", partyId).executeUpdate();
            em.createQuery("delete from PartyMember pm where pm.party.partyId = :id").setParameter("id", partyId).executeUpdate();
            em.createQuery("delete from MealParty p where p.partyId = :id").setParameter("id", partyId).executeUpdate();
        });
    }
}
