package com.sseukbap.party.entity;

import com.sseukbap.common.enums.MealType;
import com.sseukbap.member.entity.Member;
import com.sseukbap.party.service.PartyTimePolicy;
import com.sseukbap.restaurant.entity.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 식사팟 (MEAL_PARTY 테이블). 기준: docs/sql/01_ddl.sql
 *
 * - 소프트 삭제: 삭제해도 행을 지우지 않고 status = DELETED. 모든 조회·집계에서 DELETED를 빼야 한다.
 * - 방장 = host. 방장도 PARTY_MEMBER에 참여자로 들어간다. (생성자 = 방장 + 첫 참여자)
 * - 참여·나가기·일정·마감 같은 상태 변경 메서드는 각 기능 브랜치에서 추가한다.
 *   상태 계산은 PartyStatus의 after...() 메서드를 쓴다.
 */
@Entity
@Table(name = "MEAL_PARTY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealParty {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_MEAL_PARTY_GENERATOR")
    @SequenceGenerator(name = "SEQ_MEAL_PARTY_GENERATOR", sequenceName = "SEQ_MEAL_PARTY", allocationSize = 1)
    @Column(name = "PARTY_ID")
    private Long partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESTAURANT_ID", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HOST_ID", nullable = false)
    private Member host;                    // 방장

    @Column(name = "PARTY_DATE", nullable = false)
    private LocalDate partyDate;            // 식사 날짜 (당일)

    @Enumerated(EnumType.STRING)
    @Column(name = "MEAL_TYPE", nullable = false, length = 10)
    private MealType mealType;

    @Convert(converter = HourMinuteConverter.class)
    @Column(name = "MEAL_TIME", nullable = false, length = 5)
    private LocalTime mealTime;             // 예정 시간 (DB에는 'HH:mm')

    @Column(name = "CAPACITY", nullable = false)
    private int capacity;                   // 정원 2~12, 방장 포함

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private PartyStatus status;

    @Column(name = "MEMO", length = 150)
    private String memo;                    // 한마디 50자

    @Convert(converter = HourMinuteConverter.class)
    @Column(name = "CONFIRMED_TIME", length = 5)
    private LocalTime confirmedTime;        // 방장 확정 시간 (일정 확정 전에는 null)

    @Column(name = "MEET_PLACE", length = 90)
    private String meetPlace;               // 만날 장소 30자

    @Column(name = "CONFIRM_MEMO", length = 150)
    private String confirmMemo;             // 확정 메모 50자

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private MealParty(Restaurant restaurant, Member host, LocalDate partyDate, MealType mealType,
                      LocalTime mealTime, int capacity, String memo) {
        this.restaurant = restaurant;
        this.host = host;
        this.partyDate = partyDate;
        this.mealType = mealType;
        this.mealTime = mealTime;
        this.capacity = capacity;
        this.memo = memo;
        this.status = PartyStatus.RECRUITING;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 식사팟 생성 (상태는 RECRUITING)
     * 시간·정원 검증은 Service에서 먼저 한다. (PartyTimePolicy.isValidMealTime 등)
     * 방장을 PartyMember로 저장하는 것도 Service에서 함께 한다.
     */
    public static MealParty create(Restaurant restaurant, Member host, LocalDate partyDate, MealType mealType,
                                   LocalTime mealTime, int capacity, String memo) {
        return new MealParty(restaurant, host, partyDate, mealType, mealTime, capacity, memo);
    }

    /** 방장이 일정을 확정했는지 */
    public boolean hasSchedule() {
        return confirmedTime != null;
    }

    /** 기준 시간: 확정 시간이 있으면 확정 시간, 없으면 예정 시간 */
    public LocalTime getBaseTime() {
        return PartyTimePolicy.getBaseTime(mealTime, confirmedTime);
    }

    /** 이 회원이 방장인지 */
    public boolean isHost(Long memberId) {
        return host.getMemberId().equals(memberId);
    }

    /** 삭제된 팟인지 (식사팟 API는 404 PARTY_DELETED) */
    public boolean isDeleted() {
        return status == PartyStatus.DELETED;
    }

    /** 식사가 끝난 팟인지 (참여·나가기·삭제 등 불가, 409 PARTY_FINISHED) */
    public boolean isFinished() {
        return status == PartyStatus.FINISHED;
    }

    // ===== 상태 변경 =====

    /**
     * 소프트 삭제: 행은 남기고 상태만 DELETED로 바꾼다. (참여자·대화 행도 그대로)
     * 방장 여부·FINISHED 검사는 Service에서 먼저 한다.
     */
    public void delete() {
        this.status = this.status.afterDeleted();
    }

    /**
     * 참여자가 들어온 뒤 상태 반영: 정원에 도달하면 FULL (자동 마감)
     * @param memberCountAfter 참여 후 인원 (방장 포함)
     */
    public void join(int memberCountAfter) {
        this.status = this.status.afterMemberJoined(memberCountAfter, capacity);
    }

    /**
     * 참여자가 나간 뒤 상태 반영: FULL이었다면 일정 유무로 SCHEDULED / RECRUITING (CLOSED는 유지)
     * @param memberCountAfter 나간 후 인원 (방장 포함)
     */
    public void leave(int memberCountAfter) {
        this.status = this.status.afterMemberLeft(memberCountAfter, capacity, hasSchedule());
    }

    /**
     * 방장의 일정 확정·수정: 확정 시간·만날 장소·메모를 저장하고 RECRUITING이면 SCHEDULED로 바꾼다.
     * (SCHEDULED·FULL·CLOSED는 유지) 확정 초기화는 Service에서 PARTY_MEMBER에 한다.
     */
    public void updateSchedule(LocalTime confirmedTime, String meetPlace, String confirmMemo) {
        this.confirmedTime = confirmedTime;
        this.meetPlace = meetPlace;
        this.confirmMemo = confirmMemo;
        this.status = this.status.afterScheduleSet();
    }

    /** 방장의 모집 마감: RECRUITING·SCHEDULED·FULL → CLOSED */
    public void close() {
        this.status = this.status.afterClosed();
    }

    /**
     * 방장의 모집 재개 (CLOSED에서만): 정원이 차 있으면 FULL, 아니면 일정 유무로 SCHEDULED / RECRUITING
     * @param memberCount 현재 인원 (방장 포함)
     */
    public void reopen(int memberCount) {
        this.status = this.status.afterReopened(memberCount, capacity, hasSchedule());
    }
}
