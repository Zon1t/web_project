package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.party.entity.MealParty;

import java.time.LocalTime;

/** 식사팟 상세의 schedule (방장이 확정한 일정). 일정이 없으면 상세 응답에서 null */
public record PartyScheduleResponse(
        @JsonFormat(pattern = "HH:mm")
        LocalTime confirmedTime,
        String meetPlace,
        String confirmMemo
) {

    /** 일정이 확정되지 않았으면 null을 돌려준다. */
    public static PartyScheduleResponse from(MealParty party) {
        if (!party.hasSchedule()) {
            return null;
        }
        return new PartyScheduleResponse(party.getConfirmedTime(), party.getMeetPlace(), party.getConfirmMemo());
    }
}
