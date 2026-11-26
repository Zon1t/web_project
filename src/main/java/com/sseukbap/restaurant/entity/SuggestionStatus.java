package com.sseukbap.restaurant.entity;

/**
 * 수정 제안 처리 상태 (SUGGESTION.STATUS). 기준: docs/sql/08_account_admin.sql
 */
public enum SuggestionStatus {
    OPEN,    // 접수 (처리 전)
    DONE     // 관리자 처리 완료
}
