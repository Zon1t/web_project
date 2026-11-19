package com.sseukbap.party.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.party.dto.PartyThumbsPendingResponse;
import com.sseukbap.party.dto.PartyThumbsResponse;
import com.sseukbap.party.service.PartyThumbsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 따봉 API. 모두 로그인 필요.
 */
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyThumbsController {

    private final PartyThumbsService partyThumbsService;

    /** 따봉 요청 대상 (메인 배너, 최근 1건). 없으면 data = null */
    @GetMapping("/my/thumbs-pending")
    public ApiResponse<PartyThumbsPendingResponse> getThumbsPending(@LoginMember Long memberId) {
        return ApiResponse.success(partyThumbsService.getThumbsPending(memberId));
    }

    /** 따봉 등록 (식사 완료된 팟의 참여 확정자, 1회) */
    @PostMapping("/{partyId}/thumbs")
    public ApiResponse<PartyThumbsResponse> createThumbs(@PathVariable Long partyId,
                                                         @LoginMember Long memberId) {
        return ApiResponse.success(partyThumbsService.createThumbs(partyId, memberId));
    }
}
