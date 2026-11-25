package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyDetailResponse;
import com.sseukbap.party.dto.PartyListItemResponse;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.party.entity.PartyRole;
import com.sseukbap.party.repository.MealPartyRepository;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비공개 식사팟 확인 (2026-11-25 T4, 실제 DB, 끝나면 롤백)
 * - 비참여자의 목록에 안 보임 / 코드 없거나 틀리면 상세·참여 404 PARTY_NOT_FOUND / 맞는 코드면 가능
 * - 초대 링크에 코드가 붙음 / 방장이 코드를 새로 만들면 예전 코드는 404
 */
@SpringBootTest
@Transactional
class PartyPrivateTest {

    @Autowired PartyService partyService;
    @Autowired PartyMemberService partyMemberService;
    @Autowired MealPartyRepository mealPartyRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private static final LocalTime NOON = LocalTime.of(12, 0);

    private Member host;     // user01
    private Member user02;
    private Member user03;

    @BeforeEach
    void setUp() {
        host = memberRepository.findByLoginId("user01").orElseThrow();
        user02 = memberRepository.findByLoginId("user02").orElseThrow();
        user03 = memberRepository.findByLoginId("user03").orElseThrow();
        PartyTestSupport.hideExistingParties(em);   // 더미데이터 팟 숨김 ('한 끼에 팟 하나' 규칙)
    }

    @Test
    void 비공개로_만들면_추측할_수_없는_초대_코드가_생기고_공개는_코드가_없다() {
        MealParty privateParty = load(createParty(true));
        assertThat(privateParty.isPrivateParty()).isTrue();
        assertThat(privateParty.getInviteCode()).hasSize(22).matches("[A-Za-z0-9_-]+");

        PartyTestSupport.moveToTomorrow(em, privateParty.getPartyId());   // 같은 방장이 오늘 점심을 또 만들기 위해
        MealParty publicParty = load(createParty(false));
        assertThat(publicParty.isPrivateParty()).isFalse();
        assertThat(publicParty.getInviteCode()).isNull();
    }

    @Test
    void 비공개_식사팟은_참여자의_목록에만_보인다() {
        Long partyId = createParty(true);

        assertThat(listIds(host)).contains(partyId);
        assertThat(listIds(user02)).doesNotContain(partyId);
        assertThat(partyService.getPartyList(null, null, null, null, null, user02.getMemberId()).lunchCount())
                .isZero();

        PartyListItemResponse item = partyService.getPartyList(null, null, null, null, null, host.getMemberId())
                .parties().stream().filter(p -> p.partyId().equals(partyId)).findFirst().orElseThrow();
        assertThat(item.isPrivate()).isTrue();
        assertThat(item.joined()).isTrue();
    }

    @Test
    void 코드가_없거나_틀리면_상세는_404_PARTY_NOT_FOUND_맞으면_볼_수_있다() {
        Long partyId = createParty(true);
        String code = codeOf(partyId);

        assertErrorCode(() -> partyService.getParty(partyId, user02.getMemberId()), ErrorCode.PARTY_NOT_FOUND);
        assertErrorCode(() -> partyService.getParty(partyId, user02.getMemberId(), "wrong-code"), ErrorCode.PARTY_NOT_FOUND);

        PartyDetailResponse detail = partyService.getParty(partyId, user02.getMemberId(), code);
        assertThat(detail.isPrivate()).isTrue();
        assertThat(detail.myRole()).isEqualTo(PartyRole.NONE);

        // 참여자(방장)는 코드 없이 볼 수 있다
        assertThat(partyService.getParty(partyId, host.getMemberId()).myRole()).isEqualTo(PartyRole.HOST);
    }

