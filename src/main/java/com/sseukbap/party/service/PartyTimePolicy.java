package com.sseukbap.party.service;

import com.sseukbap.common.enums.MealType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 식사팟 시간 규칙 모음 (DB·스프링 없이 동작하는 순수 계산)
 *
 * - 끼니별 허용 시간: 점심 11:00~13:30, 저녁 17:00~19:30, 30분 단위
 * - 기준 시간: 확정 시간(CONFIRMED_TIME)이 있으면 그것, 없으면 예정 시간(MEAL_TIME)
 * - 확정 취소·확정자 나가기: 기준 시간 30분 전까지 가능
 * - 식사 완료(FINISHED): 기준 시간이 지나면 스케줄러가 처리
 *
 * 현재 시각(now)은 밖에서 넘겨받는다. 그래야 테스트에서 시각을 마음대로 정할 수 있다.
 * 규칙 위반 시 예외를 던지지 않고 true/false만 돌려준다. 예외(BusinessException)는 Service에서 던진다.
 */
public final class PartyTimePolicy {

    /** 시간 선택 단위 (분) */
    public static final int TIME_STEP_MINUTES = 30;

    /** 확정 취소·나가기 마감: 기준 시간 몇 분 전까지 */
    public static final int DEADLINE_MINUTES_BEFORE = 30;

    /** 식사팟을 만들고 조회할 수 있는 날짜: 오늘 ~ 오늘 + 14일 (11/20 변경, 기존 당일만) */
    public static final int MAX_DAYS_AHEAD = 14;

    /**
     * 식사 날짜가 모집 가능한 범위(오늘 ~ 오늘+14일)인지
     * 예) 오늘 11/20 → 11/20 true, 12/4 true(14일 후), 12/5 false, 11/19 false
     */
    public static boolean isWithinDateRange(LocalDate partyDate, LocalDate today) {
        return !partyDate.isBefore(today) && !partyDate.isAfter(today.plusDays(MAX_DAYS_AHEAD));
    }

    private static final LocalTime LUNCH_START = LocalTime.of(11, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(13, 30);
    private static final LocalTime DINNER_START = LocalTime.of(17, 0);
    private static final LocalTime DINNER_END = LocalTime.of(19, 30);

    private PartyTimePolicy() {
        // 객체를 만들지 않고 PartyTimePolicy.메서드() 로 사용한다.
    }

    /**
     * 끼니에 맞는 시간인지 검사한다. (생성·일정 확정 시 INVALID_MEAL_TIME 판단용)
     * 예) LUNCH 12:30 → true, LUNCH 12:15 → false, LUNCH 17:00 → false
     */
    public static boolean isValidMealTime(MealType mealType, LocalTime time) {
        if (mealType == null || time == null) {
            return false;
        }
        boolean isOnStep = time.getMinute() % TIME_STEP_MINUTES == 0
                && time.getSecond() == 0
                && time.getNano() == 0;
        boolean isInRange = !time.isBefore(startOf(mealType)) && !time.isAfter(endOf(mealType));
        return isOnStep && isInRange;
    }

    /**
     * 식사 일시가 현재 시각보다 이후인지 (생성 시 지난 시간이면 INVALID_MEAL_TIME)
     * 예) 지금 11:40 → 12:00 true, 11:30 false, 11:40(같은 시각) false
     */
    public static boolean isAfterNow(LocalDate partyDate, LocalTime time, LocalDateTime now) {
        return partyDate.atTime(time).isAfter(now);
    }

    /** 끼니별 기본 시간 (점심 11:00, 저녁 17:00) */
    public static LocalTime getDefaultMealTime(MealType mealType) {
        return startOf(mealType);
    }

    /** 기준 시간 = 확정 시간이 있으면 확정 시간, 없으면 예정 시간 */
    public static LocalTime getBaseTime(LocalTime mealTime, LocalTime confirmedTime) {
        return confirmedTime != null ? confirmedTime : mealTime;
    }

    /**
     * 확정 취소·확정자 나가기가 아직 가능한지 (기준 시간 30분 전까지, 그 시각 정각 포함)
     * 예) 기준 12:00 → 11:30까지 true, 11:31부터 false
     */
    public static boolean isBeforeDeadline(LocalDate partyDate, LocalTime baseTime, LocalDateTime now) {
        return !now.isAfter(getDeadline(partyDate, baseTime));
    }

    /** 확정 취소·나가기 마감 일시 = 기준 시간 30분 전 (상세 응답의 cancelDeadline) */
    public static LocalDateTime getDeadline(LocalDate partyDate, LocalTime baseTime) {
        return partyDate.atTime(baseTime).minusMinutes(DEADLINE_MINUTES_BEFORE);
    }

    /**
     * 기준 시간이 지났는지 (스케줄러의 FINISHED 처리 판단용)
     * 예) 기준 12:00 → 12:00 정각은 false, 12:00 이후부터 true
     */
    public static boolean isMealTimePassed(LocalDate partyDate, LocalTime baseTime, LocalDateTime now) {
        return now.isAfter(partyDate.atTime(baseTime));
    }

    private static LocalTime startOf(MealType mealType) {
        return mealType == MealType.LUNCH ? LUNCH_START : DINNER_START;
    }

    private static LocalTime endOf(MealType mealType) {
        return mealType == MealType.LUNCH ? LUNCH_END : DINNER_END;
    }
}
