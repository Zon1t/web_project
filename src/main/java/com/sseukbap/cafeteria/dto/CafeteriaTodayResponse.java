package com.sseukbap.cafeteria.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/cafeteria/menus/today 응답 data
 * 한 끼만 있거나 메뉴가 없으면 해당 끼니는 빈 배열
 */
public record CafeteriaTodayResponse(
        LocalDate date,
        String cafeteriaName,
        CafeteriaStatus status,
        List<String> lunch,
        List<String> dinner
) {
    public static final String CAFETERIA_NAME = "세종대 계절밥상";

    public static CafeteriaTodayResponse open(LocalDate date, List<String> lunch, List<String> dinner) {
        return new CafeteriaTodayResponse(date, CAFETERIA_NAME, CafeteriaStatus.OPEN, lunch, dinner);
    }

    /** CLOSED·UNAVAILABLE: 메뉴 없이 상태만 */
    public static CafeteriaTodayResponse empty(LocalDate date, CafeteriaStatus status) {
        return new CafeteriaTodayResponse(date, CAFETERIA_NAME, status, List.of(), List.of());
    }
}
