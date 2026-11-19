package com.sseukbap.auth.service;

import com.sseukbap.auth.dto.CheckIdResponse;
import com.sseukbap.auth.dto.CheckNicknameResponse;
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

    /** 아이디 중복 확인 */
    public CheckIdResponse checkLoginId(String loginId) {
        // 1. 형식 검증 (영문·숫자 4~20자)
        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "아이디는 영문·숫자 4~20자로 입력해 주세요.");
        }
        // 2~3. 같은 아이디가 없으면 사용 가능
        boolean available = !memberRepository.existsByLoginId(loginId);
        return new CheckIdResponse(loginId, available);
    }

    /** 닉네임 중복 확인 (앞뒤 공백 제거 후 비교, 대소문자 구분) */
    public CheckNicknameResponse checkNickname(String nickname) {
        // 1. 앞뒤 공백 제거 후 길이 검증
        String trimmed = (nickname == null) ? null : nickname.trim();
        if (trimmed == null || trimmed.length() < NICKNAME_MIN || trimmed.length() > NICKNAME_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "닉네임은 2~10자로 입력해 주세요.");
        }
        // 2~3. 같은 닉네임이 없으면 사용 가능
        boolean available = !memberRepository.existsByNickname(trimmed);
        return new CheckNicknameResponse(trimmed, available);
    }

    /** 회원가입 (자동 로그인 없음) */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 1. 필수값·형식·길이 검증은 SignupRequest의 @Valid에서 처리됨

        // 2. 비밀번호 확인
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 3~4. 화면의 중복 확인과 관계없이 서버에서 다시 검사
        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        // 5. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 6. 저장. 동시 가입으로 UK 제약 위반이 나면 해당 409로 응답
        Member member = Member.create(request.loginId(), encodedPassword, request.nickname());
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw toDuplicateException(e);
        }

        // 7. 생성된 회원 정보 반환
        return SignupResponse.from(member);
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
        return e;   // 그 외 제약 위반은 500으로 처리
    }
}
