package com.sseukbap.review.repository;

import com.sseukbap.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 리뷰 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 식당의 리뷰 수 */
    long countByRestaurant_RestaurantId(Long restaurantId);

    /** 식당의 리뷰 수 (관리자가 숨긴 리뷰 제외, 2026-11-24 A3). 식당 상세의 리뷰 수 */
    long countByRestaurant_RestaurantIdAndHiddenFalse(Long restaurantId);

    /**
     * 식당의 리뷰 목록 (페이징). 작성자 닉네임을 쓰므로 회원을 함께 가져온다(JOIN FETCH).
     * 정렬: 작성일시 내림차순 → reviewId 내림차순 (최신순)
     */
    @Query(value = """
            SELECT r FROM Review r
              JOIN FETCH r.member
             WHERE r.restaurant.restaurantId = :restaurantId
               AND r.hidden = false
             ORDER BY r.createdAt DESC, r.reviewId DESC
            """,
            countQuery = """
            SELECT COUNT(r) FROM Review r
             WHERE r.restaurant.restaurantId = :restaurantId
               AND r.hidden = false
            """)
    Page<Review> findPageByRestaurantId(@Param("restaurantId") Long restaurantId, Pageable pageable);
}
