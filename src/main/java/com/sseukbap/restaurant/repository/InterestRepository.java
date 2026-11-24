package com.sseukbap.restaurant.repository;

import com.sseukbap.restaurant.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 관심 식당 */
public interface InterestRepository extends JpaRepository<Interest, Long> {

    /** 회원의 이 식당 관심 1건 (관심 해제에서 사용) */
    Optional<Interest> findByRestaurant_RestaurantIdAndMember_MemberId(Long restaurantId, Long memberId);

    /** 식당의 관심 수 */
    long countByRestaurant_RestaurantId(Long restaurantId);

    /** 회원이 이 식당을 관심 등록했는지 */
    boolean existsByRestaurant_RestaurantIdAndMember_MemberId(Long restaurantId, Long memberId);

    /** 회원 탈퇴 시 그 회원의 관심 전체 삭제 (C2, 2026-11-24). 지운 건수 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Interest i where i.member.memberId = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);
}