    @Test
    void 코드가_맞아야_참여할_수_있고_참여한_뒤에는_코드_없이_볼_수_있다() {
        Long partyId = createParty(true);
        String code = codeOf(partyId);

        assertErrorCode(() -> partyMemberService.joinParty(partyId, user02.getMemberId(), null, PartyTestSupport.MORNING),
                ErrorCode.PARTY_NOT_FOUND);
        assertErrorCode(() -> partyMemberService.joinParty(partyId, user02.getMemberId(), "wrong", PartyTestSupport.MORNING),
                ErrorCode.PARTY_NOT_FOUND);

        assertThat(partyMemberService.joinParty(partyId, user02.getMemberId(), code, PartyTestSupport.MORNING).memberCount())
                .isEqualTo(2);
        assertThat(partyService.getParty(partyId, user02.getMemberId()).myRole()).isEqualTo(PartyRole.MEMBER);
        assertThat(listIds(user02)).contains(partyId);
    }

    @Test
    void 초대_링크에_코드가_붙는다() {
        Long privateId = createParty(true);
        assertThat(partyService.getInviteLink(privateId, host.getMemberId()).path())
                .isEqualTo("/party-detail.html?partyId=" + privateId + "&code=" + codeOf(privateId));

        PartyTestSupport.moveToTomorrow(em, privateId);
        Long publicId = createParty(false);
        assertThat(partyService.getInviteLink(publicId, host.getMemberId()).path())
                .isEqualTo("/party-detail.html?partyId=" + publicId);
    }

    @Test
    void 코드를_새로_만들면_예전_코드는_404_새_코드는_된다() {
        Long partyId = createParty(true);
        String oldCode = codeOf(partyId);

        String path = partyService.regenerateInviteCode(partyId, host.getMemberId()).path();
        flushAndClear();
        String newCode = codeOf(partyId);

        assertThat(newCode).isNotEqualTo(oldCode);
        assertThat(path).endsWith("&code=" + newCode);
        assertErrorCode(() -> partyService.getParty(partyId, user02.getMemberId(), oldCode), ErrorCode.PARTY_NOT_FOUND);
        assertThat(partyService.getParty(partyId, user02.getMemberId(), newCode).partyId()).isEqualTo(partyId);
    }

    @Test
    void 코드_새로_만들기는_방장만_비공개만() {
        Long privateId = createParty(true);
        partyMemberService.joinParty(privateId, user02.getMemberId(), codeOf(privateId), PartyTestSupport.MORNING);
        assertErrorCode(() -> partyService.regenerateInviteCode(privateId, user02.getMemberId()), ErrorCode.NOT_HOST);

        PartyTestSupport.moveToTomorrow(em, privateId);
        Long publicId = createParty(false);
        assertErrorCode(() -> partyService.regenerateInviteCode(publicId, host.getMemberId()), ErrorCode.INVALID_STATUS);
    }

    @Test
    void 삭제된_비공개_식사팟도_코드가_없으면_404_PARTY_NOT_FOUND로_숨긴다() {
        Long partyId = createParty(true);
        String code = codeOf(partyId);
        partyService.deleteParty(partyId, host.getMemberId());
        flushAndClear();

        assertErrorCode(() -> partyService.getParty(partyId, user03.getMemberId()), ErrorCode.PARTY_NOT_FOUND);
        assertErrorCode(() -> partyService.getParty(partyId, user03.getMemberId(), code), ErrorCode.PARTY_DELETED);
    }

    // ===== 도우미 =====

    /** user01이 오늘 12:00 점심 팟을 만든다 */
    private Long createParty(boolean isPrivate) {
        PartyCreateRequest request = new PartyCreateRequest(
                restaurantRepository.findAll().get(0).getRestaurantId(), null, MealType.LUNCH, NOON, 4, null, isPrivate);
        Long partyId = partyService.createParty(request, host.getMemberId(), PartyTestSupport.MORNING).partyId();
        flushAndClear();
        return partyId;
    }

    private MealParty load(Long partyId) {
        return mealPartyRepository.findById(partyId).orElseThrow();
    }

    private String codeOf(Long partyId) {
        return load(partyId).getInviteCode();
    }

    private List<Long> listIds(Member member) {
        return partyService.getPartyList(null, null, null, null, null, member.getMemberId())
                .parties().stream().map(PartyListItemResponse::partyId).toList();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
