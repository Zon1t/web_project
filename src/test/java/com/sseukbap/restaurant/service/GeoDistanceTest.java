package com.sseukbap.restaurant.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 두 좌표 사이 거리 */
class GeoDistanceTest {

    @Test
    void 같은_지점은_0() {
        assertThat(GeoDistance.km(37.5503, 127.0731, 37.5503, 127.0731)).isZero();
    }

    @Test
    void 세종대에서_군자역_스타벅스는_약_0_9km() {
        // 2026-11-24 네이버 응답 좌표: 스타벅스 군자역점 (127.0791098, 37.5570260)
        double km = GeoDistance.km(37.5503, 127.0731, 37.5570260, 127.0791098);
        assertThat(km).isCloseTo(0.91, within(0.05));
    }

    @Test
    void 서울에서_대구는_200km_이상() {
        assertThat(GeoDistance.km(37.5503, 127.0731, 35.8563052, 128.5885117)).isGreaterThan(200);
    }

    @Test
    void 소수_둘째_자리_반올림() {
        assertThat(GeoDistance.round2(0.8149)).isEqualTo(0.81);
        assertThat(GeoDistance.round2(0.8151)).isEqualTo(0.82);
    }
}
