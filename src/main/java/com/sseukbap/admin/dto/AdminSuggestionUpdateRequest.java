package com.sseukbap.admin.dto;

/** PATCH /api/admin/suggestions/{id} 요청: status = DONE / OPEN (2026-11-24 A3) */
public record AdminSuggestionUpdateRequest(String status) {
}
