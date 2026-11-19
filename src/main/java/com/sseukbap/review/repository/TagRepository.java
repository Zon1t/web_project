package com.sseukbap.review.repository;

import com.sseukbap.review.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

/** 리뷰 태그 (1~13 고정값, 조회만 한다). */
public interface TagRepository extends JpaRepository<Tag, Long> {
}
