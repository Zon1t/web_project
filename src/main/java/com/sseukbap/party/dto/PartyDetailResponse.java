package com.sseukbap.party.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sseukbap.party.entity.MealParty;
import com.sseukbap.common.enums.MealType;
import com.sseukbap.party.entity.PartyMember;
import com.sseukbap.party.entity.PartyRole;
import com.sseukbap.party.entity.PartyStatus;
import com.sseukbap.party.service.PartyTimePolicy;
import com.sseukbap.restaurant.entity.RestaurantCategory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * GET /api/parties/{partyId} 응답 data
 * FE는 status·myRole·myConfirmed로 버튼을 정한다. (API_SPEC 상세 조회의 버튼 표 참고)
 */
public record PartyDetailResponse(
        Long partyId,
        Long restaurantId,
        String restaurantName,
        RestaurantCategory category,
        LocalDate partyDate,
        MealType mealType,
        @JsonFormat(pattern = "HH:mm")
        LocalTime mealTime,
        int capacity,
        int memberCount,
        int confirmedCount,
        PartyStatus status,
        String memo,
        PartyScheduleResponse schedule,          // 일정 미확정이면 null
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime cancelDeadline,            // 기준 시간 30분 전
        List<PartyMemberResponse> members,
        PartyRole myRole,
        boolean myConfirmed,
        boolean isPrivate                        // 비공개 식사팟인지 (11/25). 초대 코드는 invite-link API로 받는다
) {

    /**
     * @param members       이 팟의 참여자 목록 (참여 순, 방장 포함)
     * @param loginMemberId 로그인 회원 ID
     */
    public static PartyDetailResponse of(MealParty party, List<PartyMember> members, Long loginMemberId) {
        PartyMember me = members.stream()
                .filter(pm -> pm.getMember().getMemberId().equals(loginMemberId))
                .findFirst()
                .orElse(null);

        PartyRole myRole;
        if (party.isHost(loginMemberId)) {
            myRole = PartyRole.HOST;
        } else if (me != null) {
            myRole = PartyRole.MEMBER;
        } else {
            myRole = PartyRole.NONE;
        }

        int confirmedCount = (int) members.stream().filter(PartyMember::isConfirmed).count();

        return new PartyDetailResponse(
                party.getPartyId(),
                party.getRestaurant().getRestaurantId(),
                party.getRestaurant().getName(),
                party.getRestaurant().getCategory(),
                party.getPartyDate(),
                party.getMealType(),
                party.getMealTime(),
                party.getCapacity(),
                members.size(),
                confirmedCount,
                party.getStatus(),
                party.getMemo(),
                PartyScheduleResponse.from(party),
                PartyTimePolicy.getDeadline(party.getPartyDate(), party.getBaseTime()),
                members.stream().map(pm -> PartyMemberResponse.of(party, pm)).toList(),
                myRole,
                me != null && me.isConfirmed(),
                party.isPrivateParty()
        );
    }
}
