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

    /**
     * 여러 리뷰의 태그 이름을 한 번에 조회 (리뷰 목록 한 페이지분).
     * 리뷰마다 따로 조회하지 않기 위함. 정렬: 리뷰 → tagId 오름차순
     */
    @Query("""
            SELECT rt.review.reviewId AS reviewId, t.tagName AS tagName
              FROM ReviewTag rt
              JOIN rt.tag t
             WHERE rt.review.reviewId IN :reviewIds
             ORDER BY rt.review.reviewId, t.tagId
            """)
    List<ReviewTagNameRow> findTagNames(@Param("reviewIds") List<Long> reviewIds);

    /**
     * 리뷰의 태그 연결 삭제 (리뷰 삭제 전에 호출).
     * 일괄 DELETE 쿼리 대신 엔티티를 조회해서 하나씩 지운다. (리뷰 태그는 최대 13개)
     * 그래야 같은 트랜잭션에서 저장한 ReviewTag가 메모리(영속성 컨텍스트)에 남아 오류를 내지 않는다.
     */
    void deleteByReview_ReviewId(Long reviewId);
}
