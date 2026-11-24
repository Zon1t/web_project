package com.sseukbap.auth.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 회사 이메일 규칙 (2026-11-24). 회원가입·계정 설정·비밀번호/아이디 찾기가 같이 쓴다.
 * - 앞뒤 공백 제거 + 소문자로 통일해 저장·비교한다.
 * - 형식: 아이디@도메인.끝 , 100자 이하. (회사 도메인은 제한하지 않음)
 */
public final class EmailPolicy {

    public static final int MAX_LENGTH = 100;
    static final String FORMAT_MESSAGE = "이메일 형식을 확인해 주세요.";
    private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private EmailPolicy() {
    }

    /** 앞뒤 공백 제거 + 소문자. null·빈 값이면 null */
    public static String normalize(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 정리한 값이 형식에 맞지 않으면 400 INVALID_REQUEST */
    public static String normalizeAndValidate(String email) {
        String normalized = normalize(email);
        if (normalized == null || normalized.length() > MAX_LENGTH || !FORMAT.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, FORMAT_MESSAGE);
        }
        return normalized;
    }
}
