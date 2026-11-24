package com.sseukbap.member.dto;

/** PATCH /api/users/me/nickname 응답 data: 저장된 닉네임 (앞뒤 공백 제거 값) */
public record NicknameChangeResponse(String nickname) {
}
