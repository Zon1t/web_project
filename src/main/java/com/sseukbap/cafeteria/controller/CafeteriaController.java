package com.sseukbap.cafeteria.controller;

import com.sseukbap.cafeteria.dto.CafeteriaTodayResponse;
import com.sseukbap.cafeteria.service.CafeteriaMenuService;
import com.sseukbap.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구내식당 API (C 담당). 로그인 필요 (LoginCheckInterceptor가 확인)
 */
@RestController
@RequestMapping("/api/cafeteria")
@RequiredArgsConstructor
public class CafeteriaController {

    private final CafeteriaMenuService cafeteriaMenuService;

    /**
     * 오늘의 구내식당 메뉴. 수집 실패·미운영일도 200이며 status(OPEN/CLOSED/UNAVAILABLE)로 구분한다.
     */
    @GetMapping("/menus/today")
    public ApiResponse<CafeteriaTodayResponse> today() {
        return ApiResponse.success(cafeteriaMenuService.getTodayMenu());
    }
}
