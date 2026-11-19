package com.sseukbap.member.repository;

import com.sseukbap.member.entity.Member;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.restaurant.entity.Interest;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.review.entity.Review;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 마이페이지 조회 쿼리 모음 (C 담당).
 *
 * CONFIRMED_YN·THUMBS_UP_YN은 Y/N 변환 컬럼이라 true 리터럴 대신 :yes 파라미터(true)로 비교한다.
 *
 * 리뷰(B)·식당(B)·식사팟(A) 엔티티를 읽기만 한다. 다른 팀원의 Repository 파일을 고치지 않으려고
 * 마이페이지에서 쓰는 쿼리를 여기에 모았다. (Spring Data는 같은 엔티티를 여러 Repository에서 조회할 수 있다)
 *
 * 식사팟 조회는 모두 소프트 삭제(DELETED) 팟을 뺀다. (CLAUDE.md 6장)
 */
public interface MyPageQueryRepository extends Repository<Member, Long> {

    /* ---------- 활동 건수 (summary) ---------- */

    @Query("SELECT COUNT(r) FROM Review r WHERE r.member.memberId = :memberId")
    long countReviews(@Param("memberId") Long memberId);

    /** 참여한 식사팟 수 (삭제된 팟 제외, 나간 팟은 PARTY_MEMBER 행이 없어서 자동 제외) */
    @Query("""
            SELECT COUNT(pm) FROM PartyMember pm
             WHERE pm.member.memberId = :memberId
               AND pm.party.status <> :deleted
            """)
    long countParties(@Param("memberId") Long memberId, @Param("deleted") PartyStatus deleted);

    @Query("SELECT COUNT(r) FROM Restaurant r WHERE r.member.memberId = :memberId")
    long countRestaurants(@Param("memberId") Long memberId);

    @Query("SELECT COUNT(i) FROM Interest i WHERE i.member.memberId = :memberId")
    long countInterests(@Param("memberId") Long memberId);

    /* ---------- 탭별 목록 ---------- */

    /** 내 리뷰, 최신순. 식당 이름을 쓰므로 식당을 함께 가져온다 */
    @Query("""
            SELECT r FROM Review r
              JOIN FETCH r.restaurant
             WHERE r.member.memberId = :memberId
             ORDER BY r.createdAt DESC, r.reviewId DESC
            """)
    List<Review> findMyReviews(@Param("memberId") Long memberId);

    /**
     * 참여한 식사팟 (삭제된 팟 제외). 식사팟·식당을 함께 가져온다.
     * 기준 시간(확정 시간 → 예정 시간) 정렬은 Service에서 한다.
     */
    @Query("""
            SELECT pm FROM PartyMember pm
              JOIN FETCH pm.party p
              JOIN FETCH p.restaurant
             WHERE pm.member.memberId = :memberId
               AND p.status <> :deleted
             ORDER BY p.partyDate DESC, p.partyId DESC
            """)
    List<PartyMember> findMyParties(@Param("memberId") Long memberId, @Param("deleted") PartyStatus deleted);

    /** 내가 등록한 식당, 최신순 (구내식당 초기 데이터는 등록자가 없어서 자동 제외) */
    @Query("""
            SELECT r FROM Restaurant r
             WHERE r.member.memberId = :memberId
             ORDER BY r.createdAt DESC, r.restaurantId DESC
            """)
    List<Restaurant> findMyRestaurants(@Param("memberId") Long memberId);

    /** 관심 식당, 최근 등록순. 식당을 함께 가져온다 */
    @Query("""
            SELECT i FROM Interest i
              JOIN FETCH i.restaurant
             WHERE i.member.memberId = :memberId
             ORDER BY i.createdAt DESC, i.interestId DESC
            """)
    List<Interest> findMyInterests(@Param("memberId") Long memberId);

    /* ---------- 관심 식당 부가 정보 ---------- */

    /**
     * 식당별 최근 30일 따봉 수 (여러 식당 한 번에).
     * 기준은 RestaurantRepository.countThumbsUp(B)과 같다:
     *   THUMBS_UP_YN = 'Y', 삭제 팟 제외, PARTY_DATE가 fromDate 이상 toDateExclusive 미만
     * 따봉이 0건인 식당은 결과에 없다. (Service에서 0으로 채움)
     */
    @Query("""
            SELECT p.restaurant.restaurantId AS restaurantId, COUNT(pm) AS thumbsCount
              FROM PartyMember pm
              JOIN pm.party p
             WHERE pm.thumbsUp = :yes
               AND p.status <> :deleted
               AND p.partyDate >= :fromDate
               AND p.partyDate < :toDateExclusive
               AND p.restaurant.restaurantId IN :restaurantIds
             GROUP BY p.restaurant.restaurantId
            """)
    List<RestaurantThumbsRow> countThumbs(@Param("restaurantIds") Collection<Long> restaurantIds,
                                          @Param("yes") boolean yes,
                                          @Param("deleted") PartyStatus deleted,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDateExclusive") LocalDate toDateExclusive);

    /**
     * 방문 기록이 있는 식당 ID: 내가 참여 확정(CONFIRMED_YN = 'Y')한 식사 완료(FINISHED) 팟의 식당
     */
    @Query("""
            SELECT DISTINCT p.restaurant.restaurantId
              FROM PartyMember pm
              JOIN pm.party p
             WHERE pm.member.memberId = :memberId
               AND pm.confirmed = :yes
               AND p.status = :finished
               AND p.restaurant.restaurantId IN :restaurantIds
            """)
    List<Long> findVisitedRestaurantIds(@Param("memberId") Long memberId,
                                        @Param("restaurantIds") Collection<Long> restaurantIds,
                                        @Param("yes") boolean yes,
                                        @Param("finished") PartyStatus finished);

    /** countThumbs 결과 한 줄 */
    interface RestaurantThumbsRow {
        Long getRestaurantId();

        Long getThumbsCount();
    }
}
