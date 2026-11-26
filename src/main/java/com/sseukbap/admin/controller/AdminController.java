package com.sseukbap.admin.controller;

import com.sseukbap.admin.dto.AdminCafeteriaMenuSaveRequest;
import com.sseukbap.admin.dto.AdminCafeteriaMenuSaveResponse;
import com.sseukbap.admin.dto.AdminCafeteriaMenusResponse;
import com.sseukbap.admin.dto.AdminRestaurantListResponse;
import com.sseukbap.admin.dto.AdminRestaurantResponse;
import com.sseukbap.admin.dto.AdminRestaurantUpdateRequest;
import com.sseukbap.admin.dto.AdminReviewResponse;
import com.sseukbap.admin.dto.AdminReviewUpdateRequest;
import com.sseukbap.admin.dto.AdminSuggestionResponse;
import com.sseukbap.admin.dto.AdminSuggestionUpdateRequest;
import com.sseukbap.admin.service.AdminService;
import com.sseukbap.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 API (docs/API_SPEC.md 7장, 2026-11-24 A3)
 * 로그인 확인(LoginCheckInterceptor) → 관리자 확인(AdminCheckInterceptor, 아니면 403) 뒤에 들어온다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** 전체 식당 (숨김 포함, 거리). outOfRange=true면 반경 밖만 */
    @GetMapping("/restaurants")
    public ApiResponse<AdminRestaurantListResponse> getRestaurantList(
            @RequestParam(required = false) Boolean outOfRange) {
        return ApiResponse.success(adminService.getRestaurantList(outOfRange));
    }

    /** 식당 숨김·카테고리 변경 */
    @PatchMapping("/restaurants/{restaurantId}")
    public ApiResponse<AdminRestaurantResponse> updateRestaurant(@PathVariable Long restaurantId,
                                                                 @RequestBody AdminRestaurantUpdateRequest request) {
        return ApiResponse.success(adminService.updateRestaurant(restaurantId, request));
    }

    /** 수정 제안 목록. status: OPEN(기본) / DONE / ALL */
    @GetMapping("/suggestions")
    public ApiResponse<List<AdminSuggestionResponse>> getSuggestionList(
            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminService.getSuggestionList(status));
    }

    /** 수정 제안 처리 상태 변경 */
    @PatchMapping("/suggestions/{suggestionId}")
    public ApiResponse<AdminSuggestionResponse> updateSuggestion(@PathVariable Long suggestionId,
                                                                 @RequestBody AdminSuggestionUpdateRequest request) {
        return ApiResponse.success(adminService.updateSuggestion(suggestionId, request));
    }

    /** 최근 리뷰 (숨김 포함, 최대 100건) */
    @GetMapping("/reviews")
    public ApiResponse<List<AdminReviewResponse>> getReviewList(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(adminService.getReviewList(keyword));
    }

    /** 리뷰 숨김·숨김 해제 */
    @PatchMapping("/reviews/{reviewId}")
    public ApiResponse<AdminReviewResponse> updateReview(@PathVariable Long reviewId,
                                                         @RequestBody AdminReviewUpdateRequest request) {
        return ApiResponse.success(adminService.updateReview(reviewId, request));
    }

    /** 날짜별 구내식당 메뉴 */
    @GetMapping("/cafeteria/menus")
    public ApiResponse<AdminCafeteriaMenusResponse> getCafeteriaMenus(@RequestParam(required = false) String date) {
        return ApiResponse.success(adminService.getCafeteriaMenus(date));
    }

    /** 날짜·끼니의 구내식당 메뉴 교체 저장 (공통 client.js에 put이 없어 POST로 한다) */
    @PostMapping("/cafeteria/menus")
    public ApiResponse<AdminCafeteriaMenuSaveResponse> saveCafeteriaMenus(
            @RequestBody AdminCafeteriaMenuSaveRequest request) {
        return ApiResponse.success(adminService.saveCafeteriaMenus(request));
    }
}
