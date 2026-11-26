package com.sseukbap.auth.service;

import com.sseukbap.auth.dto.PasswordResetConfirmRequest;
import com.sseukbap.auth.dto.PasswordResetRequest;
import com.sseukbap.auth.dto.PasswordResetRequestResponse;
import com.sseukbap.auth.dto.PasswordResetVerifyRequest;
import com.sseukbap.auth.mail.MailSender;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 비밀번호 찾기: 이메일 인증번호 → 새 비밀번호 (2026-11-24, C)
 *
 * - 인증번호 6자리는 DB가 아니라 서버 메모리에 5분 동안 둔다. (서버를 다시 켜면 사라짐 → 다시 받으면 됨)
 * - 메일은 MailSender로 보낸다. 지금은 LogMailSender라 서버 로그에만 찍힌다.
 * - 가입 여부를 드러내지 않도록 요청 응답은 회원이 있든 없든 같다.
 * - 2026-11-25 T3: 이메일이 아이디가 되어 이메일만 받는다. 등록된 이메일(탈퇴 회원 제외)이면 보낸다.
 * - 화면은 [인증번호 확인](verify)으로 번호만 먼저 확인하고, 마지막 변경(confirm) 때 같은 번호를 한 번 더 보낸다.
 * - 아이디 찾기는 없앴다. (2026-11-24)
 * - 같은 번호로 5번 틀리면 그 번호는 지운다. (verify·confirm 합쳐서, 다시 받아야 함)
 * - 번호는 비밀번호를 바꾸면 지운다. (verify는 지우지 않음)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final int MAX_ATTEMPTS = 5;
    private static final int CODE_LENGTH = 6;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailSender mailSender;

    private final SecureRandom random = new SecureRandom();
    /** 소문자 이메일 → 발급한 인증번호 */
    private final Map<String, IssuedCode> codes = new ConcurrentHashMap<>();

    /**
     * 인증번호 요청: 등록된 이메일이면 번호를 만들어 메일로 보낸다. 다시 요청하면 앞 번호는 버린다.
     * 없는 이메일이어도 응답은 같다.
     */
    public PasswordResetRequestResponse request(PasswordResetRequest request) {
        String email = EmailPolicy.normalize(request.email());

        memberRepository.findByEmail(email)
                .filter(member -> !member.isWithdrawn())
                .ifPresent(member -> {
                    String code = newCode();
                    codes.put(email, new IssuedCode(code, member.getMemberId(), LocalDateTime.now().plus(CODE_TTL)));
                    mailSender.send(
                            email,
                            "[슥밥] 비밀번호 찾기 인증번호",
                            member.getNickname() + "님, 비밀번호 찾기 인증번호는 " + code + " 입니다.\n"
                                    + CODE_TTL.toMinutes() + "분 안에 화면에 입력해 주세요. 요청한 적이 없다면 이 메일은 무시해 주세요.");
                });

        // 없는 이메일도 같은 응답 (가입 여부를 숨김)
        return new PasswordResetRequestResponse(request.email(), CODE_TTL.toSeconds());
    }

    /**
     * 인증번호만 확인 (화면의 [인증번호 확인]). 맞아도 번호는 지우지 않는다 → 마지막 변경(confirm)에서 쓴다.
     * 틀리면 INVALID_RESET_CODE, 틀린 횟수는 confirm과 함께 센다.
     */
    public void verify(PasswordResetVerifyRequest request) {
        checkCode(EmailPolicy.normalize(request.email()), request.code());
    }

    /**
     * 인증번호 확인 + 새 비밀번호로 변경.
     * 비밀번호 규칙·확인값을 먼저 보고(번호 시도 횟수를 쓰지 않음), 그다음 인증번호를 확인한다.
     */
    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        String email = EmailPolicy.normalize(request.email());

        // 1. 새 비밀번호 규칙(아이디와 다르게), 확인값
        String loginId = email == null ? null
                : memberRepository.findByEmail(email).map(Member::getLoginId).orElse(null);
        PasswordPolicy.validate(request.newPassword(), loginId);
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 2. 인증번호
        IssuedCode issued = checkCode(email, request.code());
        codes.remove(email, issued);   // 한 번만 쓸 수 있다

        // 3. 비밀번호 변경
        Member member = memberRepository.findById(issued.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_CODE));
        member.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /** 발급한 번호와 맞는지 본다. 없음·만료·불일치는 모두 같은 오류, 5번 틀리면 번호를 지운다 */
    private IssuedCode checkCode(String email, String code) {
        IssuedCode issued = email == null ? null : codes.get(email);
        if (issued == null || issued.isExpired()) {
            if (email != null) {
                codes.remove(email);
            }
            throw new BusinessException(ErrorCode.INVALID_RESET_CODE);
        }
        if (!issued.matches(code)) {
            if (issued.failAndCount() >= MAX_ATTEMPTS) {
                codes.remove(email, issued);
            }
            throw new BusinessException(ErrorCode.INVALID_RESET_CODE);
        }
        return issued;
    }

    private String newCode() {
        int bound = (int) Math.pow(10, CODE_LENGTH);
        return String.format("%0" + CODE_LENGTH + "d", random.nextInt(bound));
    }

    /** 발급한 인증번호 1건 (틀린 횟수만 바뀐다) */
    static final class IssuedCode {
        private final String code;
        private final Long memberId;
        private final LocalDateTime expiresAt;
        private int failures;

        IssuedCode(String code, Long memberId, LocalDateTime expiresAt) {
            this.code = code;
            this.memberId = memberId;
            this.expiresAt = expiresAt;
        }

        Long memberId() {
            return memberId;
        }

        boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }

        /** 걸린 시간으로 번호를 짐작하지 못하게 항상 같은 시간에 비교 */
        boolean matches(String input) {
            return input != null && MessageDigest.isEqual(
                    code.getBytes(StandardCharsets.UTF_8), input.getBytes(StandardCharsets.UTF_8));
        }

        synchronized int failAndCount() {
            return ++failures;
        }
    }
}
