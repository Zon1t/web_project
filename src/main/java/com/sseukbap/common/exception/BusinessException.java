package com.sseukbap.common.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 시 던지는 예외. GlobalExceptionHandler가 공통 응답으로 바꿔 준다.
 *
 * 사용 예 (Service)
 *   throw new BusinessException(ErrorCode.PARTY_FULL);                          // 대표 문구 사용
 *   throw new BusinessException(ErrorCode.NOT_HOST, "방장만 삭제할 수 있습니다."); // API별 문구 (API_SPEC 오류 표)
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
