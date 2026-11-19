package com.sseukbap.review.repository;

import com.sseukbap.restaurant.dto.TopTagResponse;
import com.sseukbap.review.entity.ReviewTag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 리뷰-태그 연결 */
public interface ReviewTagRepository extends JpaRepository<ReviewTag, Long> {

    /**
     * 식당 리뷰들에서 많이 선택된 태그 (식당 상세의 TOP 3)
     * 13개 태그 전체 대상, 정렬: 선택 수 내림차순 → tagId 오름차순. 선택된 적 없는 태그는 나오지 않는다.
     * 개수는 pageable로 제한한다. (PageRequest.of(0, 3))
     */
    @Query("""
            SELECT new com.sseukbap.restaurant.dto.TopTagResponse(t.tagId, t.tagName, t.tagCategory, COUNT(rt))
              FROM ReviewTag rt
              JOIN rt.tag t
              JOIN rt.review r
             WHERE r.restaurant.restaurantId = :restaurantId
             GROUP BY t.tagId, t.tagName, t.tagCategory
             ORDER BY COUNT(rt) DESC, t.tagId ASC
            """)
    List<TopTagResponse> findTopTags(@Param("restaurantId") Long restaurantId, Pageable pageable);
}
