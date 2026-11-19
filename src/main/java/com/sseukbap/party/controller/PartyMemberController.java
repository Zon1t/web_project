package com.sseukbap.party.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.party.dto.PartyConfirmResponse;
import com.sseukbap.party.dto.PartyJoinResponse;
import com.sseukbap.party.dto.PartyLeaveResponse;
import com.sseukbap.party.service.PartyMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 식사팟 참여 API: 참여하기(1차)·나가기·참여 확정(2차)·확정 취소. 모두 로그인 필요.
 * Body 없음. 처리 후 FE는 상세 API를 다시 불러 화면을 갱신한다.
 */
@RestController
@RequestMapping("/api/parties/{partyId}/members")
@RequiredArgsConstructor
public class PartyMemberController {

    private final PartyMemberService partyMemberService;

    /** 참여하기 (1차) */
    @PostMapping
    public ApiResponse<PartyJoinResponse> joinParty(@PathVariable Long partyId,
                                                    @LoginMember Long memberId) {
        return ApiResponse.success(partyMemberService.joinParty(partyId, memberId));
    }

    /** 나가기 (방장이면 팟 삭제) */
    @DeleteMapping("/me")
    public ApiResponse<PartyLeaveResponse> leaveParty(@PathVariable Long partyId,
                                                      @LoginMember Long memberId) {
        return ApiResponse.success(partyMemberService.leaveParty(partyId, memberId));
    }

    /** 참여 확정 (2차) */
    @PostMapping("/me/confirm")
    public ApiResponse<PartyConfirmResponse> confirmParty(@PathVariable Long partyId,
                                                          @LoginMember Long memberId) {
        return ApiResponse.success(partyMemberService.confirmParty(partyId, memberId));
    }

    /** 참여 확정 취소 */
    @DeleteMapping("/me/confirm")
    public ApiResponse<PartyConfirmResponse> cancelConfirmParty(@PathVariable Long partyId,
                                                                @LoginMember Long memberId) {
        return ApiResponse.success(partyMemberService.cancelConfirmParty(partyId, memberId));
    }
}
