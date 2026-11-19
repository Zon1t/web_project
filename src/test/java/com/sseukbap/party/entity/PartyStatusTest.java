package com.sseukbap.party.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.sseukbap.party.entity.PartyStatus.CLOSED;
import static com.sseukbap.party.entity.PartyStatus.DELETED;
import static com.sseukbap.party.entity.PartyStatus.FINISHED;
import static com.sseukbap.party.entity.PartyStatus.FULL;
import static com.sseukbap.party.entity.PartyStatus.RECRUITING;
import static com.sseukbap.party.entity.PartyStatus.SCHEDULED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PartyStatus 상태 전이 단위 테스트
 * 기준: docs/PROJECT_CONTEXT.md "식사팟 상태 전이" 표
 */
class PartyStatusTest {

    @Nested
    @DisplayName("상태 확인")
    class Checks {

        @ParameterizedTest
        @EnumSource(names = {"RECRUITING", "SCHEDULED", "FULL", "CLOSED"})
        @DisplayName("진행 중: RECRUITING, SCHEDULED, FULL, CLOSED")
        void active(PartyStatus status) {
            assertThat(status.isActive()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(names = {"FINISHED", "DELETED"})
        @DisplayName("진행 중 아님: FINISHED, DELETED")
        void notActive(PartyStatus status) {
            assertThat(status.isActive()).isFalse();
        }

        @Test
        @DisplayName("참여 가능: RECRUITING, SCHEDULED만")
        void joinable() {
            assertThat(RECRUITING.isJoinable()).isTrue();
            assertThat(SCHEDULED.isJoinable()).isTrue();
            assertThat(FULL.isJoinable()).isFalse();
            assertThat(CLOSED.isJoinable()).isFalse();
            assertThat(FINISHED.isJoinable()).isFalse();
            assertThat(DELETED.isJoinable()).isFalse();
        }

        @Test
        @DisplayName("마감 가능: RECRUITING, SCHEDULED, FULL / 재개 가능: CLOSED")
        void closableAndReopenable() {
            assertThat(RECRUITING.isClosable()).isTrue();
            assertThat(SCHEDULED.isClosable()).isTrue();
            assertThat(FULL.isClosable()).isTrue();
            assertThat(CLOSED.isClosable()).isFalse();

            assertThat(CLOSED.isReopenable()).isTrue();
            assertThat(RECRUITING.isReopenable()).isFalse();
            assertThat(FULL.isReopenable()).isFalse();
        }
    }

    @Nested
    @DisplayName("일정 확정·수정")
    class ScheduleSet {

        @Test
        @DisplayName("RECRUITING → SCHEDULED")
        void recruiting() {
            assertThat(RECRUITING.afterScheduleSet()).isEqualTo(SCHEDULED);
        }

        @ParameterizedTest
        @EnumSource(names = {"SCHEDULED", "FULL", "CLOSED"})
        @DisplayName("SCHEDULED, FULL, CLOSED → 유지 (일정만 저장)")
        void keep(PartyStatus status) {
            assertThat(status.afterScheduleSet()).isEqualTo(status);
        }

        @ParameterizedTest
        @EnumSource(names = {"FINISHED", "DELETED"})
        @DisplayName("FINISHED, DELETED → 불가")
        void notAllowed(PartyStatus status) {
            assertThatThrownBy(status::afterScheduleSet).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("참여")
    class Join {

        @Test
        @DisplayName("정원에 도달하면 FULL")
        void reachCapacity() {
            assertThat(RECRUITING.afterMemberJoined(4, 4)).isEqualTo(FULL);
            assertThat(SCHEDULED.afterMemberJoined(4, 4)).isEqualTo(FULL);
        }

        @Test
        @DisplayName("정원 미만이면 유지")
        void belowCapacity() {
            assertThat(RECRUITING.afterMemberJoined(3, 4)).isEqualTo(RECRUITING);
            assertThat(SCHEDULED.afterMemberJoined(3, 4)).isEqualTo(SCHEDULED);
        }

        @ParameterizedTest
        @EnumSource(names = {"FULL", "CLOSED", "FINISHED", "DELETED"})
        @DisplayName("FULL, CLOSED, FINISHED, DELETED → 참여 불가")
        void notAllowed(PartyStatus status) {
            assertThatThrownBy(() -> status.afterMemberJoined(2, 4)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("나가기")
    class Leave {

        @Test
        @DisplayName("FULL에서 정원 미만 + 일정 있음 → SCHEDULED")
        void fullWithSchedule() {
            assertThat(FULL.afterMemberLeft(3, 4, true)).isEqualTo(SCHEDULED);
        }

        @Test
        @DisplayName("FULL에서 정원 미만 + 일정 없음 → RECRUITING")
        void fullWithoutSchedule() {
            assertThat(FULL.afterMemberLeft(3, 4, false)).isEqualTo(RECRUITING);
        }

        @Test
        @DisplayName("CLOSED는 정원 미만이 돼도 CLOSED 유지")
        void closedKeeps() {
            assertThat(CLOSED.afterMemberLeft(1, 4, true)).isEqualTo(CLOSED);
            assertThat(CLOSED.afterMemberLeft(1, 4, false)).isEqualTo(CLOSED);
        }

        @Test
        @DisplayName("RECRUITING, SCHEDULED는 유지")
        void othersKeep() {
            assertThat(RECRUITING.afterMemberLeft(1, 4, false)).isEqualTo(RECRUITING);
            assertThat(SCHEDULED.afterMemberLeft(2, 4, true)).isEqualTo(SCHEDULED);
        }

        @ParameterizedTest
        @EnumSource(names = {"FINISHED", "DELETED"})
        @DisplayName("FINISHED, DELETED → 나가기 불가")
        void notAllowed(PartyStatus status) {
            assertThatThrownBy(() -> status.afterMemberLeft(1, 4, false)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("모집 마감·재개")
    class CloseAndReopen {

        @ParameterizedTest
        @EnumSource(names = {"RECRUITING", "SCHEDULED", "FULL"})
        @DisplayName("RECRUITING, SCHEDULED, FULL → CLOSED")
        void close(PartyStatus status) {
            assertThat(status.afterClosed()).isEqualTo(CLOSED);
        }

        @ParameterizedTest
        @EnumSource(names = {"CLOSED", "FINISHED", "DELETED"})
        @DisplayName("CLOSED, FINISHED, DELETED → 마감 불가")
        void closeNotAllowed(PartyStatus status) {
            assertThatThrownBy(status::afterClosed).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("재개: 인원 = 정원이면 FULL")
        void reopenFull() {
            assertThat(CLOSED.afterReopened(4, 4, false)).isEqualTo(FULL);
            assertThat(CLOSED.afterReopened(4, 4, true)).isEqualTo(FULL);
        }

        @Test
        @DisplayName("재개: 정원 미만이면 일정 유무로 SCHEDULED / RECRUITING")
        void reopenBelowCapacity() {
            assertThat(CLOSED.afterReopened(2, 4, true)).isEqualTo(SCHEDULED);
            assertThat(CLOSED.afterReopened(2, 4, false)).isEqualTo(RECRUITING);
        }

        @ParameterizedTest
        @EnumSource(names = {"RECRUITING", "SCHEDULED", "FULL", "FINISHED", "DELETED"})
        @DisplayName("CLOSED가 아니면 재개 불가")
        void reopenNotAllowed(PartyStatus status) {
            assertThatThrownBy(() -> status.afterReopened(2, 4, false)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("식사 완료·삭제")
    class FinishAndDelete {

        @ParameterizedTest
        @EnumSource(names = {"RECRUITING", "SCHEDULED", "FULL", "CLOSED"})
        @DisplayName("진행 중 → 기준 시간 경과 시 FINISHED")
        void finish(PartyStatus status) {
            assertThat(status.afterMealTimePassed()).isEqualTo(FINISHED);
        }

        @ParameterizedTest
        @EnumSource(names = {"RECRUITING", "SCHEDULED", "FULL", "CLOSED"})
        @DisplayName("진행 중 → 방장 삭제·나가기 시 DELETED")
        void delete(PartyStatus status) {
            assertThat(status.afterDeleted()).isEqualTo(DELETED);
        }

        @ParameterizedTest
        @EnumSource(names = {"FINISHED", "DELETED"})
        @DisplayName("FINISHED, DELETED → 완료 처리·삭제 불가")
        void notAllowed(PartyStatus status) {
            assertThatThrownBy(status::afterMealTimePassed).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(status::afterDeleted).isInstanceOf(IllegalStateException.class);
        }
    }
}
