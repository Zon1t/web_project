package com.sseukbap.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원 (MEMBER 테이블). 기준: docs/sql/01_ddl.sql
 */
@Entity
@Table(name = "MEMBER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_MEMBER_GENERATOR")
    @SequenceGenerator(name = "SEQ_MEMBER_GENERATOR", sequenceName = "SEQ_MEMBER", allocationSize = 1)
    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "LOGIN_ID", nullable = false, length = 20)
    private String loginId;

    @Column(name = "PASSWORD", nullable = false, length = 60)
    private String password;          // BCrypt 해시

    @Column(name = "NICKNAME", nullable = false, length = 30)
    private String nickname;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Member(String loginId, String encodedPassword, String nickname) {
        this.loginId = loginId;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.createdAt = LocalDateTime.now();
    }

    /** 회원 생성. 비밀번호는 반드시 암호화한 값을 넘긴다. */
    public static Member create(String loginId, String encodedPassword, String nickname) {
        return new Member(loginId, encodedPassword, nickname);
    }

    /** 비밀번호 변경 (계정 설정). 반드시 암호화한 값을 넘긴다. */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** 닉네임 변경 (계정 설정). 앞뒤 공백 제거·길이·중복 검사는 서비스에서 한다. */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }
}
