package com.sseukbap.auth.dto;

import com.sseukbap.member.entity.Member;

/**
 * POST /api/auth/login, GET /api/auth/me 응답 data
 * email: 등록한 회사 이메일, 없으면 null (2026-11-24, 계정 설정 화면에서 표시)
 * admin: 관리자면 true (2026-11-24 A3, 헤더 메뉴의 '관리자' 항목 표시용. 실제 권한 검사는 서버 AdminCheckInterceptor)
 */
public record MemberInfoResponse(Long memberId, String loginId, String nickname, String email, boolean admin) {

    public static MemberInfoResponse from(Member member) {
        return new MemberInfoResponse(member.getMemberId(), member.getLoginId(), member.getNickname(), member.getEmail(),
                member.isAdmin());
    }
}
