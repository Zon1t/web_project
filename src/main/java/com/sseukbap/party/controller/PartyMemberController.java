package com.sseukbap.party.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.party.dto.PartyConfirmResponse;
import com.sseukbap.party.dto.PartyJoinRequest;
import com.sseukbap.party.dto.PartyJoinResponse;
import com.sseukbap.party.dto.PartyLeaveResponse;
import com.sseukbap.party.service.PartyMemberService;
import com.sseukbap.party.service.PartyTimePolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 식사팟 참여 API: 참여하기(1차)·나가기·참여 확정(2차)·확정 취소. 모두 로그인 필요.
 * Body 없음 (참여하기만 비공개 식사팟 초대 코드를 선택으로 받는다). 처리 후 FE는 상세 API를 다시 불러 화면을 갱신한다.
 */
@RestController
@RequestMapping("/api/parties/{partyId}/members")
@RequiredArgsConstructor
public class PartyMemberController {

    private final PartyMemberService partyMemberService;

    /** 참여하기 (1차). 비공개 식사팟은 Body에 inviteCode */
    @PostMapping
    public ApiResponse<PartyJoinResponse> joinParty(@PathVariable Long partyId,
                                                    @Valid @RequestBody(required = false) PartyJoinRequest request,
                                                    @LoginMember Long memberId) {
        String inviteCode = request == null ? null : request.inviteCode();
        return ApiResponse.success(partyMemberService.joinParty(partyId, memberId, inviteCode, PartyTimePolicy.now()));
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
