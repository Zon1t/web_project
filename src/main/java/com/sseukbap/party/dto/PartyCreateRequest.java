package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.common.enums.MealType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * POST /api/parties/create 요청.
 * 검증 실패 시 문구는 API_SPEC대로 "입력값을 확인해 주세요." (400 INVALID_REQUEST)
 * 날짜 범위(오늘~14일 후)는 Service에서 검사한다. (400 INVALID_REQUEST)
 * 시간대·30분 단위·지난 시간 검사도 Service에서 한다. (400 INVALID_MEAL_TIME)
 * mealType에 LUNCH·DINNER 외 값, mealTime에 'HH:mm'이 아닌 값, partyDate에 'YYYY-MM-DD'가 아닌 값이 오면
 * JSON 해석 단계에서 400 INVALID_REQUEST
 */
public record PartyCreateRequest(

        @NotNull(message = "입력값을 확인해 주세요.")
        Long restaurantId,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate partyDate,       // 선택. 없으면 오늘 (11/20 추가)

        @NotNull(message = "입력값을 확인해 주세요.")
        MealType mealType,

        @NotNull(message = "입력값을 확인해 주세요.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime mealTime,

        @NotNull(message = "입력값을 확인해 주세요.")
        @Min(value = 2, message = "입력값을 확인해 주세요.")
        @Max(value = 12, message = "입력값을 확인해 주세요.")
        Integer capacity,

        @Size(max = 50, message = "입력값을 확인해 주세요.")
        String memo,

        Boolean isPrivate          // 선택. true면 비공개 식사팟 (11/25 추가). 없으면 false
) {
    /** 한마디는 앞뒤 공백을 지우고, 비어 있으면 null로 저장한다. (검증보다 먼저 실행됨) */
    public PartyCreateRequest {
        if (memo != null) {
            memo = memo.trim();
            if (memo.isEmpty()) {
                memo = null;
            }
        }
    }

    /** 공개 식사팟 (비공개 추가 전에 작성한 코드·테스트가 그대로 동작하도록 둔다) */
    public PartyCreateRequest(Long restaurantId, LocalDate partyDate, MealType mealType, LocalTime mealTime,
                              Integer capacity, String memo) {
        this(restaurantId, partyDate, mealType, mealTime, capacity, memo, null);
    }

    /** 날짜 없이 만드는 경우 (= 오늘 팟). 날짜 추가 전에 작성한 코드·테스트가 그대로 동작하도록 둔다. */
    public PartyCreateRequest(Long restaurantId, MealType mealType, LocalTime mealTime, Integer capacity, String memo) {
        this(restaurantId, null, mealType, mealTime, capacity, memo, null);
    }

    /** 비공개로 만드는지 (null이면 공개) */
    public boolean privateParty() {
        return Boolean.TRUE.equals(isPrivate);
    }
}
