package com.sseukbap.auth.dto;

import com.sseukbap.member.entity.Member;

/**
 * POST /api/auth/login, GET /api/auth/me 응답 data
 * email: 등록한 회사 이메일, 없으면 null (2026-11-24, 계정 설정 화면에서 표시)
 */
public record MemberInfoResponse(Long memberId, String loginId, String nickname, String email) {

    public static MemberInfoResponse from(Member member) {
        return new MemberInfoResponse(member.getMemberId(), member.getLoginId(), member.getNickname(), member.getEmail());
    }
}
