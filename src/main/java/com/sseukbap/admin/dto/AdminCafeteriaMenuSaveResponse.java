package com.sseukbap.admin.dto;

import com.sseukbap.common.enums.MealType;

import java.time.LocalDate;
import java.util.List;

/** POST /api/admin/cafeteria/menus 응답 data: 저장된 메뉴 (2026-11-24 A3) */
public record AdminCafeteriaMenuSaveResponse(LocalDate menuDate, MealType mealType, List<String> menuNames) {
}
