package com.sseukbap.member.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 리뷰 한 줄 (GET /api/users/me/reviews)
 * imageUrl은 사진이 없으면 null
 */
public record MyReviewResponse(
        Long reviewId,
        Long restaurantId,
        String restaurantName,
        String content,
        List<String> tags,
        String imageUrl,
        LocalDateTime createdAt
) {
}
