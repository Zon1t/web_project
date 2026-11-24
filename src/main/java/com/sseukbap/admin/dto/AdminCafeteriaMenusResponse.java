package com.sseukbap.admin.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /api/admin/cafeteria/menus 응답 data: 그 날짜의 점심·저녁 메뉴 (2026-11-24 A3) */
public record AdminCafeteriaMenusResponse(LocalDate menuDate, List<String> lunch, List<String> dinner) {
}
