package com.sseukbap.auth;

import com.sseukbap.auth.dto.LoginRequest;
import com.sseukbap.auth.dto.MemberInfoResponse;
import com.sseukbap.auth.dto.SignupRequest;
import com.sseukbap.auth.dto.SignupResponse;
import com.sseukbap.auth.service.AuthService;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 회원가입·로그인 규칙 확인 (C1 2026-11-24: 아이디 소문자 통일, 비밀번호 규칙).
 * 필요한 회원은 테스트 안에서 만든다. (DB에 어떤 더미·시연 데이터가 있든 통과하도록)
 * 테스트가 끝나면 @Transactional로 되돌려서 DB에 남지 않는다.
 */
@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired AuthService authService;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    /** 다른 데이터와 겹치지 않는 아이디·닉네임 (예: "tq12345678", "닉12345678") */
    private static String uniqueSuffix() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    }

    @Test
    void 대문자로_가입해도_소문자로_저장된다() {
        String suffix = uniqueSuffix();
        SignupResponse result = authService.signup(
                new SignupRequest("TQ" + suffix, "abcd1234", "abcd1234", "가" + suffix));

        assertThat(result.loginId()).isEqualTo("tq" + suffix);
        assertThat(memberRepository.findByLoginId("tq" + suffix)).isPresent();
    }

    @Test
    void 대소문자만_다른_아이디는_중복이다() {
        String suffix = uniqueSuffix();
        memberRepository.saveAndFlush(Member.create("tq" + suffix, passwordEncoder.encode("abcd1234"), "가" + suffix));

        assertThat(authService.checkLoginId("TQ" + suffix).available()).isFalse();
        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("Tq" + suffix, "abcd1234", "abcd1234", "나" + suffix)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
    }

    @Test
    void 아이디를_대문자로_입력해도_로그인된다() {
        String suffix = uniqueSuffix();
        memberRepository.saveAndFlush(Member.create("tq" + suffix, passwordEncoder.encode("abcd1234"), "가" + suffix));

        MemberInfoResponse member = authService.login(new LoginRequest("TQ" + suffix, "abcd1234"));

        assertThat(member.loginId()).isEqualTo("tq" + suffix);
    }

    @Test
    void 소문자_통일_전에_대문자로_가입한_계정도_로그인된다() {
        String suffix = uniqueSuffix();
        memberRepository.saveAndFlush(Member.create("Tq" + suffix, passwordEncoder.encode("abcd1234"), "가" + suffix));

        MemberInfoResponse member = authService.login(new LoginRequest("tq" + suffix, "abcd1234"));

        assertThat(member.loginId()).isEqualTo("Tq" + suffix);
    }

    @Test
    void 비밀번호_규칙에_맞지_않으면_가입할_수_없다() {
        String suffix = uniqueSuffix();
        String loginId = "tq" + suffix;
        String nickname = "가" + suffix;

        // 숫자 없음, 영문 없음, 공백, 아이디와 같음
        for (String password : new String[]{"abcdefgh", "12345678", "abcd 1234", loginId}) {
            assertThatThrownBy(() -> authService.signup(new SignupRequest(loginId, password, password, nickname)))
                    .as("password=%s", password)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        }
    }
}
