package com.sseukbap.restaurant.dto;

import java.util.List;

/**
 * 네이버 지도 URL 검증 결과. 검증 실패도 화면 안내용이라 200으로 응답하고 reason으로 구분한다.
 * (2026-11-24 변경: 찾은 장소 1곳(place) → 후보 목록(candidates), 등록 반경(radiusKm) 추가)
 *
 * @param reason     실패 이유 (INVALID_FORMAT / DUPLICATE / NOT_FOUND / NO_ELIGIBLE_PLACE), 정상이면 null
 * @param message    실패 문구, 정상이면 null
 * @param radiusKm   회사에서 등록할 수 있는 거리(km)
 * @param candidates 찾은 후보 (최대 5개). 형식·중복·검색 실패면 빈 목록
 */
public record MapUrlCheckResponse(
        boolean valid,
        String reason,
        String message,
        double radiusKm,
        List<PlaceCandidateResponse> candidates
) {

    /** 실패 이유와 화면 문구 (API 명세서 표와 같게) */
    public enum Reason {
        INVALID_FORMAT("네이버 지도 URL만 입력할 수 있습니다."),
        DUPLICATE("이미 등록된 식당입니다."),
        NOT_FOUND("존재하지 않는 주소입니다."),
        NO_ELIGIBLE_PLACE("등록할 수 있는 장소가 없습니다.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    /** 후보를 담아 돌려준다. 등록 가능한 후보가 하나도 없으면 NO_ELIGIBLE_PLACE */
    public static MapUrlCheckResponse of(double radiusKm, List<PlaceCandidateResponse> candidates) {
        boolean anyRegistrable = candidates.stream().anyMatch(PlaceCandidateResponse::registrable);
        if (anyRegistrable) {
            return new MapUrlCheckResponse(true, null, null, radiusKm, candidates);
        }
        Reason reason = Reason.NO_ELIGIBLE_PLACE;
        return new MapUrlCheckResponse(false, reason.name(), reason.message, radiusKm, candidates);
    }

    public static MapUrlCheckResponse fail(Reason reason, double radiusKm) {
        return new MapUrlCheckResponse(false, reason.name(), reason.message, radiusKm, List.of());
    }
}
