package com.sseukbap.admin.repository;

import com.sseukbap.cafeteria.entity.CafeteriaMenu;
import com.sseukbap.common.enums.MealType;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.Suggestion;
import com.sseukbap.restaurant.entity.SuggestionStatus;
import com.sseukbap.review.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 관리자 화면 전용 조회 (2026-11-24 A3).
 * 다른 도메인 Repository를 고치지 않도록 관리자에게만 필요한 쿼리를 여기에 모았다.
 * 일반 화면과 달리 숨긴 식당·리뷰도 모두 보여 준다.
 */
public interface AdminQueryRepository extends Repository<Restaurant, Long> {

    /** 전체 식당 (숨긴 식당 포함) + 등록자, 등록 순 */
    @Query("select r from Restaurant r left join fetch r.member order by r.restaurantId")
    List<Restaurant> findAllRestaurants();

    /** 식당 1곳 (숨긴 식당 포함) + 등록자 */
    @Query("select r from Restaurant r left join fetch r.member where r.restaurantId = :id")
    Optional<Restaurant> findRestaurant(@Param("id") Long restaurantId);

    /** 식당별 숨기지 않은 리뷰 수. 한 줄 = [restaurantId, count] */
    @Query("select r.restaurant.restaurantId, count(r) from Review r where r.hidden = false group by r.restaurant.restaurantId")
    List<Object[]> countVisibleReviewsByRestaurant();

    /** 식당 1곳의 숨기지 않은 리뷰 수 */
    @Query("select count(r) from Review r where r.restaurant.restaurantId = :id and r.hidden = false")
    long countVisibleReviews(@Param("id") Long restaurantId);

    /** 수정 제안 (식당·작성자 포함), 최신순 */
    @Query("select s from Suggestion s join fetch s.restaurant join fetch s.member "
            + "where s.status in :statuses order by s.createdAt desc, s.suggestionId desc")
    List<Suggestion> findSuggestions(@Param("statuses") Collection<SuggestionStatus> statuses);

    @Query("select s from Suggestion s join fetch s.restaurant join fetch s.member where s.suggestionId = :id")
    Optional<Suggestion> findSuggestion(@Param("id") Long suggestionId);

    /**
     * 최근 리뷰 (숨긴 리뷰 포함). keyword가 null이면 전체, 있으면 내용·식당 이름·작성자 닉네임 부분 일치.
     * 개수는 pageable로 제한한다.
     */
    @Query("select r from Review r join fetch r.restaurant join fetch r.member "
            + "where (:keyword is null or r.content like concat('%', :keyword, '%') "
            + "or r.restaurant.name like concat('%', :keyword, '%') "
            + "or r.member.nickname like concat('%', :keyword, '%')) "
            + "order by r.createdAt desc, r.reviewId desc")
    List<Review> findRecentReviews(@Param("keyword") String keyword, Pageable pageable);

    @Query("select r from Review r join fetch r.restaurant join fetch r.member where r.reviewId = :id")
    Optional<Review> findReview(@Param("id") Long reviewId);

    /** 하루치 구내식당 메뉴, 저장 순 */
    @Query("select m from CafeteriaMenu m where m.menuDate = :date order by m.menuId")
    List<CafeteriaMenu> findMenus(@Param("date") LocalDate menuDate);

    /** 날짜·끼니의 메뉴 전부 삭제 (관리자 메뉴 교체) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CafeteriaMenu m where m.menuDate = :date and m.mealType = :mealType")
    int deleteMenus(@Param("date") LocalDate menuDate, @Param("mealType") MealType mealType);
}
