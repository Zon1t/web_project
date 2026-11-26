package com.sseukbap.restaurant.entity;

import com.sseukbap.member.entity.Member;
import jakarta.persistence.Column;
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

import java.time.LocalDateTime;

/**
 * 식당 정보 수정 제안 (SUGGESTION 테이블). 기준: docs/sql/01_ddl.sql
 * 처리 방식은 미정이라 지금은 저장만 한다.
 */
@Entity
@Table(name = "SUGGESTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_SUGGESTION_GENERATOR")
    @SequenceGenerator(name = "SEQ_SUGGESTION_GENERATOR", sequenceName = "SEQ_SUGGESTION", allocationSize = 1)
    @Column(name = "SUGGESTION_ID")
    private Long suggestionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESTAURANT_ID", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID", nullable = false)
    private Member member;            // 제안한 회원

    @Column(name = "CONTENT", nullable = false, length = 1500)
    private String content;           // 최대 500자

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private SuggestionStatus status = SuggestionStatus.OPEN;   // 관리자가 처리하면 DONE (2026-11-24)

    private Suggestion(Restaurant restaurant, Member member, String content) {
        this.restaurant = restaurant;
        this.member = member;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    /** 관리자 처리 상태 변경: DONE(처리 완료) / OPEN(되돌리기) (2026-11-24 A3) */
    public void changeStatus(SuggestionStatus status) {
        this.status = status;
    }

    /** 수정 제안 저장 */
    public static Suggestion create(Restaurant restaurant, Member member, String content) {
        return new Suggestion(restaurant, member, content);
    }
}
