package com.sseukbap.review.entity;

/**
 * 리뷰 태그 분류. DB TAG.TAG_CATEGORY의 CHECK 제약과 같은 값이다.
 * MEAL_TYPE은 식사팟의 끼니 값(LUNCH/DINNER)과 별개다.
 */
public enum TagCategory {
    MEAL_TYPE,      // 식사 상황
    GROUP_TYPE,     // 인원 및 모임
    PRICE,          // 가격 및 가성비
    WAITING,        // 식사 시간 및 대기
    ACCESSIBILITY   // 접근성 및 편의시설
}
