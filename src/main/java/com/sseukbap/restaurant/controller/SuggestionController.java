package com.sseukbap.restaurant.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.restaurant.dto.SuggestionCreateRequest;
import com.sseukbap.restaurant.dto.SuggestionCreateResponse;
import com.sseukbap.restaurant.service.SuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 식당 정보 수정 제안 API (B 담당). 로그인 필요 */
@RestController
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    /** 수정 제안 저장 */
    @PostMapping("/api/restaurants/{restaurantId}/suggestions")
    public ApiResponse<SuggestionCreateResponse> create(@PathVariable Long restaurantId,
                                                        @Valid @RequestBody SuggestionCreateRequest request,
                                                        @LoginMember Long memberId) {
        return ApiResponse.success(suggestionService.createSuggestion(restaurantId, memberId, request.content()));
    }
}
