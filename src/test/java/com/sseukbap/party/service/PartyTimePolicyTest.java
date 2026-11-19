package com.sseukbap.party.service;

import com.sseukbap.party.entity.MealType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PartyTimePolicy 단위 테스트 (DB·스프링 없이 빠르게 실행된다)
 */
class PartyTimePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    @ParameterizedTest(name = "{0} {1} → {2}")
    @DisplayName("끼니별 허용 시간: 범위 안 + 30분 단위만 통과")
    @CsvSource({
            "LUNCH,  11:00, true",    // 점심 시작
            "LUNCH,  12:30, true",
            "LUNCH,  13:30, true",    // 점심 끝 (포함)
            "LUNCH,  10:30, false",   // 시작 전
            "LUNCH,  14:00, false",   // 끝난 뒤
            "LUNCH,  12:15, false",   // 30분 단위 아님
            "LUNCH,  17:00, false",   // 저녁 시간
            "DINNER, 17:00, true",
            "DINNER, 19:30, true",
            "DINNER, 16:30, false",
            "DINNER, 20:00, false",
            "DINNER, 18:45, false",
    })
    void isValidMealTime(MealType mealType, String time, boolean expected) {
        assertThat(PartyTimePolicy.isValidMealTime(mealType, LocalTime.parse(time))).isEqualTo(expected);
    }

    @Test
    @DisplayName("값이 없으면 허용 시간이 아니다")
    void isValidMealTime_null() {
        assertThat(PartyTimePolicy.isValidMealTime(null, LocalTime.of(12, 0))).isFalse();
        assertThat(PartyTimePolicy.isValidMealTime(MealType.LUNCH, null)).isFalse();
    }

    @Test
    @DisplayName("초가 붙은 시간은 허용하지 않는다")
    void isValidMealTime_withSeconds() {
        assertThat(PartyTimePolicy.isValidMealTime(MealType.LUNCH, LocalTime.of(12, 0, 1))).isFalse();
    }

    @ParameterizedTest(name = "현재 11:40, 식사 {0} → {1}")
    @DisplayName("생성: 식사 시간은 현재 시각 이후여야 한다 (같은 시각은 불가)")
    @CsvSource({
            "12:00, true",
            "11:30, false",
            "11:40, false",
    })
    void isAfterNow(String mealTime, boolean expected) {
        LocalDateTime now = TODAY.atTime(11, 40);
        assertThat(PartyTimePolicy.isAfterNow(TODAY, LocalTime.parse(mealTime), now)).isEqualTo(expected);
    }

    @Test
    @DisplayName("기본 시간: 점심 11:00, 저녁 17:00")
    void getDefaultMealTime() {
        assertThat(PartyTimePolicy.getDefaultMealTime(MealType.LUNCH)).isEqualTo(LocalTime.of(11, 0));
        assertThat(PartyTimePolicy.getDefaultMealTime(MealType.DINNER)).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    @DisplayName("기준 시간: 확정 시간이 있으면 확정 시간, 없으면 예정 시간")
    void getBaseTime() {
        LocalTime mealTime = LocalTime.of(11, 30);
        LocalTime confirmedTime = LocalTime.of(12, 0);

        assertThat(PartyTimePolicy.getBaseTime(mealTime, confirmedTime)).isEqualTo(confirmedTime);
        assertThat(PartyTimePolicy.getBaseTime(mealTime, null)).isEqualTo(mealTime);
    }

    @ParameterizedTest(name = "기준 12:00, 현재 {0} → {1}")
    @DisplayName("확정 취소·나가기: 기준 시간 30분 전(정각 포함)까지만 가능")
    @CsvSource({
            "10:00, true",
            "11:30, true",    // 딱 30분 전은 가능
            "11:31, false",
            "12:00, false",
            "13:00, false",
    })
    void isBeforeDeadline(String now, boolean expected) {
        LocalDateTime nowDateTime = TODAY.atTime(LocalTime.parse(now));
        assertThat(PartyTimePolicy.isBeforeDeadline(TODAY, LocalTime.of(12, 0), nowDateTime)).isEqualTo(expected);
    }

    @Test
    @DisplayName("확정 취소·나가기: 날짜가 지난 팟은 불가")
    void isBeforeDeadline_nextDay() {
        LocalDateTime nextMorning = TODAY.plusDays(1).atTime(9, 0);
        assertThat(PartyTimePolicy.isBeforeDeadline(TODAY, LocalTime.of(12, 0), nextMorning)).isFalse();
    }

    @ParameterizedTest(name = "기준 12:00, 현재 {0} → {1}")
    @DisplayName("식사 완료: 기준 시간이 지나야 true (정각은 아직 아님)")
    @CsvSource({
            "11:59, false",
            "12:00, false",
            "12:01, true",
            "18:00, true",
    })
    void isMealTimePassed(String now, boolean expected) {
        LocalDateTime nowDateTime = TODAY.atTime(LocalTime.parse(now));
        assertThat(PartyTimePolicy.isMealTimePassed(TODAY, LocalTime.of(12, 0), nowDateTime)).isEqualTo(expected);
    }

    @Test
    @DisplayName("식사 완료: 어제 팟은 오늘 아침에 지난 것으로 본다")
    void isMealTimePassed_yesterday() {
        LocalDateTime thisMorning = TODAY.atTime(0, 5);
        assertThat(PartyTimePolicy.isMealTimePassed(TODAY.minusDays(1), LocalTime.of(19, 30), thisMorning)).isTrue();
    }
}
