package com.sseukbap.restaurant.client;

/**
 * 네이버 지역 검색으로 찾은 장소 1곳
 *
 * @param name    장소 이름 (HTML 태그 제거)
 * @param address 도로명 주소, 없으면 지번 주소
 * @param lat     위도 (WGS84)
 * @param lng     경도 (WGS84)
 */
public record NaverPlace(
        String name,
        String address,
        Double lat,
        Double lng
) {
}
