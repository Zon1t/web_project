package com.sseukbap.member.dto;

import com.sseukbap.restaurant.entity.RestaurantCategory;

import java.time.LocalDateTime;

/**
 * 관심 식당 한 줄 (GET /api/users/me/interests)
 * visited: 내가 참여 확정한 식사 완료(FINISHED) 팟이 이 식당에 있으면 true
 */
public record MyInterestResponse(
        Long restaurantId,
        String name,
        RestaurantCategory category,
        long thumbsCount30d,
        boolean visited,
        LocalDateTime interestedAt
) {
}
