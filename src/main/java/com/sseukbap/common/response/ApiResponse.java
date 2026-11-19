package com.sseukbap.common.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.sseukbap.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 모든 API의 공통 응답 형식.
 * {statusCode, timestamp, path, message, data, error}
 *
 * 사용 예 (Controller)
 *   return ApiResponse.success(data);
 *   return ApiResponse.success(data, "회원가입이 완료되었습니다.");
 *   return ApiResponse.success();   // 돌려줄 data가 없을 때
 *
 * 오류 응답은 직접 만들지 말고 throw new BusinessException(ErrorCode.XXX) 를 사용한다.
 * (GlobalExceptionHandler가 이 형식으로 바꿔서 응답한다)
 */
@Getter
@JsonPropertyOrder({"statusCode", "timestamp", "path", "message", "data", "error"})
public class ApiResponse<T> {

    private static final String DEFAULT_SUCCESS_MESSAGE = "요청이 처리되었습니다.";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final int statusCode;
    private final String timestamp;   // 한국 시간, 예: 2026-11-17T12:00:00+09:00
    private final String path;
    private final String message;
    private final T data;
    private final String error;       // 성공이면 null, 실패면 ErrorCode 이름 (예: PARTY_FULL)

    private ApiResponse(int statusCode, String path, String message, T data, String error) {
        this.statusCode = statusCode;
        this.timestamp = OffsetDateTime.now(KOREA)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        this.path = path;
        this.message = message;
        this.data = data;
        this.error = error;
    }

    // ===== 성공 =====

    public static <T> ApiResponse<T> success(T data) {
        return success(data, DEFAULT_SUCCESS_MESSAGE);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(HttpStatus.OK.value(), currentPath(), message, data, null);
    }

    public static ApiResponse<Void> success() {
        return success(null, DEFAULT_SUCCESS_MESSAGE);
    }

    // ===== 실패 (GlobalExceptionHandler에서만 사용) =====

    public static ApiResponse<Void> error(ErrorCode errorCode, String path) {
        return error(errorCode, errorCode.getMessage(), path);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message, String path) {
        return new ApiResponse<>(errorCode.getStatus().value(), path, message, null, errorCode.getCode());
    }

    // 현재 요청 주소(/api/...)를 꺼낸다. Controller가 path를 직접 넘기지 않아도 되도록.
    private static String currentPath() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRequestURI();
        }
        return null;
    }
}
