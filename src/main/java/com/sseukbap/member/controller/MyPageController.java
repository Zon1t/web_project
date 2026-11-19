package com.sseukbap.member.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.member.dto.MyInterestResponse;
import com.sseukbap.member.dto.MyPageSummaryResponse;
import com.sseukbap.member.dto.MyPartyResponse;
import com.sseukbap.member.dto.MyRestaurantResponse;
import com.sseukbap.member.dto.MyReviewResponse;
import com.sseukbap.member.service.MyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 마이페이지 API (API_SPEC 2장). 모두 로그인 필요 (LoginCheckInterceptor가 /api/** 를 검사)
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/summary")
    public ApiResponse<MyPageSummaryResponse> summary(@LoginMember Long memberId) {
        return ApiResponse.success(myPageService.getSummary(memberId));
    }

    @GetMapping("/reviews")
    public ApiResponse<List<MyReviewResponse>> reviews(@LoginMember Long memberId) {
        return ApiResponse.success(myPageService.getReviews(memberId));
    }

    @GetMapping("/parties")
    public ApiResponse<List<MyPartyResponse>> parties(@LoginMember Long memberId) {
        return ApiResponse.success(myPageService.getParties(memberId));
    }

    @GetMapping("/restaurants")
    public ApiResponse<List<MyRestaurantResponse>> restaurants(@LoginMember Long memberId) {
        return ApiResponse.success(myPageService.getRestaurants(memberId));
    }

    @GetMapping("/interests")
    public ApiResponse<List<MyInterestResponse>> interests(@LoginMember Long memberId) {
        return ApiResponse.success(myPageService.getInterests(memberId));
    }
}
