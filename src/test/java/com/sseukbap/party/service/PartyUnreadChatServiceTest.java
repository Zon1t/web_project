package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.member.entity.Member;
import com.sseukbap.party.dto.PartyUnreadChatResponse;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 대화 '새 메시지' 알림 (2026-11-26). 회원·식당·팟은 테스트 안에서 만든다 */
@SpringBootTest
@Transactional
class PartyUnreadChatServiceTest {

    @Autowired PartyUnreadChatService service;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired PartyChatRepository partyChatRepository;
    @Autowired TestData data;

    @Test
    void 다른_사람의_일반_메시지만_세고_본_뒤의_것만_센다() {
        Member host = data.member();
        Member me = data.member();
        MealParty party = mealPartyRepository.saveAndFlush(MealParty.create(
                data.restaurant("알림테스트식당", RestaurantCategory.KOREAN), host,
                PartyTimePolicy.today().plusDays(1), MealType.LUNCH, LocalTime.of(12, 0), 4, null));
        partyMemberRepository.saveAndFlush(PartyMember.create(party, host));
        partyMemberRepository.saveAndFlush(PartyMember.create(party, me));

        partyChatRepository.saveAndFlush(PartyChat.createSystem(party, "참여했어요"));       // 시스템: 안 셈
        PartyChat first = partyChatRepository.saveAndFlush(PartyChat.createText(party, host, "안녕하세요"));
        partyChatRepository.saveAndFlush(PartyChat.createText(party, me, "네 반가워요"));     // 내 메시지: 안 셈
        PartyChat second = partyChatRepository.saveAndFlush(PartyChat.createText(party, host, "12시에 봬요"));

        // 처음(본 기록 없음): 참여한 뒤 온 다른 사람 메시지 2개
        List<PartyUnreadChatResponse> all = service.getUnreadChats(me.getMemberId(), Map.of());
        PartyUnreadChatResponse row = all.stream().filter(r -> r.partyId().equals(party.getPartyId())).findFirst().orElseThrow();
        assertThat(row.unreadCount()).isEqualTo(2);
        assertThat(row.lastChatId()).isEqualTo(second.getChatId());
        assertThat(row.restaurantName()).isEqualTo("알림테스트식당");

        // 첫 메시지까지 봤으면 1개
        assertThat(service.getUnreadChats(me.getMemberId(), Map.of(party.getPartyId(), first.getChatId())))
                .filteredOn(r -> r.partyId().equals(party.getPartyId()))
                .extracting(PartyUnreadChatResponse::unreadCount).containsExactly(1L);

        // 끝까지 봤으면 목록에서 빠진다
        assertThat(service.getUnreadChats(me.getMemberId(), Map.of(party.getPartyId(), second.getChatId())))
                .noneMatch(r -> r.partyId().equals(party.getPartyId()));
    }
}
