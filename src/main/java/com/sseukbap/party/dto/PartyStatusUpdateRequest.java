package com.sseukbap.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH /api/parties/{partyId}/status 요청 (방장의 모집 마감·재개)
 *
 * action을 enum이 아니라 문자열로 받는 이유:
 * 명세의 오류 문구가 "잘못된 요청입니다."인데, enum으로 받으면 잘못된 값이 JSON 해석 단계에서 걸려
 * 공통 문구("입력값을 확인해 주세요.")로 나간다. 문자열 + 검증으로 받아야 명세 문구를 쓸 수 있다.
 */
public record PartyStatusUpdateRequest(

        @NotBlank(message = "잘못된 요청입니다.")
        @Pattern(regexp = "CLOSE|REOPEN", message = "잘못된 요청입니다.")
        String action
) {
    public static final String CLOSE = "CLOSE";
    public static final String REOPEN = "REOPEN";

    /** 마감 요청인지 (아니면 재개) */
    public boolean isClose() {
        return CLOSE.equals(action);
    }
}
