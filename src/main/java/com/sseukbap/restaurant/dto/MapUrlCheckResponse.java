package com.sseukbap.restaurant.dto;

/**
 * 네이버 지도 URL 검증 결과. 검증 실패도 화면 안내용이라 200으로 응답하고 reason으로 구분한다.
 *
 * @param reason  실패 이유 (INVALID_FORMAT / DUPLICATE / NOT_FOUND), 정상이면 null
 * @param message 실패 문구, 정상이면 null
 * @param place   찾은 장소, 실패면 null
 */
public record MapUrlCheckResponse(
        boolean valid,
        String reason,
        String message,
        MapPlaceResponse place
) {

    /** 실패 이유와 화면 문구 (API 명세서 표와 같게) */
    public enum Reason {
        INVALID_FORMAT("네이버 지도 URL만 입력할 수 있습니다."),
        DUPLICATE("이미 등록된 식당입니다."),
        NOT_FOUND("존재하지 않는 주소입니다.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    public static MapUrlCheckResponse ok(MapPlaceResponse place) {
        return new MapUrlCheckResponse(true, null, null, place);
    }

    public static MapUrlCheckResponse fail(Reason reason) {
        return new MapUrlCheckResponse(false, reason.name(), reason.message, null);
    }
}
