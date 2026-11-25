package com.sseukbap.member.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.member.entity.Member;

import java.time.LocalDate;

/**
 * GET /api/members/{memberId}/profile 응답 (2026-11-25)
 * 식사팟 참여자·대화방에서 이름을 누르면 뜨는 프로필 카드용. 사내 서비스라 로그인한 회원끼리 회사 이메일을 보여 준다.
 */
public record MemberProfileResponse(
        Long memberId,
        String nickname,
        String loginId,
        String email,              // 등록 전이면 null
        String workplace,          // 지금은 한 곳뿐 (회원가입 화면의 근무지와 같음)
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate joinedAt
) {
    public static final String WORKPLACE = "멀티캠퍼스 세종대";

    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(
                member.getMemberId(),
                member.getNickname(),
                member.getLoginId(),
                member.getEmail(),
                WORKPLACE,
                member.getCreatedAt() == null ? null : member.getCreatedAt().toLocalDate()
        );
    }
}
