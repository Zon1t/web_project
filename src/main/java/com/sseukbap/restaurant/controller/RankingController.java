package com.sseukbap.restaurant.controller;

import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.restaurant.dto.RankingResponse;
import com.sseukbap.restaurant.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 따봉 랭킹 API (C 담당). 식당 API(B)와 주소 앞부분(/api/restaurants)만 같고 파일은 따로 둔다.
 * 로그인 필요 (LoginCheckInterceptor가 확인)
 */
@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    /** 따봉 랭킹 조회. 랭킹 화면 limit=10, 메인 TOP 3는 limit=3 */
    @GetMapping("/rankings")
    public ApiResponse<RankingResponse> rankings(@RequestParam(required = false) String limit) {
        // String으로 받는 이유: 숫자가 아닌 값도 API별 문구("조회 조건을 확인해 주세요.")로 400을 내려주기 위해
        return ApiResponse.success(rankingService.getRankings(limit));
    }
}
