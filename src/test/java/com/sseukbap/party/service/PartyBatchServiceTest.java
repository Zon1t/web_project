package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyChatCreateRequest;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스케줄러 일괄 처리 확인 (실제 DB, 끝나면 롤백)
 * 5분·0시를 기다리지 않고 PartyBatchService에 원하는 "지금"을 넘겨서 확인한다.
 * 다른 팟(더미데이터)도 대상에 섞이므로 개수 대신 "내가 만든 팟이 들어있는지"로 확인한다.
 */
@SpringBootTest
@Transactional
class PartyBatchServiceTest {

    @Autowired PartyBatchService partyBatchService;
    @Autowired PartyService partyService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyHostService partyHostService;
    @Autowired PartyChatService partyChatService;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired PartyChatRepository partyChatRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;
    @Autowired ApplicationContext context;

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDateTime NINE_AM = TODAY.atTime(9, 0);

    private Member host;     // user01
    private Member user02;

    @BeforeEach
    void setUp() {
        host = memberRepository.findByLoginId("user01").orElseThrow();
        user02 = memberRepository.findByLoginId("user02").orElseThrow();
        PartyTestSupport.hideExistingParties(em);   // 더미데이터 팟 숨김 ('한 끼에 팟 하나' 규칙)
    }

    @Test
    void 테스트에서는_스케줄러가_꺼져_있다() {
        // src/test/resources/config/application.properties: sseukbap.scheduler.enabled=false
        assertThat(context.getBeanNamesForType(PartyScheduler.class)).isEmpty();
    }

    // ===== 식사 완료 처리 =====

