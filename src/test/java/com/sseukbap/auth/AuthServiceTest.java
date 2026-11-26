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
 * 회원가입·로그인 규칙 확인.
 * C1 2026-11-24: 비밀번호 규칙 / C2: 탈퇴·로그인 잠금 / 2026-11-25 T3: 아이디 + 회사 이메일(@samsung.com), 닉네임 2~12자
 * 필요한 회원은 테스트 안에서 만든다. (DB에 어떤 더미·시연 데이터가 있든 통과하도록)
 * 테스트가 끝나면 @Transactional로 되돌려서 DB에 남지 않는다.
 */
@SpringBootTest
@Transactional
class AuthServiceTest {

    private static final String PASSWORD = "abcd1234";

    @Autowired AuthService authService;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    /** 다른 데이터와 겹치지 않는 숫자 (예: "12345678") */
    private static String uniqueSuffix() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    }

    private SignupResponse signup(String loginId, String email, String nickname) {
        return authService.signup(new SignupRequest(loginId, email, PASSWORD, PASSWORD, nickname));
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    // ===== 회원가입 =====

    @Test
    void 아이디와_회사_이메일로_가입하면_둘_다_소문자로_저장된다() {
        String suffix = uniqueSuffix();
        SignupResponse result = signup(" Jiho" + suffix + " ", " Jiho.Lee" + suffix + "@Samsung.com ", "가" + suffix);

        assertThat(result.loginId()).isEqualTo("jiho" + suffix);
        assertThat(result.email()).isEqualTo("jiho.lee" + suffix + "@samsung.com");
        assertThat(memberRepository.findByEmail("jiho.lee" + suffix + "@samsung.com")).isPresent();
    }

    @Test
    void 같은_아이디는_대소문자가_달라도_중복이다() {
        String suffix = uniqueSuffix();
        signup("dup" + suffix, "dup" + suffix + "@samsung.com", "가" + suffix);

        assertThat(authService.checkLoginId("DUP" + suffix).available()).isFalse();
        assertThat(authService.checkLoginId("new" + suffix).available()).isTrue();
        assertError(() -> authService.checkLoginId("ab"), ErrorCode.INVALID_REQUEST);   // 4자 미만
        assertError(() -> signup("Dup" + suffix, "other" + suffix + "@samsung.com", "나" + suffix),
                ErrorCode.LOGIN_ID_ALREADY_EXISTS);
    }

    @Test
    void 회사_도메인이_아니면_가입할_수_없다() {
        String suffix = uniqueSuffix();
        assertError(() -> signup("me" + suffix, "me" + suffix + "@gmail.com", "가" + suffix), ErrorCode.INVALID_EMAIL_DOMAIN);
        assertError(() -> authService.checkEmail("me" + suffix + "@naver.com"), ErrorCode.INVALID_EMAIL_DOMAIN);
        assertError(() -> authService.checkEmail("not-an-email"), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 같은_이메일은_대소문자가_달라도_중복이다() {
        String suffix = uniqueSuffix();
        signup("mail" + suffix, "dup" + suffix + "@samsung.com", "가" + suffix);

        assertThat(authService.checkEmail("DUP" + suffix + "@SAMSUNG.COM").available()).isFalse();
        assertThat(authService.checkEmail("new" + suffix + "@samsung.com").available()).isTrue();
        assertError(() -> signup("other" + suffix, "Dup" + suffix + "@samsung.com", "나" + suffix),
                ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void 비밀번호_규칙에_맞지_않으면_가입할_수_없다() {
        String suffix = uniqueSuffix();
        String loginId = "pw" + suffix;
        String email = loginId + "@samsung.com";
        String nickname = "가" + suffix;

        // 숫자 없음, 영문 없음, 공백, 아이디와 같음
        for (String password : new String[]{"abcdefgh", "12345678", "abcd 1234", loginId}) {
            assertThatThrownBy(() -> authService.signup(new SignupRequest(loginId, email, password, password, nickname)))
                    .as("password=%s", password)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        }
    }

    @Test
    void 닉네임은_12자까지_쓸_수_있다() {
        String suffix = uniqueSuffix();
        String twelve = "가나다라마바사" + suffix.substring(0, 5);   // 12자

        assertThat(authService.checkNickname(twelve).available()).isTrue();
        assertError(() -> authService.checkNickname(twelve + "아"), ErrorCode.INVALID_REQUEST);   // 13자
        SignupResponse result = signup("nick" + suffix, "nick" + suffix + "@samsung.com", twelve);
        assertThat(result.nickname()).isEqualTo(twelve);
    }

    @Test
    void 탈퇴한회원으로_시작하는_닉네임은_쓸_수_없다() {
        assertError(() -> authService.checkNickname("탈퇴한회원7"), ErrorCode.INVALID_REQUEST);
    }

    // ===== 로그인 =====

    @Test
    void 아이디로도_이메일로도_로그인된다_대소문자는_무시한다() {
        String suffix = uniqueSuffix();
        signup("login" + suffix, "mail" + suffix + "@samsung.com", "가" + suffix);

        MemberInfoResponse byId = authService.login(new LoginRequest(" LOGIN" + suffix + " ", PASSWORD));
        MemberInfoResponse byEmail = authService.login(new LoginRequest("MAIL" + suffix + "@Samsung.com", PASSWORD));

        assertThat(byId.memberId()).isEqualTo(byEmail.memberId());
        assertThat(byId.email()).isEqualTo("mail" + suffix + "@samsung.com");
        assertThat(byId.emailRequired()).isFalse();
    }

    @Test
    void 이메일이_없는_기존_회원은_로그인되고_emailRequired가_true() {
        String suffix = uniqueSuffix();
        memberRepository.saveAndFlush(Member.create("old" + suffix, passwordEncoder.encode(PASSWORD), "가" + suffix));

        MemberInfoResponse member = authService.login(new LoginRequest("OLD" + suffix, PASSWORD));

        assertThat(member.loginId()).isEqualTo("old" + suffix);
        assertThat(member.emailRequired()).isTrue();
    }

    @Test
    void 탈퇴한_회원은_로그인할_수_없고_같은_이메일로_다시_가입할_수_없다() {
        String suffix = uniqueSuffix();
        String email = "bye" + suffix + "@samsung.com";
        signup("bye" + suffix, email, "가" + suffix);
        Member member = memberRepository.findByEmail(email).orElseThrow();
        member.withdraw(java.time.LocalDateTime.now());
        memberRepository.flush();

        assertError(() -> authService.login(new LoginRequest(email, PASSWORD)), ErrorCode.LOGIN_FAILED);
        assertThat(authService.getMe(member.getMemberId())).isEmpty();
        assertThat(authService.checkEmail(email).available()).isFalse();   // 이메일 재사용 불가
    }

    @Test
    void 다섯_번_연속_틀리면_맞는_비밀번호도_잠긴다() {
        String suffix = uniqueSuffix();
        String loginId = "lock" + suffix;
        signup(loginId, loginId + "@samsung.com", "가" + suffix);

        for (int i = 0; i < 5; i++) {
            assertError(() -> authService.login(new LoginRequest(loginId, "wrong1234")), ErrorCode.LOGIN_FAILED);
        }
        assertError(() -> authService.login(new LoginRequest(loginId.toUpperCase(), PASSWORD)), ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
    }

    @Test
    void 성공하면_실패_횟수가_지워진다() {
        String suffix = uniqueSuffix();
        String loginId = "ok" + suffix;
        signup(loginId, loginId + "@samsung.com", "가" + suffix);

        for (int i = 0; i < 4; i++) {
            assertError(() -> authService.login(new LoginRequest(loginId, "wrong1234")), ErrorCode.LOGIN_FAILED);
        }
        authService.login(new LoginRequest(loginId, PASSWORD));
        for (int i = 0; i < 4; i++) {
            assertError(() -> authService.login(new LoginRequest(loginId, "wrong1234")), ErrorCode.LOGIN_FAILED);   // 아직 잠기지 않음
        }
    }
}
