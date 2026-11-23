package com.sseukbap.restaurant.repository;

import com.sseukbap.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 룰렛 조회 쿼리 (C 담당). B의 RestaurantRepository를 고치지 않으려고 따로 둔다.
 *
 * 구내식당(CAFETERIA)은 룰렛 후보에서 항상 제외한다. ('전체' 포함, API_SPEC 4장)
 */
public interface RouletteQueryRepository extends Repository<Restaurant, Long> {

    /**
     * 룰렛 대상 식당 수
     *
     * @param category 카테고리 값(예: "KOREAN"). null이면 전체
     */
    @Query(value = """
            SELECT COUNT(*)
              FROM RESTAURANT r
             WHERE r.CATEGORY <> 'CAFETERIA'
               AND (:category IS NULL OR r.CATEGORY = :category)
            """, nativeQuery = true)
    long countTargets(@Param("category") String category);

    /**
     * 룰렛 후보를 무작위로 최대 limit개 (Oracle 11g: DBMS_RANDOM 정렬 후 ROWNUM)
     *
     * @param category 카테고리 값(예: "KOREAN"). null이면 전체
     */
    @Query(value = """
            SELECT x."restaurantId", x."name"
              FROM (SELECT r.RESTAURANT_ID AS "restaurantId",
                           r.NAME          AS "name"
                      FROM RESTAURANT r
                     WHERE r.CATEGORY <> 'CAFETERIA'
                       AND (:category IS NULL OR r.CATEGORY = :category)
                     ORDER BY DBMS_RANDOM.VALUE) x
             WHERE ROWNUM <= :limit
            """, nativeQuery = true)
    List<RouletteCandidateRow> findRandomCandidates(@Param("category") String category,
                                                    @Param("limit") int limit);

    /**
     * 보낸 후보 ID 중 실제로 있는 식당.
     * 2026-11-23: 내 룰렛에서 구내식당도 고를 수 있게 구내식당 제외 조건을 뺐다. (카테고리 룰렛 후보 조회는 계속 제외)
     */
    @Query(value = """
            SELECT r.*
              FROM RESTAURANT r
             WHERE r.RESTAURANT_ID IN (:ids)
            """, nativeQuery = true)
    List<Restaurant> findSpinnable(@Param("ids") Collection<Long> ids);

    /** findRandomCandidates 결과 한 줄. 메서드 이름이 SQL 별칭과 같아야 한다 */
    interface RouletteCandidateRow {
        Long getRestaurantId();

        String getName();
    }
}
