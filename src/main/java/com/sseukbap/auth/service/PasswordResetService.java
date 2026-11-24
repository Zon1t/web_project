package com.sseukbap.auth.service;

import com.sseukbap.auth.dto.FindIdRequest;
import com.sseukbap.auth.dto.FindIdResponse;
import com.sseukbap.auth.dto.PasswordResetConfirmRequest;
import com.sseukbap.auth.dto.PasswordResetRequest;
import com.sseukbap.auth.dto.PasswordResetRequestResponse;
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
 * - 아이디가 있는지 드러내지 않도록 요청 응답은 회원이 있든 없든 같다.
 * - 2026-11-24 (EMAIL 컬럼 추가 후): 입력한 이메일이 그 회원이 등록한 이메일과 같을 때만 보낸다.
 *   이메일을 등록하지 않은 회원·탈퇴한 회원에게는 보내지 않는다. (응답은 같음)
 * - 아이디 찾기도 여기서: 등록된 이메일이면 아이디를 메일로 보낸다.
 * - 같은 번호로 5번 틀리면 그 번호는 지운다. (다시 받아야 함)
 * - 번호는 한 번 쓰면 지운다.
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
    /** 소문자 아이디 → 발급한 인증번호 */
    private final Map<String, IssuedCode> codes = new ConcurrentHashMap<>();

    /**
     * 인증번호 요청: 아이디와 등록 이메일이 맞으면 번호를 만들어 메일로 보낸다. 다시 요청하면 앞 번호는 버린다.
     * 맞지 않아도 응답은 같다.
     */
    public PasswordResetRequestResponse request(PasswordResetRequest request) {
        String loginId = AuthService.normalizeLoginId(request.loginId());
        String email = EmailPolicy.normalize(request.email());

        memberRepository.findByLoginIdForLogin(loginId)
                .filter(member -> !member.isWithdrawn())
                .filter(member -> email != null && email.equals(member.getEmail()))
                .ifPresent(member -> {
                    String code = newCode();
                    codes.put(loginId, new IssuedCode(code, member.getMemberId(), LocalDateTime.now().plus(CODE_TTL)));
                    mailSender.send(
                            email,
                            "[슥밥] 비밀번호 찾기 인증번호",
                            member.getLoginId() + "님, 비밀번호 찾기 인증번호는 " + code + " 입니다.\n"
                                    + CODE_TTL.toMinutes() + "분 안에 화면에 입력해 주세요. 요청한 적이 없다면 이 메일은 무시해 주세요.");
                });

        // 없는 아이디, 이메일이 다른 경우도 같은 응답 (아이디·이메일이 맞는지 숨김)
        return new PasswordResetRequestResponse(request.email(), CODE_TTL.toSeconds());
    }

    /**
     * 인증번호 확인 + 새 비밀번호로 변경.
     * 비밀번호 규칙·확인값을 먼저 보고(번호 시도 횟수를 쓰지 않음), 그다음 인증번호를 확인한다.
     */
    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        String loginId = AuthService.normalizeLoginId(request.loginId());

        // 1. 새 비밀번호 규칙, 확인값
        PasswordPolicy.validate(request.newPassword(), loginId);
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 2. 인증번호 (없음·만료·불일치는 모두 같은 오류)
        IssuedCode issued = codes.get(loginId);
        if (issued == null || issued.isExpired()) {
            codes.remove(loginId);
            throw new BusinessException(ErrorCode.INVALID_RESET_CODE);
        }
        if (!issued.matches(request.code())) {
            if (issued.failAndCount() >= MAX_ATTEMPTS) {
                codes.remove(loginId, issued);
            }
            throw new BusinessException(ErrorCode.INVALID_RESET_CODE);
        }
        codes.remove(loginId, issued);   // 한 번만 쓸 수 있다

        // 3. 비밀번호 변경
        Member member = memberRepository.findById(issued.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_CODE));
        member.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * 아이디 찾기: 등록된 이메일이면 그 이메일로 아이디를 보낸다. (탈퇴한 회원 제외)
     * 등록되지 않은 이메일이어도 응답은 같다. (이메일 등록 여부를 숨김)
     */
    public FindIdResponse findId(FindIdRequest request) {
        String email = EmailPolicy.normalizeAndValidate(request.email());

        memberRepository.findByEmail(email)
                .filter(member -> !member.isWithdrawn())
                .ifPresent(member -> mailSender.send(
                        email,
                        "[슥밥] 아이디 찾기 안내",
                        "이 이메일로 가입한 슥밥 아이디는 " + member.getLoginId() + " 입니다.\n"
                                + "요청한 적이 없다면 이 메일은 무시해 주세요."));

        return new FindIdResponse(email);
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
