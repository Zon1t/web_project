package com.sseukbap.auth.controller;

import com.sseukbap.auth.dto.PasswordResetConfirmRequest;
import com.sseukbap.auth.dto.PasswordResetRequest;
import com.sseukbap.auth.dto.PasswordResetRequestResponse;
import com.sseukbap.auth.dto.PasswordResetVerifyRequest;
import com.sseukbap.auth.service.PasswordResetService;
import com.sseukbap.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 찾기 API (API_SPEC 1장, 2026-11-24). 로그인 없이 호출 (WebConfig에서 제외)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /** 인증번호 요청 → { email, expiresInSeconds } (아이디가 없어도 같은 응답) */
    @PostMapping("/password-reset/request")
    public ApiResponse<PasswordResetRequestResponse> request(@Valid @RequestBody PasswordResetRequest request) {
        return ApiResponse.success(passwordResetService.request(request));
    }

    /** 인증번호만 확인 → data 없음 (번호는 아직 쓰지 않음, 틀리면 INVALID_RESET_CODE) */
    @PostMapping("/password-reset/verify")
    public ApiResponse<Void> verify(@Valid @RequestBody PasswordResetVerifyRequest request) {
        passwordResetService.verify(request);
        return ApiResponse.success();
    }

    /** 인증번호 확인 + 새 비밀번호 → data 없음 */
    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request);
        return ApiResponse.success();
    }
}
