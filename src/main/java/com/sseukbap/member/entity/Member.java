package com.sseukbap.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // ===== 2026-11-24 추가 (docs/sql/08_account_admin.sql). 값 변경 메서드는 쓰는 기능에서 추가한다. =====

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private MemberStatus status = MemberStatus.ACTIVE;   // 탈퇴하면 WITHDRAWN

    @Column(name = "WITHDRAWN_AT")
    private LocalDateTime withdrawnAt;                    // 탈퇴 일시

    @Column(name = "EMAIL", length = 100)
    private String email;                                 // 계정 찾기용 (선택)

    @Column(name = "RESET_TOKEN_HASH", length = 64)
    private String resetTokenHash;                        // 비밀번호 재설정 토큰의 SHA-256 (원문 저장 금지)

    @Column(name = "RESET_TOKEN_EXPIRES_AT")
    private LocalDateTime resetTokenExpiresAt;            // 재설정 토큰 만료 일시

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 10)
    private MemberRole role = MemberRole.USER;            // 관리자면 ADMIN

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

    /** 탈퇴한 회원인지 */
    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    /** 관리자인지 */
    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }
}
