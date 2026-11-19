package com.sseukbap.restaurant.client;

import com.sseukbap.restaurant.client.NaverLocalSearchClient.LocalSearchItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 네이버 검색 응답 1건 → NaverPlace 변환 (실제 호출 없음) */
class NaverLocalSearchClientTest {

    @Test
    void 이름의_태그를_지우고_좌표를_소수로_바꾼다() {
        LocalSearchItem item = new LocalSearchItem(
                "<b>대쿠이</b> 본점", "대구광역시 남구 대명동 1807-25", "대구광역시 남구 중앙대로51길 38-2",
                "1285885117", "358563052");

        NaverPlace place = NaverLocalSearchClient.toPlace(item).orElseThrow();

        assertThat(place.name()).isEqualTo("대쿠이 본점");
        assertThat(place.address()).isEqualTo("대구광역시 남구 중앙대로51길 38-2"); // 도로명 주소 우선
        assertThat(place.lng()).isEqualTo(128.5885117);
        assertThat(place.lat()).isEqualTo(35.8563052);
    }

    @Test
    void 도로명_주소가_없으면_지번_주소() {
        LocalSearchItem item = new LocalSearchItem("가게", "서울특별시 광진구 군자동 98", "", "1270738840", "375514705");

        assertThat(NaverLocalSearchClient.toPlace(item).orElseThrow().address()).isEqualTo("서울특별시 광진구 군자동 98");
    }

    @Test
    void 좌표가_잘못되면_빈_값() {
        LocalSearchItem item = new LocalSearchItem("가게", "주소", "주소", "abc", null);

        assertThat(NaverLocalSearchClient.toPlace(item)).isEmpty();
    }

    @Test
    void HTML_특수문자를_글자로_바꾼다() {
        assertThat(NaverLocalSearchClient.stripTags("<b>A&amp;B</b> &lt;본점&gt;")).isEqualTo("A&B <본점>");
    }
}
