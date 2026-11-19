package com.sseukbap.restaurant.entity;

import com.sseukbap.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 관심 식당 (INTEREST 테이블). 기준: docs/sql/01_ddl.sql
 *
 * - 회원 1명이 같은 식당에 한 번만 등록할 수 있다. (UK_INTEREST: MEMBER_ID + RESTAURANT_ID)
 * - 해제하면 행을 삭제한다.
 * - 관심 수는 랭킹에 반영하지 않는다.
 */
@Entity
@Table(name = "INTEREST")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Interest {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_INTEREST_GENERATOR")
    @SequenceGenerator(name = "SEQ_INTEREST_GENERATOR", sequenceName = "SEQ_INTEREST", allocationSize = 1)
    @Column(name = "INTEREST_ID")
    private Long interestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESTAURANT_ID", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID", nullable = false)
    private Member member;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Interest(Restaurant restaurant, Member member) {
        this.restaurant = restaurant;
        this.member = member;
        this.createdAt = LocalDateTime.now();
    }

    /** 관심 등록 */
    public static Interest create(Restaurant restaurant, Member member) {
        return new Interest(restaurant, member);
    }
}
