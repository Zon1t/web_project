package com.sseukbap.member.dto;

/** PATCH /api/users/me/email 응답 data: 저장된 이메일 (소문자) */
public record EmailChangeResponse(String email) {
}
