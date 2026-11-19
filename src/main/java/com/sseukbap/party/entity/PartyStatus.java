package com.sseukbap.party.entity;

/**
 * 식사팟 상태 (MEAL_PARTY.STATUS)
 * DB에는 이름 그대로 문자열로 저장된다. (@Enumerated(EnumType.STRING))
 */
public enum PartyStatus {
    RECRUITING,   // 모집중 (생성 직후)
    SCHEDULED,    // 일정 확정 (방장이 일정 확정)
    FULL,         // 정원 마감 (참여로 정원 도달 시 자동)
    CLOSED,       // 모집 마감 (방장이 직접 마감)
    FINISHED,     // 식사 완료 (기준 시간 경과 시 스케줄러)
    DELETED       // 삭제 (소프트 삭제, 응답·집계에 노출하지 않음)
}
