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

    /** 비밀번호 변경 (계정 설정·비밀번호 찾기). 반드시 암호화한 값을 넘긴다. */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** 닉네임 변경 (계정 설정). 앞뒤 공백 제거·길이·중복 검사는 서비스에서 한다. */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 회사 이메일 등록·변경 (계정 설정·회원가입, 비밀번호·아이디 찾기용). 형식·중복 검사는 서비스에서 한다. null이면 삭제 */
    public void changeEmail(String email) {
        this.email = email;
    }

    /**
     * 회원 탈퇴 (C2). 행은 지우지 않고 상태만 바꾼다. (리뷰·대화·등록한 식당은 남김)
     * - 닉네임은 '탈퇴한회원' + 회원번호로 바꿔 원래 닉네임을 다른 사람이 쓸 수 있게 한다. (UK 때문에 번호를 붙임)
     * - 이메일·재설정 토큰은 지운다. 아이디는 그대로 두어 다시 가입에 쓸 수 없다.
     */
    public void withdraw(LocalDateTime now) {
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.nickname = WITHDRAWN_NICKNAME_PREFIX + memberId;
        this.email = null;
        this.resetTokenHash = null;
        this.resetTokenExpiresAt = null;
    }

    /** 탈퇴한 회원의 닉네임 앞부분. 이 글자로 시작하는 닉네임은 가입·변경에 쓸 수 없다 */
    public static final String WITHDRAWN_NICKNAME_PREFIX = "탈퇴한회원";

    /** 탈퇴한 회원인지 */
    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    /** 관리자인지 */
    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }
}
