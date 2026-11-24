package com.sseukbap.restaurant.dto;

/**
 * URL 검증에서 찾은 장소 후보 1곳 (화면에 목록으로 보여 주고 사용자가 고른다, 2026-11-24)
 *
 * @param naverCategory     네이버 분류 원문 (예: "음식점>한식")
 * @param distanceKm        회사(구내식당 좌표)까지 거리, 소수 둘째 자리. 회사 좌표가 없으면 null
 * @param suggestedCategory 네이버 분류로 추천하는 카테고리 값, 판단할 수 없으면 null
 * @param registrable       등록할 수 있으면 true
 * @param blockReason       등록할 수 없는 이유 (NOT_A_RESTAURANT / OUT_OF_RANGE), 가능하면 null
 */
public record PlaceCandidateResponse(
        String name,
        String address,
        Double lat,
        Double lng,
        String naverCategory,
        Double distanceKm,
        String suggestedCategory,
        boolean registrable,
        String blockReason
) {

    /** 등록할 수 없는 이유 */
    public enum BlockReason {
        NOT_A_RESTAURANT,   // 음식점·카페가 아님
        OUT_OF_RANGE        // 회사에서 반경 밖
    }
}
