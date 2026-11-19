package com.sseukbap.restaurant.dto;

import com.sseukbap.review.entity.TagCategory;

/**
 * 식당 상세의 태그 TOP 3 항목 (topTags 배열의 원소)
 * ReviewTagRepository.findTopTags()가 JPQL로 바로 만들어 준다.
 */
public record TopTagResponse(
        Long tagId,
        String name,
        TagCategory category,
        long count
) {
}
