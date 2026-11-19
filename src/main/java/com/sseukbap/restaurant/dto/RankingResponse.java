package com.sseukbap.restaurant.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/restaurants/rankings 응답 data (C 담당)
 * from·to는 집계 기간 (오늘 - 29일 ~ 오늘), 날짜 형식 YYYY-MM-DD
 */
public record RankingResponse(LocalDate from, LocalDate to, List<RankingItemResponse> rankings) {
}
