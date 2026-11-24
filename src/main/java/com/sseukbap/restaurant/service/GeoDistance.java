package com.sseukbap.restaurant.service;

/**
 * 두 좌표(위도·경도) 사이의 직선거리 (하버사인 공식). 식당 등록 반경 검사에 쓴다. (2026-11-24)
 * 화면의 [가까운 순] 정렬은 같은 공식을 js/utils.js에서 계산한다.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() {
    }

    /** 두 지점 사이 거리(km) */
    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 소수 둘째 자리로 반올림 (응답용) */
    public static double round2(double km) {
        return Math.round(km * 100) / 100.0;
    }
}
