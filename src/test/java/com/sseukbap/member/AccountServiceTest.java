package com.sseukbap.member;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.dto.EmailChangeRequest;
import com.sseukbap.member.dto.NicknameChangeRequest;
import com.sseukbap.member.dto.PasswordChangeRequest;
import com.sseukbap.member.dto.WithdrawRequest;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.entity.MemberStatus;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.member.service.AccountService;
import com.sseukbap.restaurant.entity.Interest;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.InterestRepository;
import com.sseukbap.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계정 설정(비밀번호·닉네임 변경) 확인 (C1 2026-11-24).
 * 필요한 회원은 테스트 안에서 만들고, 끝나면 @Transactional로 되돌린다.
 */
@SpringBootTest
@Transactional
class AccountServiceTest {

    private static final String PASSWORD = "abcd1234";

    @Autowired AccountService accountService;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired InterestRepository interestRepository;
    @Autowired TestData data;

    Member me;
    Member other;

    @BeforeEach
    void setUp() {
        String a = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        String b = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        me = memberRepository.saveAndFlush(Member.create("ta" + a, passwordEncoder.encode(PASSWORD), "가" + a));
        other = memberRepository.saveAndFlush(Member.create("tb" + b, passwordEncoder.encode(PASSWORD), "나" + b));
    }

    // ===== 비밀번호 =====

    @Test
    void 비밀번호를_바꾸면_새_비밀번호로_확인된다() {
        accountService.changePassword(me.getMemberId(), new PasswordChangeRequest(PASSWORD, "newpass99", "newpass99"));

        Member saved = memberRepository.findById(me.getMemberId()).orElseThrow();
        assertThat(passwordEncoder.matches("newpass99", saved.getPassword())).isTrue();
    }

    @Test
    void 현재_비밀번호가_틀리면_INVALID_PASSWORD() {
        assertError(() -> accountService.changePassword(me.getMemberId(),
                new PasswordChangeRequest("wrong1234", "newpass99", "newpass99")), ErrorCode.INVALID_PASSWORD);
    }

    @Test
    void 새_비밀번호가_규칙에_맞지_않으면_INVALID_REQUEST() {
        assertError(() -> accountService.changePassword(me.getMemberId(),
                new PasswordChangeRequest(PASSWORD, "onlyletters", "onlyletters")), ErrorCode.INVALID_REQUEST);
        assertError(() -> accountService.changePassword(me.getMemberId(),
                new PasswordChangeRequest(PASSWORD, me.getLoginId(), me.getLoginId())), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 새_비밀번호_확인이_다르면_PASSWORD_MISMATCH() {
        assertError(() -> accountService.changePassword(me.getMemberId(),
                new PasswordChangeRequest(PASSWORD, "newpass99", "newpass98")), ErrorCode.PASSWORD_MISMATCH);
    }

    @Test
    void 지금과_같은_비밀번호로는_바꿀_수_없다() {
        assertError(() -> accountService.changePassword(me.getMemberId(),
                new PasswordChangeRequest(PASSWORD, PASSWORD, PASSWORD)), ErrorCode.INVALID_REQUEST);
    }

    // ===== 닉네임 =====

    @Test
    void 닉네임을_바꾸면_공백을_뺀_값으로_저장된다() {
        String nickname = "새닉" + me.getMemberId();
        var result = accountService.changeNickname(me.getMemberId(), new NicknameChangeRequest("  " + nickname + " "));

        assertThat(result.nickname()).isEqualTo(nickname);
        assertThat(memberRepository.findById(me.getMemberId()).orElseThrow().getNickname()).isEqualTo(nickname);
    }

    @Test
    void 다른_회원이_쓰는_닉네임이면_NICKNAME_ALREADY_EXISTS() {
        assertError(() -> accountService.changeNickname(me.getMemberId(),
                new NicknameChangeRequest(other.getNickname())), ErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    @Test
    void 지금과_같은_닉네임이면_그대로_성공한다() {
        var result = accountService.changeNickname(me.getMemberId(), new NicknameChangeRequest(me.getNickname()));

        assertThat(result.nickname()).isEqualTo(me.getNickname());
    }

    // ===== 이메일 (2026-11-24) =====

    @Test
    void 이메일을_등록하면_소문자로_저장되고_다른_회원이_쓰면_409() {
        String email = "Acc" + me.getMemberId() + "@Samsung.com";
        var result = accountService.changeEmail(me.getMemberId(), new EmailChangeRequest(" " + email + " "));

        assertThat(result.email()).isEqualTo(email.toLowerCase());
        assertError(() -> accountService.changeEmail(other.getMemberId(), new EmailChangeRequest(email)),
                ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void 이메일_형식이_틀리면_INVALID_REQUEST() {
        for (String email : new String[]{null, "", "abc", "a@b", "a b@c.com"}) {
            assertError(() -> accountService.changeEmail(me.getMemberId(), new EmailChangeRequest(email)),
                    ErrorCode.INVALID_REQUEST);
        }
    }

    @Test
    void 회사_도메인이_아니면_INVALID_EMAIL_DOMAIN() {
        assertError(() -> accountService.changeEmail(me.getMemberId(), new EmailChangeRequest("me" + me.getMemberId() + "@gmail.com")),
                ErrorCode.INVALID_EMAIL_DOMAIN);
    }

    // ===== 회원 탈퇴 (C2, 2026-11-24) =====

    @Test
    void 탈퇴하면_상태_닉네임이_바뀌고_관심이_지워지며_이메일은_남는다() {
        accountService.changeEmail(me.getMemberId(), new EmailChangeRequest("bye" + me.getMemberId() + "@samsung.com"));
        Restaurant restaurant = data.restaurant("탈퇴관심" + TestData.uniqueNumber(), RestaurantCategory.KOREAN);
        interestRepository.saveAndFlush(Interest.create(restaurant, me));
        String oldNickname = me.getNickname();

        accountService.withdraw(me.getMemberId(), new WithdrawRequest(PASSWORD));

        Member saved = memberRepository.findById(me.getMemberId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(saved.getWithdrawnAt()).isNotNull();
        assertThat(saved.getNickname()).isEqualTo("탈퇴한회원" + me.getMemberId());
        assertThat(saved.getEmail()).isEqualTo("bye" + me.getMemberId() + "@samsung.com");   // 같은 이메일로 재가입 방지 (2026-11-25 T3)
        assertThat(interestRepository.existsByRestaurant_RestaurantIdAndMember_MemberId(
                restaurant.getRestaurantId(), me.getMemberId())).isFalse();
        // 원래 닉네임은 다른 사람이 쓸 수 있다
        assertThat(memberRepository.existsByNickname(oldNickname)).isFalse();
    }

    @Test
    void 탈퇴_비밀번호가_틀리면_INVALID_PASSWORD이고_아무것도_바뀌지_않는다() {
        assertError(() -> accountService.withdraw(me.getMemberId(), new WithdrawRequest("wrong1234")),
                ErrorCode.INVALID_PASSWORD);
        assertThat(memberRepository.findById(me.getMemberId()).orElseThrow().isWithdrawn()).isFalse();
    }

    @Test
    void 탈퇴한_회원은_계정_설정을_쓸_수_없다() {
        accountService.withdraw(me.getMemberId(), new WithdrawRequest(PASSWORD));

        assertError(() -> accountService.changeNickname(me.getMemberId(), new NicknameChangeRequest("다시닉" + me.getMemberId())),
                ErrorCode.UNAUTHORIZED);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
