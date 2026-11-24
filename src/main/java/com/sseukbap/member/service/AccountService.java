package com.sseukbap.member.service;

import com.sseukbap.auth.service.PasswordPolicy;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.dto.NicknameChangeRequest;
import com.sseukbap.member.dto.NicknameChangeResponse;
import com.sseukbap.member.dto.PasswordChangeRequest;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 설정: 비밀번호·닉네임 변경 (API_SPEC 2장, C1 2026-11-24)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 비밀번호 변경. 바꾼 뒤에도 지금 로그인(세션)은 그대로 유지한다.
     * 검사 순서: 현재 비밀번호 → 새 비밀번호 규칙 → 확인값 일치 → 지금 비밀번호와 다른지
     */
    @Transactional
    public void changePassword(Long memberId, PasswordChangeRequest request) {
        Member member = findMember(memberId);

        // 1. 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        // 2. 새 비밀번호 규칙 (회원가입과 같은 규칙)
        PasswordPolicy.validate(request.newPassword(), member.getLoginId());

        // 3. 새 비밀번호 확인
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 4. 지금 쓰는 비밀번호와 같으면 바꿀 필요가 없다
        if (passwordEncoder.matches(request.newPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지금 쓰는 비밀번호와 다른 비밀번호를 입력해 주세요.");
        }

        // 5. 암호화해서 저장 (변경 감지로 UPDATE)
        member.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * 닉네임 변경. 지금 닉네임과 같으면 아무것도 바꾸지 않고 그대로 돌려준다.
     * 다른 회원이 쓰는 닉네임이면 409 (동시에 바꿔서 UK 제약에 걸려도 409)
     */
    @Transactional
    public NicknameChangeResponse changeNickname(Long memberId, NicknameChangeRequest request) {
        Member member = findMember(memberId);
        String nickname = request.nickname();   // 앞뒤 공백 제거·길이 검증은 DTO에서 끝남

        if (nickname.equals(member.getNickname())) {
            return new NicknameChangeResponse(nickname);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        member.changeNickname(nickname);
        try {
            memberRepository.flush();   // UK_MEMBER_NICKNAME 위반을 여기서 잡기 위해 바로 반영
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        return new NicknameChangeResponse(nickname);
    }

    /** 세션의 회원이 DB에 없으면(탈퇴 등) 다시 로그인하게 401 */
    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
