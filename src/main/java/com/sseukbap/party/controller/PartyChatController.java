package com.sseukbap.party.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.party.dto.PartyChatCreateRequest;
import com.sseukbap.party.dto.PartyChatCreateResponse;
import com.sseukbap.party.dto.PartyChatListResponse;
import com.sseukbap.party.service.PartyChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 식사팟 대화 API (참여자 전용). FE는 3초마다 조회 API를 호출한다(폴링).
 */
@RestController
@RequestMapping("/api/parties/{partyId}/chats")
@RequiredArgsConstructor
public class PartyChatController {

    private final PartyChatService partyChatService;

    /** 대화 조회. afterId가 없으면 전체, 있으면 그보다 새 메시지만 (최대 200개) */
    @GetMapping
    public ApiResponse<PartyChatListResponse> getChatList(@PathVariable Long partyId,
                                                          @RequestParam(required = false) Long afterId,
                                                          @LoginMember Long memberId) {
        return ApiResponse.success(partyChatService.getChatList(partyId, afterId, memberId));
    }

    /** 대화 전송 */
    @PostMapping
    public ApiResponse<PartyChatCreateResponse> createChat(@PathVariable Long partyId,
                                                           @Valid @RequestBody PartyChatCreateRequest request,
                                                           @LoginMember Long memberId) {
        return ApiResponse.success(partyChatService.createChat(partyId, request, memberId));
    }
}
