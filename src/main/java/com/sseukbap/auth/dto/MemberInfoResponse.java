package com.sseukbap.auth.dto;

import com.sseukbap.member.entity.Member;

/** POST /api/auth/login, GET /api/auth/me 응답 data */
public record MemberInfoResponse(Long memberId, String loginId, String nickname) {

    public static MemberInfoResponse from(Member member) {
        return new MemberInfoResponse(member.getMemberId(), member.getLoginId(), member.getNickname());
    }
}
