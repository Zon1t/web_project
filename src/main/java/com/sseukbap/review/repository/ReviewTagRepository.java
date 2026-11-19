package com.sseukbap.review.repository;

import com.sseukbap.review.entity.ReviewTag;
import org.springframework.data.jpa.repository.JpaRepository;

/** 리뷰-태그 연결. 태그 TOP 3 집계 쿼리는 태그 기능 작업 때 추가한다. */
public interface ReviewTagRepository extends JpaRepository<ReviewTag, Long> {
}
