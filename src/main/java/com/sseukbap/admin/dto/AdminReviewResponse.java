package com.sseukbap.admin.dto;

import com.sseukbap.review.entity.Review;

import java.time.LocalDateTime;

/** 관리자 리뷰 목록의 한 줄 (숨긴 리뷰 포함, 2026-11-24 A3) */
public record AdminReviewResponse(
        Long reviewId,
        Long restaurantId,
        String restaurantName,
        String nickname,
        String content,
        boolean hasImage,
        boolean hidden,
        LocalDateTime createdAt
) {

    public static AdminReviewResponse from(Review r) {
        return new AdminReviewResponse(
                r.getReviewId(), r.getRestaurant().getRestaurantId(), r.getRestaurant().getName(),
                r.getMember().getNickname(), r.getContent(), r.getImagePath() != null,
                r.isHidden(), r.getCreatedAt());
    }
}
