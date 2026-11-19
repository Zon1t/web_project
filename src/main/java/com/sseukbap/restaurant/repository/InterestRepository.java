package com.sseukbap.restaurant.repository;

import com.sseukbap.restaurant.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 관심 식당 */
public interface InterestRepository extends JpaRepository<Interest, Long> {

    /** 회원의 이 식당 관심 1건 (관심 해제에서 사용) */
    Optional<Interest> findByRestaurant_RestaurantIdAndMember_MemberId(Long restaurantId, Long memberId);

    /** 식당의 관심 수 */
    long countByRestaurant_RestaurantId(Long restaurantId);

    /** 회원이 이 식당을 관심 등록했는지 */
    boolean existsByRestaurant_RestaurantIdAndMember_MemberId(Long restaurantId, Long memberId);
}
