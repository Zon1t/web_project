package com.sseukbap.auth.service;

import com.sseukbap.auth.dto.CheckIdResponse;
import com.sseukbap.auth.dto.CheckNicknameResponse;
import com.sseukbap.auth.dto.LoginRequest;
import com.sseukbap.auth.dto.MemberInfoResponse;
import com.sseukbap.auth.dto.SignupRequest;
import com.sseukbap.auth.dto.SignupResponse;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,20}$");
    private static final int NICKNAME_MIN = 2;
    private static final int NICKNAME_MAX = 10;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;

    /**
     * 닉네임으로 쓸 수 없는 값인지: 탈퇴한 회원 표시('탈퇴한회원…')로 시작하면 안 된다. (2026-11-24)
     * 회원가입·닉네임 중복 확인·닉네임 변경이 같이 쓴다.
     */
    public static void validateNicknameNotReserved(String nickname) {
        if (nickname != null && nickname.startsWith(Member.WITHDRAWN_NICKNAME_PREFIX)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "사용할 수 없는 닉네임입니다.");
        }
    }

    /**
     * 아이디를 앞뒤 공백 제거 + 소문자로 통일한다. (2026-11-24)
     * 가입·로그인·중복 확인이 모두 이 값으로 비교한다.
     */
    public static String normalizeLoginId(String loginId) {
        return (loginId == null) ? null : loginId.trim().toLowerCase(Locale.ROOT);
    }

    /** 아이디 중복 확인 (대소문자 무시) */
    public CheckIdResponse checkLoginId(String loginId) {
        // 1. 소문자로 통일한 뒤 형식 검증 (영문·숫자 4~20자)
        String normalized = normalizeLoginId(loginId);
        if (normalized == null || !LOGIN_ID_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "아이디는 영문·숫자 4~20자로 입력해 주세요.");
        }
        // 2~3. 대소문자만 다른 아이디도 없으면 사용 가능
        boolean available = !memberRepository.existsByLoginIdIgnoreCase(normalized);
        return new CheckIdResponse(normalized, available);
    }

    /** 닉네임 중복 확인 (앞뒤 공백 제거 후 비교, 대소문자 구분) */
    public CheckNicknameResponse checkNickname(String nickname) {
        // 1. 앞뒤 공백 제거 후 길이 검증
        String trimmed = (nickname == null) ? null : nickname.trim();
        if (trimmed == null || trimmed.length() < NICKNAME_MIN || trimmed.length() > NICKNAME_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "닉네임은 2~10자로 입력해 주세요.");
        }
        validateNicknameNotReserved(trimmed);
        // 2~3. 같은 닉네임이 없으면 사용 가능
        boolean available = !memberRepository.existsByNickname(trimmed);
        return new CheckNicknameResponse(trimmed, available);
    }

    /** 회원가입 (자동 로그인 없음) */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 1. 필수값·형식·길이 검증은 SignupRequest의 @Valid에서 처리됨

        //    (아이디는 SignupRequest에서 이미 소문자로 통일됨)

        // 2. 비밀번호 규칙 (8~20자, 영문+숫자, 공백 불가, 아이디와 다르게) — 2026-11-24
        PasswordPolicy.validate(request.password(), request.loginId());

        // 3. 비밀번호 확인
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 4~5. 화면의 중복 확인과 관계없이 서버에서 다시 검사 (아이디는 대소문자 무시)
        if (memberRepository.existsByLoginIdIgnoreCase(request.loginId())) {
            throw new BusinessException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }
        validateNicknameNotReserved(request.nickname());
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        // 5-1. 회사 이메일 (선택): 있으면 형식·중복 확인 후 소문자로 저장 (2026-11-24)
        String email = null;
        if (request.email() != null && !request.email().isBlank()) {
            email = EmailPolicy.normalizeAndValidate(request.email());
            if (memberRepository.existsByEmail(email)) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
        }

        // 5. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 6. 저장. 동시 가입으로 UK 제약 위반이 나면 해당 409로 응답
        Member member = Member.create(request.loginId(), encodedPassword, request.nickname());
        member.changeEmail(email);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw toDuplicateException(e);
        }

        // 7. 생성된 회원 정보 반환
        return SignupResponse.from(member);
    }

    /**
     * 로그인: 아이디·비밀번호 확인. 세션 처리는 Controller에서 한다.
     * 아이디가 없든, 탈퇴했든, 비밀번호가 틀리든 같은 오류(LOGIN_FAILED)로 응답한다.
     * 같은 아이디로 5번 연속 실패하면 5분 동안 429 TOO_MANY_LOGIN_ATTEMPTS (2026-11-24, C2)
     */
    public MemberInfoResponse login(LoginRequest request) {
        // 1. 필수값 검증은 LoginRequest의 @Valid에서 처리됨
        String loginId = normalizeLoginId(request.loginId());

        // 2. 잠겨 있으면 비밀번호를 보지 않고 거절
        loginAttemptLimiter.checkNotLocked(loginId);

        // 3. 아이디로 회원 조회 (대소문자 무시). 없거나 탈퇴한 회원이면 실패
        Member member = memberRepository.findByLoginIdForLogin(loginId)
                .filter(m -> !m.isWithdrawn())
                .orElse(null);

        // 4. 입력 비밀번호와 저장된 암호화 비밀번호 비교
        if (member == null || !passwordEncoder.matches(request.password(), member.getPassword())) {
            loginAttemptLimiter.recordFailure(loginId);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        loginAttemptLimiter.reset(loginId);
        return MemberInfoResponse.from(member);
    }

    /**
     * 로그인 사용자 조회. 세션의 회원이 DB에 없으면(탈퇴 등) empty를 돌려준다.
     * → Controller가 세션을 무효화하고 401로 응답한다.
     */
    public Optional<MemberInfoResponse> getMe(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> !member.isWithdrawn())   // 탈퇴한 회원도 없는 것처럼 401 (2026-11-24)
                .map(MemberInfoResponse::from);
    }

    // Oracle 오류 메시지 예: ORA-00001: unique constraint (계정.UK_MEMBER_LOGIN_ID) violated
    private RuntimeException toDuplicateException(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains("UK_MEMBER_LOGIN_ID")) {
            return new BusinessException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }
        if (message.contains("UK_MEMBER_NICKNAME")) {
            return new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        if (message.contains("UK_MEMBER_EMAIL")) {
            return new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return e;   // 그 외 제약 위반은 500으로 처리
    }
}
