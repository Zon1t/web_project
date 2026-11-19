package com.sseukbap.restaurant.dto;

/**
 * 관심 등록·해제 응답
 *
 * @param interested    지금 내가 관심 등록한 상태인지
 * @param interestCount 바뀐 뒤의 식당 관심 수
 */
public record InterestStatusResponse(
        boolean interested,
        long interestCount
) {
}
