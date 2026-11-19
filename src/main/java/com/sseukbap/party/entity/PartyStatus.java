package com.sseukbap.party.entity;

/**
 * 식사팟 상태 (MEAL_PARTY.STATUS)
 * DB에는 이름 그대로 문자열로 저장된다. (@Enumerated(EnumType.STRING))
 *
 * 상태 전이 규칙은 docs/PROJECT_CONTEXT.md "식사팟 상태 전이" 표를 그대로 옮겼다.
 * - after...() 메서드는 "이 일이 일어난 뒤의 상태"를 돌려준다. 값을 바꾸지는 않는다.
 * - 규칙상 일어날 수 없는 전이(예: FINISHED 팟에 참여)는 IllegalStateException을 던진다.
 *   이런 경우는 Service가 먼저 막고 BusinessException(API 오류 코드)을 던져야 한다.
 *   여기서 예외가 나면 Service의 검사가 빠졌다는 뜻이다.
 */
public enum PartyStatus {
    RECRUITING,   // 모집중 (생성 직후)
    SCHEDULED,    // 일정 확정 (방장이 일정 확정)
    FULL,         // 정원 마감 (참여로 정원 도달 시 자동)
    CLOSED,       // 모집 마감 (방장이 직접 마감)
    FINISHED,     // 식사 완료 (기준 시간 경과 시 스케줄러)
    DELETED;      // 삭제 (소프트 삭제, 응답·집계에 노출하지 않음)

    // ===== 상태 확인 =====

    /** 진행 중인 팟인지 (FINISHED·DELETED가 아님). 참여·나가기·일정·마감·대화 전송은 이 상태에서만 가능 */
    public boolean isActive() {
        return this != FINISHED && this != DELETED;
    }

    /** 새 참여자를 받을 수 있는 상태인지 (RECRUITING, SCHEDULED) */
    public boolean isJoinable() {
        return this == RECRUITING || this == SCHEDULED;
    }

    /** 방장이 모집 마감할 수 있는 상태인지 (RECRUITING, SCHEDULED, FULL) */
    public boolean isClosable() {
        return this == RECRUITING || this == SCHEDULED || this == FULL;
    }

    /** 방장이 모집 재개할 수 있는 상태인지 (CLOSED) */
    public boolean isReopenable() {
        return this == CLOSED;
    }

    // ===== 상태 전이 =====

    /**
     * 방장이 일정을 확정·수정한 뒤의 상태
     * RECRUITING → SCHEDULED / SCHEDULED, FULL, CLOSED → 유지
     */
    public PartyStatus afterScheduleSet() {
        requireActive("일정 확정");
        return this == RECRUITING ? SCHEDULED : this;
    }

    /**
     * 참여자가 들어온 뒤의 상태
     * RECRUITING, SCHEDULED에서 인원이 정원에 도달하면 FULL, 아니면 유지
     *
     * @param memberCount 참여 후 인원 (방장 포함)
     * @param capacity    정원 (방장 포함)
     */
    public PartyStatus afterMemberJoined(int memberCount, int capacity) {
        if (!isJoinable()) {
            throw new IllegalStateException(this + " 상태에서는 참여할 수 없습니다.");
        }
        return memberCount >= capacity ? FULL : this;
    }

    /**
     * 참여자가 나간 뒤의 상태
     * FULL에서 정원 미만이 되면 일정 유무로 SCHEDULED / RECRUITING, 그 외(CLOSED 포함)는 유지
     *
     * @param memberCount 나간 후 인원 (방장 포함)
     * @param capacity    정원
     * @param hasSchedule 방장이 일정을 확정했는지 (CONFIRMED_TIME 있음)
     */
    public PartyStatus afterMemberLeft(int memberCount, int capacity, boolean hasSchedule) {
        requireActive("나가기");
        if (this == FULL && memberCount < capacity) {
            return recruitingOrScheduled(hasSchedule);
        }
        return this;
    }

    /**
     * 방장이 모집 마감한 뒤의 상태: RECRUITING, SCHEDULED, FULL → CLOSED
     */
    public PartyStatus afterClosed() {
        if (!isClosable()) {
            throw new IllegalStateException(this + " 상태에서는 모집 마감할 수 없습니다.");
        }
        return CLOSED;
    }

    /**
     * 방장이 모집 재개한 뒤의 상태 (CLOSED에서만)
     * 인원 = 정원이면 FULL, 아니면 일정 유무로 SCHEDULED / RECRUITING
     */
    public PartyStatus afterReopened(int memberCount, int capacity, boolean hasSchedule) {
        if (!isReopenable()) {
            throw new IllegalStateException(this + " 상태에서는 모집 재개할 수 없습니다.");
        }
        return memberCount >= capacity ? FULL : recruitingOrScheduled(hasSchedule);
    }

    /** 기준 시간이 지난 뒤의 상태 (스케줄러): 진행 중인 팟 → FINISHED */
    public PartyStatus afterMealTimePassed() {
        requireActive("식사 완료 처리");
        return FINISHED;
    }

    /** 방장이 삭제하거나 나간 뒤의 상태: 진행 중인 팟 → DELETED (FINISHED는 삭제 불가) */
    public PartyStatus afterDeleted() {
        requireActive("삭제");
        return DELETED;
    }

    private static PartyStatus recruitingOrScheduled(boolean hasSchedule) {
        return hasSchedule ? SCHEDULED : RECRUITING;
    }

    private void requireActive(String action) {
        if (!isActive()) {
            throw new IllegalStateException(this + " 상태에서는 " + action + "을(를) 할 수 없습니다.");
        }
    }
}
