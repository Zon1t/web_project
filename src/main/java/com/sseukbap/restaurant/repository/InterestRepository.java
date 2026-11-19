package com.sseukbap.restaurant.repository;

import com.sseukbap.restaurant.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;

/** 관심 식당. 등록 여부·관심 수 쿼리는 관심 기능 작업 때 추가한다. */
public interface InterestRepository extends JpaRepository<Interest, Long> {
}
