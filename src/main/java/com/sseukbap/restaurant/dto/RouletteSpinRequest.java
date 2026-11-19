package com.sseukbap.restaurant.dto;

import java.util.List;

/**
 * 룰렛 실행 요청 (POST /api/restaurants/roulette/spin)
 * candidateIds: 휠에 그려진 후보 ID, 2~8개, 중복 없음 (검증은 RouletteService)
 */
public record RouletteSpinRequest(List<Long> candidateIds) {
}