    @Test
    void 기준_시간이_지나면_대상이_되고_FINISHED와_시스템_메시지() {
        Long partyId = createParty(LocalTime.of(12, 0));

        assertThat(partyBatchService.findFinishTargetIds(TODAY.atTime(12, 0))).doesNotContain(partyId);   // 정각은 아직
        assertThat(partyBatchService.findFinishTargetIds(TODAY.atTime(12, 1))).contains(partyId);

        assertThat(partyBatchService.finishParty(partyId, TODAY.atTime(12, 1))).isTrue();
        flushAndClear();

        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.FINISHED);
        assertThat(lastChat(partyId)).isEqualTo("식사가 끝났어요");
    }

    @Test
    void 확정_시간이_있으면_확정_시간_기준() {
        Long partyId = createParty(LocalTime.of(12, 0));
        partyHostService.updateSchedule(partyId, new PartyScheduleUpdateRequest(LocalTime.of(13, 0), "정문", null),
                host.getMemberId(), NINE_AM);

        assertThat(partyBatchService.findFinishTargetIds(TODAY.atTime(12, 30))).doesNotContain(partyId);
        assertThat(partyBatchService.findFinishTargetIds(TODAY.atTime(13, 1))).contains(partyId);
    }

    @Test
    void 마감된_팟도_완료_처리되고_삭제_완료된_팟은_대상이_아니다() {
        // 삭제할 팟을 먼저 만든다. (삭제된 팟은 '한 끼에 팟 하나' 규칙에서 빠지므로 다음 팟을 만들 수 있다)
        Long deleted = createParty(LocalTime.of(12, 0));
        partyService.deleteParty(deleted, host.getMemberId());
        Long full = createParty(LocalTime.of(12, 0), 2);
        partyMemberService.joinParty(full, user02.getMemberId(), PartyTestSupport.MORNING);   // FULL
        flushAndClear();

        List<Long> targets = partyBatchService.findFinishTargetIds(TODAY.atTime(12, 1));
        assertThat(targets).contains(full).doesNotContain(deleted);

        partyBatchService.finishParty(full, TODAY.atTime(12, 1));
        flushAndClear();
        assertThat(partyBatchService.findFinishTargetIds(TODAY.atTime(12, 1))).doesNotContain(full);   // 이미 완료
    }

    @Test
    void 서버가_꺼져_밀린_어제_팟도_처리된다() {
        Long partyId = createParty(LocalTime.of(18, 0));
        setPartyDate(partyId, TODAY.minusDays(1));

        assertThat(partyBatchService.findFinishTargetIds(TODAY.atTime(0, 5))).contains(partyId);
    }

    @Test
    void 대상을_찾은_뒤_그_사이_삭제됐으면_건너뛴다() {
        Long partyId = createParty(LocalTime.of(12, 0));
        partyService.deleteParty(partyId, host.getMemberId());

        assertThat(partyBatchService.finishParty(partyId, TODAY.atTime(12, 1))).isFalse();
        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.DELETED);
    }

    @Test
    void 완료된_팟은_참여_불가_대화는_읽기_전용() {
        Long partyId = createParty(LocalTime.of(12, 0));
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);
        partyBatchService.finishParty(partyId, TODAY.atTime(12, 1));
        flushAndClear();

        assertThatThrownBy(() -> partyMemberService.joinParty(partyId,
                memberRepository.findByLoginId("user03").orElseThrow().getMemberId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARTY_FINISHED);
        assertThat(partyChatService.getChatList(partyId, null, user02.getMemberId()).readOnly()).isTrue();
    }

    // ===== 대화 기록 삭제 =====

    @Test
    void 어제_끝난_팟의_대화만_지우고_팟과_참여_이력은_남긴다() {
        Long yesterdayFinished = createPartyWithChat();
        setPartyDate(yesterdayFinished, TODAY.minusDays(1));
        setStatus(yesterdayFinished, PartyStatus.FINISHED);

        Long yesterdayDeleted = createPartyWithChat();
        setPartyDate(yesterdayDeleted, TODAY.minusDays(1));
        setStatus(yesterdayDeleted, PartyStatus.DELETED);

        // 오늘 팟은 마지막에 만든다. (오늘 점심 팟이 남아 있으면 '한 끼에 팟 하나' 규칙으로 다음 팟을 못 만든다)
        Long yesterdayActive = createPartyWithChat();   // 서버가 꺼져 아직 완료 처리 안 된 팟
        setPartyDate(yesterdayActive, TODAY.minusDays(1));

        Long todayFinished = createPartyWithChat();
        setStatus(todayFinished, PartyStatus.FINISHED);

        int deleted = partyBatchService.deleteOldChats(TODAY);
        flushAndClear();

        assertThat(deleted).isGreaterThanOrEqualTo(6);   // 만든 팟 2개 × (생성·참여·대화 3개) + 더미의 지난 대화
        assertThat(partyChatRepository.findAllAfter(yesterdayFinished, 0L)).isEmpty();
        assertThat(partyChatRepository.findAllAfter(yesterdayDeleted, 0L)).isEmpty();
        assertThat(partyChatRepository.findAllAfter(todayFinished, 0L)).hasSize(3);    // 오늘 끝난 팟은 내일 0시에
        assertThat(partyChatRepository.findAllAfter(yesterdayActive, 0L)).hasSize(3);  // 진행 중이면 남김

        // 팟·참여 이력은 그대로 (마이페이지 이력)
        assertThat(mealPartyRepository.findById(yesterdayFinished)).isPresent();
        assertThat(partyMemberRepository.countByPartyId(yesterdayFinished)).isEqualTo(2);
    }

    // ===== 도우미 =====

    private Long createParty(LocalTime mealTime) {
        return createParty(mealTime, 4);
    }

    private Long createParty(LocalTime mealTime, int capacity) {
        MealType mealType = mealTime.getHour() < 15 ? MealType.LUNCH : MealType.DINNER;
        return partyService.createParty(
                new PartyCreateRequest(restaurantRepository.findAll().get(0).getRestaurantId(),
                        mealType, mealTime, capacity, null),
                host.getMemberId(), NINE_AM).partyId();
    }

    /** 대화 3개짜리 팟: 생성(시스템) + user02 참여(시스템) + user02 메시지 */
    private Long createPartyWithChat() {
        Long partyId = createParty(LocalTime.of(12, 0));
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);
        partyChatService.createChat(partyId, new PartyChatCreateRequest("맛있게 드세요"), user02.getMemberId());
        return partyId;
    }

    private void setPartyDate(Long partyId, LocalDate date) {
        em.flush();
        em.createQuery("update MealParty p set p.partyDate = :date where p.partyId = :id")
                .setParameter("date", date)
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
        return mealPartyRepository.findById(partyId).map(MealParty::getStatus).orElseThrow();
    }

    private String lastChat(Long partyId) {
        List<PartyChat> chats = partyChatRepository.findAllAfter(partyId, 0L);
        return chats.get(chats.size() - 1).getContent();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
