package com.sseukbap.auth.dto;

import com.sseukbap.member.entity.Member;

/** POST /api/auth/signup 응답 data (2026-11-25 T3: email 추가, loginId는 서버가 만든 내부 아이디) */
public record SignupResponse(Long memberId, String email, String loginId, String nickname) {

    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getMemberId(), member.getEmail(), member.getLoginId(), member.getNickname());
    }
}
