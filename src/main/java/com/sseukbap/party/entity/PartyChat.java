package com.sseukbap.party.entity;

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
 * 식사팟 대화 (PARTY_CHAT 테이블). 기준: docs/sql/01_ddl.sql
 *
 * - chatId는 폴링 기준이다. (afterId보다 큰 메시지만 조회)
 * - 시스템 메시지(참여·나가기·일정·확정 등)는 작성자(member)가 null이다.
 * - 회원이 나가도 대화는 남는다. (PARTY_MEMBER가 아니라 MEMBER를 직접 참조)
 */
@Entity
@Table(name = "PARTY_CHAT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyChat {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_PARTY_CHAT_GENERATOR")
    @SequenceGenerator(name = "SEQ_PARTY_CHAT_GENERATOR", sequenceName = "SEQ_PARTY_CHAT", allocationSize = 1)
    @Column(name = "CHAT_ID")
    private Long chatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARTY_ID", nullable = false)
    private MealParty party;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID")
    private Member member;                  // 시스템 메시지는 null

    @Column(name = "CONTENT", nullable = false, length = 600)
    private String content;                 // 200자

    @Enumerated(EnumType.STRING)
    @Column(name = "MSG_TYPE", nullable = false, length = 10)
    private MessageType messageType;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PartyChat(MealParty party, Member member, String content, MessageType messageType) {
        this.party = party;
        this.member = member;
        this.content = content;
        this.messageType = messageType;
        this.createdAt = LocalDateTime.now();
    }

    /** 참여자가 보낸 메시지 */
    public static PartyChat createText(MealParty party, Member member, String content) {
        return new PartyChat(party, member, content, MessageType.TEXT);
    }

    /** 서버가 남기는 시스템 메시지 (작성자 없음) */
    public static PartyChat createSystem(MealParty party, String content) {
        return new PartyChat(party, null, content, MessageType.SYSTEM);
    }

    /** 시스템 메시지인지 */
    public boolean isSystem() {
        return messageType == MessageType.SYSTEM;
    }
}
