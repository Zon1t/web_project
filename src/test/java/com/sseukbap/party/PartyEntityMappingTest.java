package com.sseukbap.party;

import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.MealType;
import com.sseukbap.party.entity.MessageType;
import com.sseukbap.party.entity.PartyChat;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.party.repository.PartyChatRepository;
import com.sseukbap.party.repository.PartyMemberRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 식사팟 엔티티·Repository가 DB 테이블(docs/sql/01_ddl.sql)과 맞게 연결됐는지 확인한다.
 *
 * 실행 전 준비: 로컬 DB에 docs/sql 01 ~ 04 스크립트 실행, application-local.yml 작성
 * 실행: IntelliJ에서 클래스 옆 ▶ 버튼, 또는 gradlew.bat test --tests "*PartyEntityMappingTest"
 *
 * @Transactional: 테스트가 끝나면 저장한 데이터를 모두 되돌린다(rollback). DB 데이터는 바뀌지 않는다.
 */
@SpringBootTest
@Transactional
class PartyEntityMappingTest {

    @Autowired EntityManager em;
    @Autowired MemberRepository memberRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired PartyChatRepository partyChatRepository;

    @Test
    void 더미_데이터를_읽을_수_있다() {
        // 식사팟 → 식당·방장 연결, 'HH:mm' 시간 변환, 상태 enum
        List<MealParty> parties = mealPartyRepository.findAll();
        assertThat(parties).isNotEmpty();
        parties.forEach(p -> {
            assertThat(p.getRestaurant().getName()).isNotBlank();
            assertThat(p.getHost().getNickname()).isNotBlank();
            assertThat(p.getMealTime()).isNotNull();
            assertThat(p.getStatus()).isNotNull();
            assertThat(p.getBaseTime()).isNotNull();
        });

        // 일정 확정된 팟은 확정 시간이 있다
        assertThat(parties).filteredOn(p -> p.getStatus() == PartyStatus.SCHEDULED)
                .isNotEmpty()
                .allMatch(MealParty::hasSchedule);

        // 소프트 삭제된 팟도 행은 남아 있다
        assertThat(parties).anyMatch(MealParty::isDeleted);

        // 참여자: Y/N → boolean 변환 (따봉 'Y' 데이터가 있어야 함)
        List<PartyMember> partyMembers = partyMemberRepository.findAll();
        assertThat(partyMembers).anyMatch(PartyMember::isConfirmed);
        assertThat(partyMembers).anyMatch(PartyMember::isThumbsUp);
        assertThat(partyMembers).anyMatch(pm -> !pm.isConfirmed());
        partyMembers.forEach(pm -> assertThat(pm.getJoinedAt()).isNotNull());

        // 대화: 시스템 메시지는 작성자 없음
        List<PartyChat> chats = partyChatRepository.findAll();
        assertThat(chats).filteredOn(PartyChat::isSystem)
                .isNotEmpty()
                .allMatch(c -> c.getMember() == null);
        assertThat(chats).filteredOn(c -> c.getMessageType() == MessageType.TEXT)
                .isNotEmpty()
                .allMatch(c -> c.getMember() != null);
    }

    @Test
    void 새_데이터를_저장하고_조회할_수_있다() {
        Member host = memberRepository.findByLoginId("user01").orElseThrow();
        Member guest = memberRepository.findByLoginId("user02").orElseThrow();
        Restaurant restaurant = restaurantRepository.findAll().get(0);

        MealParty party = mealPartyRepository.save(MealParty.create(
                restaurant, host, LocalDate.now(), MealType.LUNCH, LocalTime.of(12, 30), 4, "테스트 팟"));
        partyMemberRepository.save(PartyMember.create(party, host));
        partyMemberRepository.save(PartyMember.create(party, guest));
        PartyChat systemChat = partyChatRepository.save(PartyChat.createSystem(party, "점심요정님이 참여했습니다."));
        partyChatRepository.save(PartyChat.createText(party, guest, "안녕하세요"));

        em.flush();   // INSERT SQL을 지금 DB로 보낸다 (컬럼·시퀀스·제약조건 오류가 여기서 드러남)
        em.clear();   // 1차 캐시를 비워서 아래 조회가 실제 DB에서 읽게 한다

        Long partyId = party.getPartyId();

        // 식사팟: 저장한 값이 그대로 돌아오는지
        MealParty found = mealPartyRepository.findById(partyId).orElseThrow();
        assertThat(found.getPartyDate()).isEqualTo(LocalDate.now());
        assertThat(found.getMealType()).isEqualTo(MealType.LUNCH);
        assertThat(found.getMealTime()).isEqualTo(LocalTime.of(12, 30));
        assertThat(found.getCapacity()).isEqualTo(4);
        assertThat(found.getStatus()).isEqualTo(PartyStatus.RECRUITING);
        assertThat(found.getConfirmedTime()).isNull();
        assertThat(found.hasSchedule()).isFalse();
        assertThat(found.getBaseTime()).isEqualTo(LocalTime.of(12, 30));
        assertThat(found.isHost(host.getMemberId())).isTrue();
        assertThat(found.isHost(guest.getMemberId())).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();

        // 행 잠금 조회 (Oracle에서 FOR UPDATE SQL이 정상 실행되는지)
        assertThat(mealPartyRepository.findByIdForUpdate(partyId)).isPresent();

        // 참여자 조회
        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(2);
        assertThat(partyMemberRepository.existsByPartyIdAndMemberId(partyId, guest.getMemberId())).isTrue();
        PartyMember foundGuest = partyMemberRepository
                .findByPartyIdAndMemberId(partyId, guest.getMemberId()).orElseThrow();
        assertThat(foundGuest.isConfirmed()).isFalse();
        assertThat(foundGuest.isThumbsUp()).isFalse();

        List<PartyMember> members = partyMemberRepository.findAllWithMemberByPartyId(partyId);
        assertThat(members).extracting(pm -> pm.getMember().getLoginId())
                .containsExactly("user01", "user02");

        // 대화 폴링: afterId 이후만, 오래된 순
        List<PartyChat> all = partyChatRepository.findAllAfter(partyId, 0L);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).isSystem()).isTrue();
        assertThat(all.get(0).getMember()).isNull();
        assertThat(all.get(1).getMember().getLoginId()).isEqualTo("user02");

        List<PartyChat> afterSystem = partyChatRepository.findAllAfter(partyId, systemChat.getChatId());
        assertThat(afterSystem).extracting(PartyChat::getContent).containsExactly("안녕하세요");
    }
}
