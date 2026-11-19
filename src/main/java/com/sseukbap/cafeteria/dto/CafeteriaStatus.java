package com.sseukbap.cafeteria.dto;

/**
 * 오늘의 구내식당 메뉴 상태 (API_SPEC: GET /api/cafeteria/menus/today)
 * - OPEN: 메뉴 있음
 * - CLOSED: 미운영일 → 화면 '운영하지 않습니다'
 * - UNAVAILABLE: 수집 실패 → 화면 '메뉴 정보를 불러올 수 없습니다'
 */
public enum CafeteriaStatus {
    OPEN,
    CLOSED,
    UNAVAILABLE
}
