package com.sseukbap.member.controller;

import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.member.dto.MemberProfileResponse;
import com.sseukbap.member.service.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 프로필 카드 API (C, 2026-11-25). 로그인 필요 (LoginCheckInterceptor가 /api/** 를 검사)
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberProfileController {

    private final MemberProfileService memberProfileService;

    @GetMapping("/{memberId}/profile")
    public ApiResponse<MemberProfileResponse> profile(@PathVariable Long memberId) {
        return ApiResponse.success(memberProfileService.getProfile(memberId));
    }
}
