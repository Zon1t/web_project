package com.sseukbap.auth;

import com.sseukbap.auth.dto.PasswordResetConfirmRequest;
import com.sseukbap.auth.dto.PasswordResetRequest;
import com.sseukbap.auth.dto.PasswordResetRequestResponse;
import com.sseukbap.auth.dto.PasswordResetVerifyRequest;
import com.sseukbap.auth.mail.MailSender;
import com.sseukbap.auth.service.PasswordResetService;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 비밀번호 찾기 확인 (2026-11-24).
 * 메일 발송기는 가짜(@MockitoBean)로 바꿔서, 보낸 본문에서 인증번호를 꺼내 쓴다.
 * 필요한 회원은 테스트 안에서 만들고, 끝나면 @Transactional로 되돌린다.
 */
@SpringBootTest
@Transactional
class PasswordResetServiceTest {

    private static final Pattern CODE = Pattern.compile("인증번호는 (\\d{6})");   // 아이디의 숫자와 헷갈리지 않게 문구로 찾는다

    @Autowired PasswordResetService passwordResetService;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean MailSender mailSender;

    Member member;
    String email;   // 이 회원이 등록한 회사 이메일 (2026-11-24: 등록 이메일과 같아야 메일을 보낸다)

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        email = "tr" + suffix + "@samsung.com";
        member = Member.create("tr" + suffix, passwordEncoder.encode("oldpass11"), "가" + suffix);
        member.changeEmail(email);
        member = memberRepository.saveAndFlush(member);
    }

    @Test
    void 받은_인증번호로_비밀번호를_바꿀_수_있다() {
        String code = requestCode(email);

        passwordResetService.confirm(new PasswordResetConfirmRequest(email, code, "newpass22", "newpass22"));

        Member saved = memberRepository.findById(member.getMemberId()).orElseThrow();
        assertThat(passwordEncoder.matches("newpass22", saved.getPassword())).isTrue();
    }

    @Test
    void 이메일을_대문자로_입력해도_된다() {
        String code = requestCode(email.toUpperCase());

        passwordResetService.confirm(new PasswordResetConfirmRequest(email.toUpperCase(), code, "newpass22", "newpass22"));

        Member saved = memberRepository.findById(member.getMemberId()).orElseThrow();
        assertThat(passwordEncoder.matches("newpass22", saved.getPassword())).isTrue();
    }

    @Test
    void 인증번호는_한_번만_쓸_수_있다() {
        String code = requestCode(email);
        passwordResetService.confirm(new PasswordResetConfirmRequest(email, code, "newpass22", "newpass22"));

        assertResetCodeError(() -> passwordResetService.confirm(
                new PasswordResetConfirmRequest(email, code, "newpass33", "newpass33")));
    }

    @Test
    void 다섯_번_틀리면_맞는_번호도_쓸_수_없다() {
        String code = requestCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertResetCodeError(() -> passwordResetService.confirm(
                    new PasswordResetConfirmRequest(email, wrong, "newpass22", "newpass22")));
        }
        assertResetCodeError(() -> passwordResetService.confirm(
                new PasswordResetConfirmRequest(email, code, "newpass22", "newpass22")));
    }

    @Test
    void 새_비밀번호_규칙이_틀리면_번호를_쓰지_않고_INVALID_REQUEST() {
        String code = requestCode(email);

        assertThatThrownBy(() -> passwordResetService.confirm(
                new PasswordResetConfirmRequest(email, code, "onlyletters", "onlyletters")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

        // 번호는 그대로 남아 있어서 규칙에 맞게 다시 하면 된다
        passwordResetService.confirm(new PasswordResetConfirmRequest(email, code, "newpass22", "newpass22"));
    }

    @Test
    void 등록되지_않은_이메일도_같은_응답이고_메일은_보내지_않는다() {
        String unknown = "nobody" + member.getMemberId() + "@samsung.com";
        PasswordResetRequestResponse response = passwordResetService.request(new PasswordResetRequest(unknown));

        assertThat(response.email()).isEqualTo(unknown);
        assertThat(response.expiresInSeconds()).isEqualTo(300);
        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void 탈퇴한_회원에게는_보내지_않는다() {
        member.withdraw(java.time.LocalDateTime.now());   // 이메일은 남지만 (2026-11-25 T3) 보내지 않는다
        memberRepository.flush();

        passwordResetService.request(new PasswordResetRequest(email));

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void 인증번호_확인은_번호를_지우지_않아서_이어서_비밀번호를_바꿀_수_있다() {
        String code = requestCode(email);

        passwordResetService.verify(new PasswordResetVerifyRequest(email, code));
        passwordResetService.confirm(new PasswordResetConfirmRequest(email, code, "newpass22", "newpass22"));

        Member saved = memberRepository.findById(member.getMemberId()).orElseThrow();
        assertThat(passwordEncoder.matches("newpass22", saved.getPassword())).isTrue();
    }

    @Test
    void 인증번호_확인에서_틀리면_INVALID_RESET_CODE() {
        String code = requestCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertResetCodeError(() -> passwordResetService.verify(new PasswordResetVerifyRequest(email, wrong)));
    }

    @Test
    void 인증번호_확인에서_다섯_번_틀리면_번호가_사라진다() {
        String code = requestCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertResetCodeError(() -> passwordResetService.verify(new PasswordResetVerifyRequest(email, wrong)));
        }
        assertResetCodeError(() -> passwordResetService.verify(new PasswordResetVerifyRequest(email, code)));
    }

    /** 인증번호를 요청하고, 가짜 발송기에 넘어간 본문에서 6자리 번호를 꺼낸다 */
    private String requestCode(String input) {
        passwordResetService.request(new PasswordResetRequest(input));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq(email), anyString(), body.capture());
        Matcher matcher = CODE.matcher(body.getValue());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static void assertResetCodeError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_RESET_CODE);
    }
}
