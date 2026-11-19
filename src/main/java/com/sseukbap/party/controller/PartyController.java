package com.sseukbap.party.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyCreateResponse;
import com.sseukbap.party.dto.PartyDetailResponse;
import com.sseukbap.party.service.PartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 식사팟 API. 모두 로그인 필요 (미로그인은 인터셉터가 401)
 * 처리 로직은 PartyService에 있다. Controller는 요청을 받아 넘기고 ApiResponse로 감싸기만 한다.
 */
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    /** 식사팟 생성 */
    @PostMapping("/create")
    public ApiResponse<PartyCreateResponse> createParty(@Valid @RequestBody PartyCreateRequest request,
                                                        @LoginMember Long memberId) {
        return ApiResponse.success(partyService.createParty(request, memberId));
    }

    /** 식사팟 상세 조회 */
    @GetMapping("/{partyId}")
    public ApiResponse<PartyDetailResponse> getParty(@PathVariable Long partyId,
                                                     @LoginMember Long memberId) {
        return ApiResponse.success(partyService.getParty(partyId, memberId));
    }

    /** 식사팟 삭제 (방장, 소프트 삭제) */
    @DeleteMapping("/{partyId}")
    public ApiResponse<Void> deleteParty(@PathVariable Long partyId,
                                         @LoginMember Long memberId) {
        partyService.deleteParty(partyId, memberId);
        return ApiResponse.success();
    }
}
