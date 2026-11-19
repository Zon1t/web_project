package com.sseukbap.restaurant.repository;

/**
 * 식당 목록 native query 결과 한 줄 (RestaurantRepository.findListRows).
 * 메서드 이름이 SQL의 별칭("restaurantId" 등)과 같아야 값이 들어온다.
 */
public interface RestaurantListRow {

    Long getRestaurantId();

    String getName();

    String getCategory();

    Double getLat();

    Double getLng();

    Long getThumbsCount30d();

    Long getReviewCount();
}
