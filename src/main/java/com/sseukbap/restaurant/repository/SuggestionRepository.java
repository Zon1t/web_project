package com.sseukbap.restaurant.repository;

import com.sseukbap.restaurant.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;

/** 식당 정보 수정 제안. 저장만 한다. */
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {
}
