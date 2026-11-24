package com.sseukbap.member.dto;

/**
 * PATCH /api/users/me/email 요청 (2026-11-24).
 * 형식 검사·소문자 통일은 AccountService(EmailPolicy)에서 한다.
 */
public record EmailChangeRequest(String email) {
}
