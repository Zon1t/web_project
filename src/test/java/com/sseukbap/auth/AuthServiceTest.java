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
                new SignupRequest("TQ" + suffix, "abcd1234", "abcd1234", "가" + suffix, null));

        assertThat(result.loginId()).isEqualTo("tq" + suffix);
        assertThat(memberRepository.findByLoginId("tq" + suffix)).isPresent();
    }

    @Test
    void 대소문자만_다른_아이디는_중복이다() {
        String suffix = uniqueSuffix();
        memberRepository.saveAndFlush(Member.create("tq" + suffix, passwordEncoder.encode("abcd1234"), "가" + suffix));

        assertThat(authService.checkLoginId("TQ" + suffix).available()).isFalse();
        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("Tq" + suffix, "abcd1234", "abcd1234", "나" + suffix, null)))
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
            assertThatThrownBy(() -> authService.signup(new SignupRequest(loginId, password, password, nickname, null)))
                    .as("password=%s", password)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        }
    }

    // ===== 2026-11-24: 회사 이메일, 탈퇴 회원, 로그인 시도 제한, 예약 닉네임 =====

    @Test
    void 가입할_때_이메일은_소문자로_저장되고_중복이면_409() {
        String suffix = uniqueSuffix();
        String email = "Mail" + suffix + "@Samsung.com";
        authService.signup(new SignupRequest("tm" + suffix, "abcd1234", "abcd1234", "가" + suffix, " " + email + " "));

        assertThat(memberRepository.findByLoginId("tm" + suffix).orElseThrow().getEmail())
                .isEqualTo(email.toLowerCase());
        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("tn" + suffix, "abcd1234", "abcd1234", "나" + suffix, email.toUpperCase())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void 탈퇴한_회원은_로그인할_수_없다() {
        String suffix = uniqueSuffix();
        Member member = memberRepository.saveAndFlush(
                Member.create("tw" + suffix, passwordEncoder.encode("abcd1234"), "가" + suffix));
        member.withdraw(java.time.LocalDateTime.now());
        memberRepository.flush();

        assertThatThrownBy(() -> authService.login(new LoginRequest("tw" + suffix, "abcd1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);
        assertThat(authService.getMe(member.getMemberId())).isEmpty();
        assertThat(authService.checkLoginId("tw" + suffix).available()).isFalse();   // 아이디 재사용 불가
    }

    @Test
    void 다섯_번_연속_틀리면_맞는_비밀번호도_잠긴다() {
        String suffix = uniqueSuffix();
        memberRepository.saveAndFlush(Member.create("tl" + suffix, passwordEncoder.encode("abcd1234"), "가" + suffix));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("tl" + suffix, "wrong1234")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);
        }
        assertThatThrownBy(() -> authService.login(new LoginRequest("TL" + suffix, "abcd1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
    }

    @Test
    void 성공하면_실패_횟수가_지워진다() {
        String suffix = uniqueSuffix();
        memberRepository.saveAndFlush(Member.create("ts" + suffix, passwordEncoder.encode("abcd1234"), "가" + suffix));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("ts" + suffix, "wrong1234")))
                    .isInstanceOf(BusinessException.class);
        }
        authService.login(new LoginRequest("ts" + suffix, "abcd1234"));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("ts" + suffix, "wrong1234")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_FAILED);   // 아직 잠기지 않음
        }
    }

    @Test
    void 탈퇴한회원으로_시작하는_닉네임은_쓸_수_없다() {
        assertThatThrownBy(() -> authService.checkNickname("탈퇴한회원7"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
