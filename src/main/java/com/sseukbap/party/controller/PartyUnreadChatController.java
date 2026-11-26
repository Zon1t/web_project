package com.sseukbap.party.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.party.dto.PartyUnreadChatRequest;
import com.sseukbap.party.dto.PartyUnreadChatResponse;
import com.sseukbap.party.service.PartyUnreadChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 식사팟 대화 '새 메시지' 알림 API (C, 2026-11-26). 로그인 필요
 * 읽은 위치를 Body로 받으므로 POST (조회만 하고 아무것도 바꾸지 않는다)
 */
@RestController
@RequestMapping("/api/parties/my")
@RequiredArgsConstructor
public class PartyUnreadChatController {

    private final PartyUnreadChatService partyUnreadChatService;

    @PostMapping("/unread-chats")
    public ApiResponse<List<PartyUnreadChatResponse>> getUnreadChats(
            @RequestBody(required = false) PartyUnreadChatRequest request,
            @LoginMember Long memberId) {
        return ApiResponse.success(partyUnreadChatService.getUnreadChats(
                memberId, request == null ? null : request.seen()));
    }
}
