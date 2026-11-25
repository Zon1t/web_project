package com.sseukbap.member.service;

import com.sseukbap.auth.service.AuthService;
import com.sseukbap.auth.service.CompanyEmailPolicy;
import com.sseukbap.auth.service.PasswordPolicy;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.dto.EmailChangeRequest;
import com.sseukbap.member.dto.EmailChangeResponse;
import com.sseukbap.member.dto.NicknameChangeRequest;
import com.sseukbap.member.dto.NicknameChangeResponse;
import com.sseukbap.member.dto.PasswordChangeRequest;
import com.sseukbap.member.dto.WithdrawRequest;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.party.service.PartyService;
import com.sseukbap.restaurant.repository.InterestRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 계정 설정 (API_SPEC 2장, C 2026-11-24)
 * - 비밀번호·닉네임 변경 (C1)
 * - 회사 이메일 등록·변경 (비밀번호·아이디 찾기용)
 * - 회원 탈퇴 (C2)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PartyService partyService;               // 탈퇴 시 식사팟 정리 (A2)
    private final InterestRepository interestRepository;   // 탈퇴 시 관심 삭제
    private final CompanyEmailPolicy companyEmailPolicy;   // 회사 도메인만 (2026-11-25 T3)

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
        AuthService.validateNicknameNotReserved(nickname);   // '탈퇴한회원…'은 쓸 수 없다
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

    /**
     * 회사 이메일 등록·변경. 소문자로 저장하고, 다른 회원이 쓰는 이메일이면 409.
     * 2026-11-25 T3: 이메일이 로그인 아이디가 되어 회사 도메인(@samsung.com)만 허용. 바꾸면 다음 로그인부터 새 이메일로.
     * 지금 이메일과 같으면 바꾸지 않고 그대로 돌려준다.
     */
    @Transactional
    public EmailChangeResponse changeEmail(Long memberId, EmailChangeRequest request) {
        Member member = findMember(memberId);
        String email = companyEmailPolicy.normalizeAndValidate(request.email());

        if (email.equals(member.getEmail())) {
            return new EmailChangeResponse(email);
        }
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        member.changeEmail(email);
        try {
            memberRepository.flush();   // UK_MEMBER_EMAIL 위반을 여기서 잡기 위해 바로 반영
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return new EmailChangeResponse(email);
    }

    /**
     * 회원 탈퇴 (C2). 세션 무효화는 Controller에서 한다.
     * 1) 비밀번호 확인 → 2) 식사팟 정리(방장 팟 삭제, 참여 팟 나가기 — A의 PartyService) → 3) 관심 삭제
     * → 4) 회원: STATUS=WITHDRAWN, 탈퇴 일시, 닉네임 '탈퇴한회원'+번호 (이메일은 남겨 재가입을 막는다, 2026-11-25 T3)
     * 리뷰·대화·등록한 식당·끝난 식사팟 기록은 남긴다. 하나라도 실패하면 모두 되돌린다(같은 트랜잭션).
     */
    @Transactional
    public void withdraw(Long memberId, WithdrawRequest request) {
        Member member = findMember(memberId);

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        PartyService.PartyWithdrawalResult parties = partyService.cleanUpForWithdrawal(memberId);
        int interests = interestRepository.deleteAllByMemberId(memberId);
        // 위 삭제 쿼리(clearAutomatically)가 영속성 컨텍스트를 비워 member가 준영속이 되므로 다시 읽어 와서 바꾼다.
        // (다시 안 읽으면 탈퇴 처리가 DB에 저장되지 않음 — 2026-11-24 브라우저 확인에서 발견)
        member = findMember(memberId);
        member.withdraw(LocalDateTime.now());

        log.info("회원 탈퇴: memberId={}, 삭제한 팟 {}개, 나간 팟 {}개, 관심 {}개 삭제",
                memberId, parties.deletedPartyCount(), parties.leftPartyCount(), interests);
    }

    /** 세션의 회원이 DB에 없거나 탈퇴했으면 다시 로그인하게 401 */
    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> !member.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
