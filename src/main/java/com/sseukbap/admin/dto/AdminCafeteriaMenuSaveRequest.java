package com.sseukbap.admin.dto;

import com.sseukbap.common.enums.MealType;

import java.time.LocalDate;
import java.util.List;

/**
 * POST /api/admin/cafeteria/menus 요청 (2026-11-24 A3). 같은 날짜·끼니의 메뉴를 이 목록으로 바꾼다.
 * menuNames: 최대 15개, 각 1~50자. 빈 목록이면 그 끼니 메뉴를 지운다. 검사는 Service에서 한다.
 */
public record AdminCafeteriaMenuSaveRequest(LocalDate menuDate, MealType mealType, List<String> menuNames) {
}
