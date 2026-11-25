package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyChatCreateRequest;
import com.sseukbap.party.dto.PartyChatCreateResponse;
import com.sseukbap.party.dto.PartyChatListResponse;
import com.sseukbap.party.dto.PartyChatResponse;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.MessageType;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyChatRepository;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 대화 조회(폴링)·전송 확인 (실제 DB, 끝나면 롤백)
 * 테스트마다 user01이 팟을 만들고(시스템 메시지 1개), user02가 참여한다(시스템 메시지 1개).
 */
@SpringBootTest
@Transactional
class PartyChatServiceTest {

    @Autowired PartyChatService partyChatService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired PartyService partyService;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired PartyChatRepository partyChatRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;
    @Autowired JsonMapper jsonMapper;

    private Member host;     // user01
    private Member user02;   // 참여자
    private Member user03;   // 미참여자
    private Long partyId;

    @BeforeEach
    void setUp() {
        host = memberRepository.findByLoginId("user01").orElseThrow();
        user02 = memberRepository.findByLoginId("user02").orElseThrow();
        user03 = memberRepository.findByLoginId("user03").orElseThrow();
        PartyTestSupport.hideExistingParties(em);   // 더미데이터 팟 숨김 ('한 끼에 팟 하나' 규칙)

        partyId = partyService.createParty(
                new PartyCreateRequest(restaurantRepository.findAll().get(0).getRestaurantId(),
                        MealType.LUNCH, LocalTime.of(12, 0), 4, null),
                host.getMemberId(), LocalDate.now().atTime(9, 0)).partyId();
        partyMemberService.joinParty(partyId, user02.getMemberId(), PartyTestSupport.MORNING);
    }

    // ===== 조회 =====

    @Test
    void 처음_조회하면_시스템_메시지까지_전체가_나온다() {
        PartyChatListResponse response = partyChatService.getChatList(partyId, null, user02.getMemberId());

        assertThat(response.readOnly()).isFalse();
        assertThat(response.chats()).extracting(PartyChatResponse::content).containsExactly(
                host.getNickname() + "님이 식사팟을 만들었어요",
                user02.getNickname() + "님이 참여했어요");
        assertThat(response.chats()).allSatisfy(chat -> {
            assertThat(chat.type()).isEqualTo(MessageType.SYSTEM);
            assertThat(chat.nickname()).isNull();
            assertThat(chat.mine()).isFalse();
        });
    }

    @Test
    void 전송하면_afterId_이후로_새_메시지만_받는다() {
        List<PartyChatResponse> first = partyChatService.getChatList(partyId, null, host.getMemberId()).chats();
        Long lastId = first.get(first.size() - 1).chatId();

        partyChatService.createChat(partyId, new PartyChatCreateRequest("안녕하세요!"), user02.getMemberId());
        partyChatService.createChat(partyId, new PartyChatCreateRequest("반가워요"), host.getMemberId());

        List<PartyChatResponse> newChats = partyChatService.getChatList(partyId, lastId, host.getMemberId()).chats();
        assertThat(newChats).extracting(PartyChatResponse::content).containsExactly("안녕하세요!", "반가워요");
        assertThat(newChats).extracting(PartyChatResponse::nickname)
                .containsExactly(user02.getNickname(), host.getNickname());
        assertThat(newChats).extracting(PartyChatResponse::mine).containsExactly(false, true);   // host가 조회

        // 새 메시지가 없으면 빈 배열
        Long newest = newChats.get(1).chatId();
        assertThat(partyChatService.getChatList(partyId, newest, host.getMemberId()).chats()).isEmpty();
    }

    @Test
    void 최대_200개까지만_준다() {
        MealParty party = mealPartyRepository.findById(partyId).orElseThrow();
        for (int i = 1; i <= 205; i++) {
            partyChatRepository.save(PartyChat.createText(party, host, "메시지 " + i));
        }
        em.flush();
        em.clear();

        List<PartyChatResponse> first = partyChatService.getChatList(partyId, null, host.getMemberId()).chats();
        assertThat(first).hasSize(200);   // 전체 207개 (시스템 2 + 205) 중 앞 200개

        List<PartyChatResponse> rest = partyChatService.getChatList(
                partyId, first.get(199).chatId(), host.getMemberId()).chats();
        assertThat(rest).hasSize(7);
        assertThat(rest.get(6).content()).isEqualTo("메시지 205");
    }

    @Test
    void 참여자가_아니면_403_NOT_JOINED() {
        assertThatThrownBy(() -> partyChatService.getChatList(partyId, null, user03.getMemberId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("참여자만 대화를 볼 수 있습니다.")
                .satisfies(e -> {
                    ErrorCode code = ((BusinessException) e).getErrorCode();
                    assertThat(code.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(code.getCode()).isEqualTo("NOT_JOINED");
                });

        assertThatThrownBy(() -> partyChatService.createChat(
                partyId, new PartyChatCreateRequest("끼워 주세요"), user03.getMemberId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("참여자만 대화할 수 있습니다.")
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_JOINED_FORBIDDEN);
    }

    @Test
    void 나간_사람은_대화를_볼_수_없다() {
        partyMemberService.leaveParty(partyId, user02.getMemberId());

        assertErrorCode(() -> partyChatService.getChatList(partyId, null, user02.getMemberId()),
                ErrorCode.NOT_JOINED_FORBIDDEN);
    }

    @Test
    void 식사가_끝나면_읽기_전용이고_전송은_CHAT_READ_ONLY() {
        em.flush();
        em.createQuery("update MealParty p set p.status = :status where p.partyId = :id")
                .setParameter("status", PartyStatus.FINISHED)
                .setParameter("id", partyId)
                .executeUpdate();
        em.clear();

        assertThat(partyChatService.getChatList(partyId, null, user02.getMemberId()).readOnly()).isTrue();
        assertErrorCode(() -> partyChatService.createChat(partyId, new PartyChatCreateRequest("잘 먹었습니다"),
                user02.getMemberId()), ErrorCode.CHAT_READ_ONLY);
    }

    @Test
    void 삭제된_팟은_PARTY_DELETED() {
        partyService.deleteParty(partyId, host.getMemberId());

        assertErrorCode(() -> partyChatService.getChatList(partyId, null, user02.getMemberId()), ErrorCode.PARTY_DELETED);
        assertErrorCode(() -> partyChatService.createChat(partyId, new PartyChatCreateRequest("?"),
                user02.getMemberId()), ErrorCode.PARTY_DELETED);
    }

    // ===== 전송 응답 =====

    @Test
    void 전송_응답의_시각은_조회한_시각과_같다() throws Exception {
        PartyChatCreateResponse sent = partyChatService.createChat(
                partyId, new PartyChatCreateRequest("12시에 로비에서 만나요"), user02.getMemberId());
        em.flush();
        em.clear();

        PartyChatResponse found = partyChatService.getChatList(partyId, sent.chatId() - 1, user02.getMemberId())
                .chats().get(0);
        assertThat(found.chatId()).isEqualTo(sent.chatId());
        assertThat(found.createdAt()).isEqualTo(sent.createdAt());
        assertThat(found.mine()).isTrue();

        String json = jsonMapper.writeValueAsString(found);
        assertThat(json).containsPattern("\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\"");
        assertThat(json).contains("\"type\":\"TEXT\"");
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
