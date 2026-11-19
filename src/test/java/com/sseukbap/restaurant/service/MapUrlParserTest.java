package com.sseukbap.restaurant.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네이버 지도 URL → 표준 URL. (naver.me 단축 링크는 네트워크가 필요해서 여기서 확인하지 않는다)
 */
class MapUrlParserTest {

    private final MapUrlParser parser = new MapUrlParser();

    @Test
    void 검색_결과에서_복사한_긴_링크에서_장소ID를_찾는다() {
        String url = "https://map.naver.com/p/search/%EB%8C%80%EC%BF%A0%EC%9D%B4/place/1132965955"
                + "?c=15.00,0,0,0,dh&placePath=%2Fhome%3Fbk_query%3D%EB%8C%80%EC%BF%A0%EC%9D%B4";

        assertThat(parser.toStandardUrl(url)).hasValue("https://map.naver.com/p/entry/place/1132965955");
    }

    @Test
    void 표준_URL과_모바일_지도_링크도_같은_표준_URL이_된다() {
        String expected = "https://map.naver.com/p/entry/place/1132965955";

        assertThat(parser.toStandardUrl("https://map.naver.com/p/entry/place/1132965955")).hasValue(expected);
        assertThat(parser.toStandardUrl("  https://map.naver.com/p/entry/place/1132965955?c=15  ")).hasValue(expected);
        assertThat(parser.toStandardUrl("https://m.map.naver.com/p/entry/place/1132965955/home")).hasValue(expected);
        assertThat(parser.toStandardUrl("http://MAP.NAVER.COM/p/entry/place/1132965955")).hasValue(expected);
    }

    @Test
    void 네이버_지도가_아니거나_장소ID가_없으면_빈_값() {
        assertThat(parser.toStandardUrl("https://map.kakao.com/?itemId=12345")).isEmpty();
        assertThat(parser.toStandardUrl("https://map.naver.com/p/search/대쿠이")).isEmpty();
        assertThat(parser.toStandardUrl("https://map.naver.com.evil.com/p/entry/place/1132965955")).isEmpty();
        assertThat(parser.toStandardUrl("javascript:alert(1)")).isEmpty();
        assertThat(parser.toStandardUrl("map.naver.com/p/entry/place/1132965955")).isEmpty(); // http(s) 없음
        assertThat(parser.toStandardUrl("아무 글자")).isEmpty();
        assertThat(parser.toStandardUrl("")).isEmpty();
        assertThat(parser.toStandardUrl(null)).isEmpty();
    }
}
