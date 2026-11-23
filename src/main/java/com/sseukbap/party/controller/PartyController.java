package com.sseukbap.party.controller;

import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.response.ApiResponse;
import com.sseukbap.party.dto.PartyActionPendingResponse;
import com.sseukbap.party.dto.PartyCreateRequest;
import com.sseukbap.party.dto.PartyCreateResponse;
import com.sseukbap.party.dto.PartyDetailResponse;
import com.sseukbap.party.dto.PartyInviteLinkResponse;
import com.sseukbap.party.dto.PartyListResponse;
import com.sseukbap.party.dto.PartyMyTodayResponse;
import com.sseukbap.party.dto.PartyScheduleUpdateRequest;
import com.sseukbap.party.dto.PartyScheduleUpdateResponse;
import com.sseukbap.party.dto.PartyStatusUpdateRequest;
import com.sseukbap.party.dto.PartyStatusUpdateResponse;
import com.sseukbap.party.service.PartyHostService;
import com.sseukbap.party.service.PartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 식사팟 API. 모두 로그인 필요 (미로그인은 인터셉터가 401)
 * 처리 로직은 PartyService(생성·조회·삭제)와 PartyHostService(방장의 일정·마감)에 있다.
 * Controller는 요청을 받아 넘기고 ApiResponse로 감싸기만 한다.
 */
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;
    private final PartyHostService partyHostService;

    /** 식사팟 생성 */
    @PostMapping("/create")
    public ApiResponse<PartyCreateResponse> createParty(@Valid @RequestBody PartyCreateRequest request,
                                                        @LoginMember Long memberId) {
        return ApiResponse.success(partyService.createParty(request, memberId));
    }

    /**
     * 식사팟 목록 (date 날짜, 없으면 오늘. FINISHED·DELETED 제외)
     * 파라미터는 모두 선택값. 잘못된 값이면 Service에서 400 "조회 조건을 확인해 주세요."
     * (문자열로 받는 이유: 타입이 틀린 값도 같은 문구로 응답하기 위해)
     */
    @GetMapping("/list")
    public ApiResponse<PartyListResponse> getPartyList(@RequestParam(required = false) String date,
                                                       @RequestParam(required = false) String mealType,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String excludeJoined,
                                                       @RequestParam(required = false) String limit,
                                                       @LoginMember Long memberId) {
        return ApiResponse.success(partyService.getPartyList(date, mealType, status, excludeJoined, limit, memberId));
    }

    /** 내가 참여 중인 식사팟 (오늘 ~ 14일 후 중 가까운 2개, 메인 카드용) */
    @GetMapping("/my/today")
    public ApiResponse<List<PartyMyTodayResponse>> getMyTodayPartyList(@LoginMember Long memberId) {
        return ApiResponse.success(partyService.getMyTodayPartyList(memberId));
    }

    /** 내가 할 일이 있는 식사팟 (헤더 종 알림: 방장 일정 정하기 / 참석 확정). 없으면 빈 배열 */
    @GetMapping("/my/action-pending")
    public ApiResponse<List<PartyActionPendingResponse>> getActionPendingList(@LoginMember Long memberId) {
        return ApiResponse.success(partyService.getActionPendingList(memberId));
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

    /** 초대 링크 조회 (참여자, 모집 중인 팟만) */
    @GetMapping("/{partyId}/invite-link")
    public ApiResponse<PartyInviteLinkResponse> getInviteLink(@PathVariable Long partyId,
                                                              @LoginMember Long memberId) {
        return ApiResponse.success(partyService.getInviteLink(partyId, memberId));
    }

    /** 일정 확정·수정 (방장). 수정이면 모든 참여자의 확정이 초기화된다 */
    @PatchMapping("/{partyId}/schedule")
    public ApiResponse<PartyScheduleUpdateResponse> updateSchedule(@PathVariable Long partyId,
                                                                   @Valid @RequestBody PartyScheduleUpdateRequest request,
                                                                   @LoginMember Long memberId) {
        return ApiResponse.success(partyHostService.updateSchedule(partyId, request, memberId));
    }

    /** 모집 마감·재개 (방장). action = CLOSE / REOPEN */
    @PatchMapping("/{partyId}/status")
    public ApiResponse<PartyStatusUpdateResponse> updateStatus(@PathVariable Long partyId,
                                                               @Valid @RequestBody PartyStatusUpdateRequest request,
                                                               @LoginMember Long memberId) {
        return ApiResponse.success(partyHostService.updateStatus(partyId, request, memberId));
    }
}
