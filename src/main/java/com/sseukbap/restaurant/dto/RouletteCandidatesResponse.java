package com.sseukbap.restaurant.dto;

import java.util.List;

/**
 * 룰렛 후보 조회 (GET /api/restaurants/roulette/candidates)
 * category: 요청한 카테고리, 보내지 않았으면 null(전체)
 * spinnable: 후보가 2곳 이상이면 true
 */
public record RouletteCandidatesResponse(
        String category,
        long totalCount,
        List<RouletteCandidateResponse> candidates,
        boolean spinnable
) {
}
