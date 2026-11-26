package com.sseukbap.restaurant.controller;

import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.restaurant.dto.RouletteCandidatesResponse;
import com.sseukbap.restaurant.dto.RouletteSpinRequest;
import com.sseukbap.restaurant.dto.RouletteSpinResponse;
import com.sseukbap.restaurant.service.RouletteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메뉴 룰렛 API (API_SPEC 4장, C). 로그인 필요 (LoginCheckInterceptor)
 */
@RestController
@RequestMapping("/api/restaurants/roulette")
@RequiredArgsConstructor
public class RouletteController {

    private final RouletteService rouletteService;

    @GetMapping("/candidates")
    public ApiResponse<RouletteCandidatesResponse> candidates(@RequestParam(required = false) String category) {
        return ApiResponse.success(rouletteService.getCandidates(category));
    }

    @PostMapping("/spin")
    public ApiResponse<RouletteSpinResponse> spin(@RequestBody(required = false) RouletteSpinRequest request) {
        return ApiResponse.success(rouletteService.spin(request));
    }
}
