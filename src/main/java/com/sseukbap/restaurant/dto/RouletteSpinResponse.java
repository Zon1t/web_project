package com.sseukbap.restaurant.dto;

/**
 * 룰렛 실행 결과 (POST /api/restaurants/roulette/spin)
 * FE는 winnerId 칸에서 멈추도록 회전 연출 후 winner로 결과 카드를 그린다.
 */
public record RouletteSpinResponse(Long winnerId, RouletteWinnerResponse winner) {
}
