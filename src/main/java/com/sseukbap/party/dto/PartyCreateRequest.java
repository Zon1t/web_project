package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.party.entity.MealType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * POST /api/parties/create 요청.
 * 검증 실패 시 문구는 API_SPEC대로 "입력값을 확인해 주세요." (400 INVALID_REQUEST)
 * 시간대·30분 단위·지난 시간 검사는 Service에서 한다. (400 INVALID_MEAL_TIME)
 * mealType에 LUNCH·DINNER 외 값, mealTime에 'HH:mm'이 아닌 값이 오면 JSON 해석 단계에서 400 INVALID_REQUEST
 */
public record PartyCreateRequest(

        @NotNull(message = "입력값을 확인해 주세요.")
        Long restaurantId,

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
        String memo
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
}
