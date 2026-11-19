package com.sseukbap.restaurant.repository;

import com.sseukbap.restaurant.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;

/** 관심 식당 */
public interface InterestRepository extends JpaRepository<Interest, Long> {

    /** 식당의 관심 수 */
    long countByRestaurant_RestaurantId(Long restaurantId);

    /** 회원이 이 식당을 관심 등록했는지 */
    boolean existsByRestaurant_RestaurantIdAndMember_MemberId(Long restaurantId, Long memberId);
}
