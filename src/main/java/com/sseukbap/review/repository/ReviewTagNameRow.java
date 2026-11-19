package com.sseukbap.review.repository;

/** 리뷰별 태그 이름 조회 결과 (ReviewTagRepository.findTagNames) */
public interface ReviewTagNameRow {

    Long getReviewId();

    String getTagName();
}
