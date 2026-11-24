package com.sseukbap.common.exception;

import com.sseukbap.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 모든 예외를 공통 응답 형식으로 바꿔 주는 곳.
 * 오류 응답은 여기서만 만든다. (Controller·Service에서 try-catch로 응답을 만들지 않는다)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 서비스에서 던진 비즈니스 예외 (예: PARTY_FULL) + 로그인 체크 인터셉터의 401 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("BusinessException: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getErrorCode());
        return toResponse(e.getErrorCode(), e.getMessage(), request);
    }

    /** @RequestBody DTO의 @Valid 검증 실패 → DTO에 적은 문구를 그대로 내려준다 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null) ? fieldError.getDefaultMessage() : null;
        return toResponse(ErrorCode.INVALID_REQUEST, message, request);
    }

    /** @RequestParam·@PathVariable에 붙인 검증(@NotBlank, @Min 등) 실패 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {
        String message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse(null);
        return toResponse(ErrorCode.INVALID_REQUEST, message, request);
    }

    /**
     * 필수 파라미터 누락, JSON 형식 오류, 타입 불일치 등 → "입력값을 확인해 주세요."
     * API마다 다른 문구가 필요하면 @RequestParam(required = false) + @NotBlank(message = "...")로 받아서
     * 위의 검증 처리로 넘어가게 한다.
     *
     * 필수 파라미터 누락, JSON 형식 오류, 타입 불일치(숫자 자리에 문자 등), Content-Type·HTTP 메서드 오류 */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("Bad request: {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return toResponse(ErrorCode.INVALID_REQUEST, null, request);
    }

    /**
     * 업로드 파일 크기 초과 (application.yml: max-file-size 5MB, max-request-size 6MB)
     * → API_SPEC 리뷰 작성 오류 표대로 400 INVALID_IMAGE "jpg·png 형식, 5MB 이하 사진만 올릴 수 있습니다."
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("Upload too large: {} {}", request.getMethod(), request.getRequestURI());
        return toResponse(ErrorCode.INVALID_IMAGE, null, request);
    }

    /**
     * 없는 주소.
     * - /api/ 요청: 지금처럼 JSON 404 NOT_FOUND
     * - 화면 주소(예: /abc.html): JSON 글자가 그대로 보이지 않게 static/404.html을 404로 보여 준다 (2026-11-24, C1)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .body(notFoundPage());
        }
        return toResponse(ErrorCode.NOT_FOUND, null, request);
    }

    /** static/404.html 내용. 파일을 못 읽으면 아주 짧은 안내로 대신한다 */
    private String notFoundPage() {
        try {
            return new ClassPathResource("static/404.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "<!doctype html><meta charset=\"utf-8\"><title>페이지를 찾을 수 없어요</title>"
                    + "<p>페이지를 찾을 수 없어요. <a href=\"/\">홈으로</a></p>";
        }
    }

    /** 그 외 예상하지 못한 모든 오류 → 500. 원인은 서버 로그에서 확인 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception: {} {}", request.getMethod(), request.getRequestURI(), e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR, null, request);
    }

    // message가 null이면 ErrorCode의 기본 문구를 쓴다.
    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message, HttpServletRequest request) {
        String finalMessage = (message != null && !message.isBlank()) ? message : errorCode.getMessage();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, finalMessage, request.getRequestURI()));
    }
}
