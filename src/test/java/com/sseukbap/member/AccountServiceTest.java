package com.sseukbap.member;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.dto.NicknameChangeRequest;
import com.sseukbap.member.dto.PasswordChangeRequest;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.member.service.AccountService;
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

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
