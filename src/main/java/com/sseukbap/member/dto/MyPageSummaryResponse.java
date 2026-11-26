package com.sseukbap.member.dto;

/**
 * 마이페이지 프로필 + 활동 건수 (GET /api/users/me/summary)
 */
public record MyPageSummaryResponse(
        String nickname,
        String loginId,
        long reviewCount,
        long partyCount,
        long restaurantCount,
        long interestCount
) {
}
