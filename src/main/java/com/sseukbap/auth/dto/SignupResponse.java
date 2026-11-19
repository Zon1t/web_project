package com.sseukbap.auth.dto;

import com.sseukbap.member.entity.Member;

/** POST /api/auth/signup 응답 data */
public record SignupResponse(Long memberId, String loginId, String nickname) {

    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getMemberId(), member.getLoginId(), member.getNickname());
    }
}
