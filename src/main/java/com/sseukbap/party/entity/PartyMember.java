package com.sseukbap.party.entity;

import com.sseukbap.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.type.YesNoConverter;

import java.time.LocalDateTime;

/**
 * 식사팟 참여자 (PARTY_MEMBER 테이블). 기준: docs/sql/01_ddl.sql
 *
 * - 한 회원은 같은 팟에 한 번만 참여한다. (UK_PARTY_MEMBER: PARTY_ID + MEMBER_ID)
 * - 나가기 = 행 삭제.
 * - 방장 여부는 이 테이블이 아니라 MealParty.host로 판단한다.
 * - 따봉도 이 테이블에 저장한다. (THUMBS_UP 테이블 없음)
 * - Y/N 컬럼은 boolean으로 다룬다. getter 이름은 isConfirmed(), isThumbsUp()
 */
@Entity
@Table(name = "PARTY_MEMBER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_PARTY_MEMBER_GENERATOR")
    @SequenceGenerator(name = "SEQ_PARTY_MEMBER_GENERATOR", sequenceName = "SEQ_PARTY_MEMBER", allocationSize = 1)
    @Column(name = "PARTY_MEMBER_ID")
    private Long partyMemberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARTY_ID", nullable = false)
    private MealParty party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID", nullable = false)
    private Member member;

    @Convert(converter = YesNoConverter.class)
    @Column(name = "CONFIRMED_YN", nullable = false, length = 1)
    private boolean confirmed;              // 참여 확정(2차)

    @Convert(converter = YesNoConverter.class)
    @Column(name = "THUMBS_UP_YN", nullable = false, length = 1)
    private boolean thumbsUp;               // 따봉 완료

    @Column(name = "JOINED_AT", nullable = false, updatable = false)
    private LocalDateTime joinedAt;         // 참여하기(1차) 일시

    private PartyMember(MealParty party, Member member) {
        this.party = party;
        this.member = member;
        this.confirmed = false;
        this.thumbsUp = false;
        this.joinedAt = LocalDateTime.now();
    }

    /** 참여하기(1차). 확정·따봉은 N으로 시작한다. */
    public static PartyMember create(MealParty party, Member member) {
        return new PartyMember(party, member);
    }

    /** 참여 확정(2차). 일정 유무·중복 확정 검사는 Service에서 먼저 한다. */
    public void confirm() {
        this.confirmed = true;
    }

    /** 참여 확정 취소. 참여자 자격은 유지된다. (행은 그대로) */
    public void cancelConfirm() {
        this.confirmed = false;
    }
}
