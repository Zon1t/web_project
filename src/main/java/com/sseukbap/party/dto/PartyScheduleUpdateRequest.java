package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * PATCH /api/parties/{partyId}/schedule 요청 (방장의 일정 확정·수정)
 * 검증 실패 시 문구는 API_SPEC대로 "입력값을 확인해 주세요." (400 INVALID_REQUEST)
 * 시간대·30분 단위·지난 시간 검사는 Service에서 한다. (400 INVALID_MEAL_TIME)
 */
public record PartyScheduleUpdateRequest(

        @NotNull(message = "입력값을 확인해 주세요.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime confirmedTime,

        @NotBlank(message = "입력값을 확인해 주세요.")
        @Size(max = 30, message = "입력값을 확인해 주세요.")
        String meetPlace,

        @Size(max = 50, message = "입력값을 확인해 주세요.")
        String confirmMemo
) {
    /** 앞뒤 공백 제거. 메모가 비어 있으면 null로 저장한다. (검증보다 먼저 실행됨) */
    public PartyScheduleUpdateRequest {
        if (meetPlace != null) {
            meetPlace = meetPlace.trim();
        }
        if (confirmMemo != null) {
            confirmMemo = confirmMemo.trim();
            if (confirmMemo.isEmpty()) {
                confirmMemo = null;
            }
        }
    }
}
