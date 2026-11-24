package com.sseukbap.auth.dto;

/** 아이디 찾기 응답 data. 등록된 이메일이든 아니든 같은 값 (이메일 등록 여부를 숨김) */
public record FindIdResponse(String email) {
}
