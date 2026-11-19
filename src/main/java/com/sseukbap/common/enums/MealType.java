package com.sseukbap.common.enums;

/**
 * 끼니. 여러 도메인이 같이 쓰는 공용 값.
 * - 식사팟: MEAL_PARTY.MEAL_TYPE (시간 범위 규칙은 party.service.PartyTimePolicy)
 * - 구내식당: CAFETERIA_MENU.MEAL_TYPE
 *
 * 값을 바꾸면 DB CHECK 제약(LUNCH, DINNER)과 API 명세도 같이 바꿔야 한다.
 */
public enum MealType {
    LUNCH,
    DINNER
}
