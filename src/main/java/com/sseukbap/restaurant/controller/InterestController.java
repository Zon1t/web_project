package com.sseukbap.restaurant.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.restaurant.dto.InterestStatusResponse;
import com.sseukbap.restaurant.service.InterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관심 등록·해제 API (B 담당). 로그인 필요 */
@RestController
@RequestMapping("/api/restaurants/{restaurantId}/interest")
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interestService;

    /** 관심 등록 */
    @PostMapping
    public ApiResponse<InterestStatusResponse> create(@PathVariable Long restaurantId,
                                                      @LoginMember Long memberId) {
        return ApiResponse.success(interestService.createInterest(restaurantId, memberId));
    }

    /** 관심 해제 */
    @DeleteMapping
    public ApiResponse<InterestStatusResponse> delete(@PathVariable Long restaurantId,
                                                      @LoginMember Long memberId) {
        return ApiResponse.success(interestService.deleteInterest(restaurantId, memberId));
    }
}
