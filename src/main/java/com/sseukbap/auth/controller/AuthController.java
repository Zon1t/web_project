package com.sseukbap.auth.controller;

import com.sseukbap.auth.dto.CheckIdResponse;
import com.sseukbap.auth.dto.CheckNicknameResponse;
import com.sseukbap.auth.dto.SignupRequest;
import com.sseukbap.auth.dto.SignupResponse;
import com.sseukbap.auth.service.AuthService;
import com.sseukbap.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 아이디 중복 확인 (로그인 불필요) */
    @GetMapping("/check-id")
    public ApiResponse<CheckIdResponse> checkId(@RequestParam(required = false) String loginId) {
        // required = false: 값이 없을 때도 Service에서 API별 문구로 400을 내려주기 위해
        return ApiResponse.success(authService.checkLoginId(loginId));
    }

    /** 닉네임 중복 확인 (로그인 불필요) */
    @GetMapping("/check-nickname")
    public ApiResponse<CheckNicknameResponse> checkNickname(@RequestParam(required = false) String nickname) {
        return ApiResponse.success(authService.checkNickname(nickname));
    }

    /** 회원가입 (로그인 불필요) */
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }
}
