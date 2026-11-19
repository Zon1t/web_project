package com.sseukbap.restaurant.dto;

import com.sseukbap.restaurant.repository.RestaurantListRow;

/** 랭킹 한 줄 (C 담당) */
public record RankingItemResponse(
        int rank,
        Long restaurantId,
        String name,
        String category,
        long thumbsCount30d,
        long reviewCount
) {
    public static RankingItemResponse of(int rank, RestaurantListRow row) {
        return new RankingItemResponse(
                rank,
                row.getRestaurantId(),
                row.getName(),
                row.getCategory(),
                row.getThumbsCount30d(),
                row.getReviewCount()
        );
    }
}
