package com.sseukbap.member.entity;

/**
 * 회원 역할 (MEMBER.ROLE). 기준: docs/sql/08_account_admin.sql
 */
public enum MemberRole {
    USER,    // 일반 회원
    ADMIN    // 관리자 (관리자 페이지 사용)
}
