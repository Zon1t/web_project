package com.sseukbap.restaurant.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.restaurant.dto.RestaurantAutocompleteResponse;
import com.sseukbap.restaurant.dto.RestaurantDetailResponse;
import com.sseukbap.restaurant.dto.RestaurantListResponse;
import com.sseukbap.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 식당 API (B 담당). 랭킹·룰렛(/api/restaurants/rankings, /roulette/**)은 C 담당이라 여기 없다.
 * 모든 API 로그인 필요 (LoginCheckInterceptor가 확인)
 */
@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    /** 식당 이름 자동완성 */
    @GetMapping("/autocomplete")
    public ApiResponse<List<RestaurantAutocompleteResponse>> autocomplete(
            @RequestParam(required = false) String keyword) {
        // required = false: 값이 없을 때도 Service에서 API별 문구로 400을 내려주기 위해
        return ApiResponse.success(restaurantService.getAutocompleteList(keyword));
    }

    /** 식당 목록 조회 (검색·필터·지도) */
    @GetMapping("/list")
    public ApiResponse<List<RestaurantListResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return ApiResponse.success(restaurantService.getRestaurantList(keyword, category));
    }

    /** 식당 상세 조회 */
    @GetMapping("/{restaurantId}")
    public ApiResponse<RestaurantDetailResponse> detail(@PathVariable Long restaurantId,
                                                        @LoginMember Long memberId) {
        return ApiResponse.success(restaurantService.getRestaurant(restaurantId, memberId));
    }
}
