package com.sseukbap.restaurant.dto;

import com.sseukbap.restaurant.client.NaverPlace;

/** URL 검증에서 찾은 장소 (화면에 보여 주고 사용자가 맞는지 확인) */
public record MapPlaceResponse(
        String name,
        String address,
        Double lat,
        Double lng
) {

    public static MapPlaceResponse from(NaverPlace place) {
        return new MapPlaceResponse(place.name(), place.address(), place.lat(), place.lng());
    }
}
