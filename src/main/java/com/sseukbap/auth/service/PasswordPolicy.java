package com.sseukbap.auth.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 비밀번호 규칙 (2026-11-24, C1). 회원가입과 비밀번호 변경이 같이 쓴다.
 *
 * - 8~20자
 * - 영문과 숫자를 모두 포함 (특수문자는 넣어도 되고 안 넣어도 됨)
 * - 공백 불가
 * - 아이디와 같으면 안 됨 (대소문자 무시)
 *
 * 화면(signup.js, account.js)도 같은 규칙으로 미리 검사하지만, 최종 판단은 여기서 한다.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 20;

    static final String RULE_MESSAGE = "비밀번호는 8~20자로, 영문과 숫자를 함께 넣어 주세요.";
    static final String SPACE_MESSAGE = "비밀번호에는 공백을 넣을 수 없습니다.";
    static final String SAME_AS_ID_MESSAGE = "아이디와 다른 비밀번호를 입력해 주세요.";

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private PasswordPolicy() {
    }

    /**
     * 규칙에 맞지 않으면 400 INVALID_REQUEST (규칙별 문구)
     *
     * @param password 검사할 비밀번호 (원문)
     * @param loginId  같은지 비교할 아이디. null이면 비교하지 않는다
     */
    public static void validate(String password, String loginId) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, RULE_MESSAGE);
        }
        if (WHITESPACE.matcher(password).find()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, SPACE_MESSAGE);
        }
        if (!LETTER.matcher(password).find() || !DIGIT.matcher(password).find()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, RULE_MESSAGE);
        }
        if (loginId != null && password.toLowerCase(Locale.ROOT).equals(loginId.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, SAME_AS_ID_MESSAGE);
        }
    }
}
