package com.sseukbap.review.dto;

/** 리뷰 작성 응답. FE는 이 응답을 받은 뒤 리뷰 목록 1페이지를 다시 조회한다. */
public record ReviewCreateResponse(
        Long reviewId
) {
}
