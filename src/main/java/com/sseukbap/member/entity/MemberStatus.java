package com.sseukbap.member.entity;

/**
 * 회원 상태 (MEMBER.STATUS). 기준: docs/sql/08_account_admin.sql
 */
public enum MemberStatus {
    ACTIVE,      // 사용 중
    WITHDRAWN    // 탈퇴 (로그인 불가, 아이디 재사용 불가)
}
