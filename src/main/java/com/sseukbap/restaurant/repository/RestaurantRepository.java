package com.sseukbap.restaurant.repository;

import com.sseukbap.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

/** 식당. 목록·자동완성·중복 확인 쿼리는 기능 작업 때 추가한다. */
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
}
