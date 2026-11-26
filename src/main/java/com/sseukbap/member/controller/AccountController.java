package com.sseukbap.member.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.member.dto.EmailChangeRequest;
import com.sseukbap.member.dto.EmailChangeResponse;
import com.sseukbap.member.dto.NicknameChangeRequest;
import com.sseukbap.member.dto.NicknameChangeResponse;
import com.sseukbap.member.dto.PasswordChangeRequest;
import com.sseukbap.member.dto.WithdrawRequest;
import com.sseukbap.member.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계정 설정 API (API_SPEC 2장, C1 2026-11-24). 로그인 필요 (LoginCheckInterceptor가 /api/** 를 검사)
 * 마이페이지 조회 API(MyPageController)와 주소는 같은 /api/users/me 아래지만, 바꾸는 기능이라 따로 둔다.
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** 비밀번호 변경 → data 없음 */
    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@LoginMember Long memberId,
                                            @Valid @RequestBody PasswordChangeRequest request) {
        accountService.changePassword(memberId, request);
        return ApiResponse.success();
    }

    /** 닉네임 변경 → { nickname } */
    @PatchMapping("/nickname")
    public ApiResponse<NicknameChangeResponse> changeNickname(@LoginMember Long memberId,
                                                              @Valid @RequestBody NicknameChangeRequest request) {
        return ApiResponse.success(accountService.changeNickname(memberId, request));
    }

    /** 회사 이메일 등록·변경 → { email } (소문자) */
    @PatchMapping("/email")
    public ApiResponse<EmailChangeResponse> changeEmail(@LoginMember Long memberId,
                                                        @RequestBody EmailChangeRequest request) {
        return ApiResponse.success(accountService.changeEmail(memberId, request));
    }

    /**
     * 회원 탈퇴 → data 없음. 성공하면 세션을 무효화한다. (FE는 랜딩 화면으로 이동)
     * DELETE 대신 POST를 쓰는 이유: 본인 확인 비밀번호를 본문(body)으로 받기 위해 (client.js의 delete는 본문을 보내지 않음)
     */
    @PostMapping("/withdraw")
    public ApiResponse<Void> withdraw(@LoginMember Long memberId,
                                      @Valid @RequestBody WithdrawRequest request,
                                      HttpServletRequest httpRequest) {
        accountService.withdraw(memberId, request);
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.success();
    }
}
