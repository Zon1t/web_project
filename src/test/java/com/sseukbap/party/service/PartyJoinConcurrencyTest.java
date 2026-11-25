package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
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
 * 동시 참여: 여러 명이 동시에 마지막 자리에 참여해도 정원을 넘지 않는지 확인한다.
 *
 * 주의: 여러 스레드가 각자 트랜잭션을 커밋해야 해서 이 테스트는 롤백되지 않는다.
 * 그래서 만든 팟·참여자·대화를 @AfterEach에서 직접 지운다. (더미데이터는 건드리지 않음)
 */
@SpringBootTest
class PartyJoinConcurrencyTest {

    @Autowired PartyService partyService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired EntityManager em;

    private Long partyId;

    @Test
    void 남은_자리가_2개일_때_4명이_동시에_참여하면_2명만_성공한다() throws Exception {
        // 정원 3 = 방장(user01) + 빈자리 2
        int[] result = joinAtOnce(3, List.of("user02", "user03", "user04", "user05"));

        assertThat(result[0]).isEqualTo(2);
        assertThat(result[1]).isEqualTo(2);
        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(3);
        assertThat(mealPartyRepository.findById(partyId).orElseThrow().getStatus()).isEqualTo(PartyStatus.FULL);
    }

    @Test
    void 마지막_1자리에_2명이_동시에_참여하면_1명만_성공한다() throws Exception {
        // 정원 2 = 방장(user01) + 빈자리 1 (2026-11-25 T6 P2)
        int[] result = joinAtOnce(2, List.of("user02", "user03"));

        assertThat(result[0]).isEqualTo(1);
        assertThat(result[1]).isEqualTo(1);
        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(2);
        assertThat(mealPartyRepository.findById(partyId).orElseThrow().getStatus()).isEqualTo(PartyStatus.FULL);
    }

    /**
     * user01이 정원 capacity인 팟을 만들고, joinerLoginIds가 한꺼번에 참여한다.
     * @return {성공 수, PARTY_FULL 수}
     */
    private int[] joinAtOnce(int capacity, List<String> joinerLoginIds) throws Exception {
        Member host = memberRepository.findByLoginId("user01").orElseThrow();
        // 트랜잭션 없이 커밋되는 테스트라 더미 팟을 숨길 수 없다. 더미 팟이 없는 14일 후 날짜로 만든다. ('한 끼에 팟 하나' 규칙)
        LocalDate partyDate = LocalDate.now().plusDays(PartyTimePolicy.MAX_DAYS_AHEAD);
        partyId = partyService.createParty(
                new PartyCreateRequest(restaurantRepository.findAll().get(0).getRestaurantId(),
                        partyDate, MealType.LUNCH, LocalTime.of(12, 0), capacity, null),
                host.getMemberId(), LocalDate.now().atTime(9, 0)).partyId();

        List<Long> joinerIds = joinerLoginIds.stream()
                .map(id -> memberRepository.findByLoginId(id).orElseThrow().getMemberId())
                .toList();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);   // 모든 스레드가 준비되면 한꺼번에 출발
        ExecutorService executor = Executors.newFixedThreadPool(joinerIds.size());
        List<Future<?>> futures = new ArrayList<>();

        for (Long memberId : joinerIds) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    partyMemberService.joinParty(partyId, memberId, PartyTestSupport.MORNING);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.PARTY_FULL) {
                        full.incrementAndGet();
                    } else {
                        throw e;
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);   // 스레드 안에서 예상 밖 예외가 나면 여기서 테스트 실패
        }
        executor.shutdown();
        return new int[] {success.get(), full.get()};
    }

    /** 커밋된 테스트 데이터 정리: 대화 → 참여자 → 팟 순서 (FK 때문) */
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
