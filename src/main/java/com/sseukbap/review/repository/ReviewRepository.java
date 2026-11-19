package com.sseukbap.review.repository;

import com.sseukbap.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

/** 리뷰. 식당별 페이징 쿼리는 리뷰 기능 작업 때 추가한다. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 식당의 리뷰 수 */
    long countByRestaurant_RestaurantId(Long restaurantId);
}
