package com.sseukbap.restaurant.entity;

/**
 * 식당 카테고리. DB RESTAURANT.CATEGORY의 CHECK 제약과 같은 값이다.
 * 기준: docs/API_SPEC.md 공통 규칙 '코드 값'
 */
public enum RestaurantCategory {
    CAFETERIA,  // 구내식당 (초기 데이터)
    KOREAN,     // 한식
    CHINESE,    // 중식
    JAPANESE,   // 일식
    WESTERN,    // 양식
    SNACK,      // 분식
    CAFE,       // 카페 (2026-11-23 추가. 룰렛·랭킹 '전체'에서는 제외)
    ETC         // 기타
}
