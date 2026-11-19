package com.sseukbap.review.dto;

import java.util.List;

/**
 * 리뷰 목록 (페이징). 공통 페이징 응답 형식 {page, size, totalCount, hasNext, items}
 *
 * @param page 요청한 페이지 (1부터)
 */
public record ReviewListResponse(
        int page,
        int size,
        long totalCount,
        boolean hasNext,
        List<ReviewItemResponse> items
) {
}
