package com.sseukbap.restaurant.repository;

import com.sseukbap.restaurant.entity.Restaurant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 식당.
 *
 * 따봉 집계 기준 (docs/API_SPEC.md 공통 규칙 '따봉 집계')
 *   PARTY_MEMBER.THUMBS_UP_YN = 'Y' 와 MEAL_PARTY를 조인해
 *   MEAL_PARTY.PARTY_DATE가 fromDate 이상, toDateExclusive 미만인 행을 식당별로 센다.
 *   소프트 삭제된 식사팟(STATUS = 'DELETED')은 제외한다.
 *   최근 30일(오늘 포함) = fromDate: 오늘 - 29일, toDateExclusive: 오늘 + 1일
 *
 * 검색어(keyword)의 %, _ 는 Service에서 '\'로 이스케이프해서 넘긴다. (문자 그대로 검색되도록)
 */
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    /**
     * 식당 목록 (검색·필터) + 식당별 최근 30일 따봉 수, 리뷰 수.
     * 정렬: 따봉 수 내림차순 → 리뷰 수 내림차순 → 등록 순(RESTAURANT_ID 오름차순)
     *
     * @param keyword  이름 부분 일치. null이면 조건 없음 (Oracle은 빈 문자열도 NULL로 본다)
     * @param category 카테고리 값(예: "KOREAN"). null이면 전체
     */
    @Query(value = """
            SELECT r.RESTAURANT_ID       AS "restaurantId",
                   r.NAME                AS "name",
                   r.CATEGORY            AS "category",
                   r.LAT                 AS "lat",
                   r.LNG                 AS "lng",
                   NVL(t.THUMBS_CNT, 0)  AS "thumbsCount30d",
                   NVL(v.REVIEW_CNT, 0)  AS "reviewCount"
              FROM RESTAURANT r
              LEFT JOIN (SELECT mp.RESTAURANT_ID, COUNT(*) AS THUMBS_CNT
                           FROM PARTY_MEMBER pm
                           JOIN MEAL_PARTY mp ON mp.PARTY_ID = pm.PARTY_ID
                          WHERE pm.THUMBS_UP_YN = 'Y'
                            AND mp.STATUS <> 'DELETED'
                            AND mp.PARTY_DATE >= :fromDate
                            AND mp.PARTY_DATE <  :toDateExclusive
                          GROUP BY mp.RESTAURANT_ID) t
                ON t.RESTAURANT_ID = r.RESTAURANT_ID
              LEFT JOIN (SELECT RESTAURANT_ID, COUNT(*) AS REVIEW_CNT
                           FROM REVIEW
                          GROUP BY RESTAURANT_ID) v
                ON v.RESTAURANT_ID = r.RESTAURANT_ID
             WHERE (:keyword IS NULL OR r.NAME LIKE '%' || :keyword || '%' ESCAPE '\\')
               AND (:category IS NULL OR r.CATEGORY = :category)
             ORDER BY "thumbsCount30d" DESC, "reviewCount" DESC, r.RESTAURANT_ID ASC
            """, nativeQuery = true)
    List<RestaurantListRow> findListRows(@Param("keyword") String keyword,
                                         @Param("category") String category,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDateExclusive") LocalDate toDateExclusive);

    /**
     * 식당 1곳의 최근 30일 따봉 수 (식당 상세, 랭킹·룰렛에서도 재사용 가능)
     */
    @Query(value = """
            SELECT COUNT(*)
              FROM PARTY_MEMBER pm
              JOIN MEAL_PARTY mp ON mp.PARTY_ID = pm.PARTY_ID
             WHERE mp.RESTAURANT_ID = :restaurantId
               AND pm.THUMBS_UP_YN = 'Y'
               AND mp.STATUS <> 'DELETED'
               AND mp.PARTY_DATE >= :fromDate
               AND mp.PARTY_DATE <  :toDateExclusive
            """, nativeQuery = true)
    long countThumbsUp(@Param("restaurantId") Long restaurantId,
                       @Param("fromDate") LocalDate fromDate,
                       @Param("toDateExclusive") LocalDate toDateExclusive);

    /**
     * 자동완성: 이름에 keyword가 들어간 식당.
     * 정렬: keyword로 시작하는 이름 우선 → 이름 가나다순 → 등록 순. 개수는 pageable로 제한 (최대 5개)
     */
    @Query("""
            SELECT r FROM Restaurant r
             WHERE r.name LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'
             ORDER BY CASE WHEN r.name LIKE CONCAT(:keyword, '%') ESCAPE '\\' THEN 0 ELSE 1 END,
                      r.name ASC,
                      r.restaurantId ASC
            """)
    List<Restaurant> findForAutocomplete(@Param("keyword") String keyword, Pageable pageable);
}
