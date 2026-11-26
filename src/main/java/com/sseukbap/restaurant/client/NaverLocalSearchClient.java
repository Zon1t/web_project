package com.sseukbap.restaurant.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 네이버 지역 검색 (NAVER API HUB) 호출.
 *
 * - GET https://naverapihub.apigw.ntruss.com/search/v1/local?query=검색어&display=5 (네이버 최대 5개)
 * - 키: application-local.yml의 naver.client-id / naver.client-secret (Git에 올리지 않는다)
 * - 응답 좌표 mapx(경도)·mapy(위도)는 WGS84 값 × 10^7 정수 문자열 → 소수로 바꾼다.
 * - 호출 실패(키 없음, 시간 초과, 오류 응답)는 502 MAP_API_ERROR
 */
@Slf4j
@Component
public class NaverLocalSearchClient {

    private static final String SEARCH_URL = "https://naverapihub.apigw.ntruss.com/search/v1/local";
    private static final int TIMEOUT_MILLIS = 3000;
    private static final int COORDINATE_SCALE = 7; // 10^7
    private static final int MAX_DISPLAY = 5;      // 지역 검색 API가 한 번에 주는 최대 개수

    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final String clientId;
    private final String clientSecret;

    public NaverLocalSearchClient(@Value("${naver.client-id:}") String clientId,
                                  @Value("${naver.client-secret:}") String clientSecret) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(TIMEOUT_MILLIS);

        this.restClient = RestClient.builder()
                .baseUrl(SEARCH_URL)
                .requestFactory(requestFactory)
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** 검색어로 찾은 장소 (네이버 순서 그대로, 최대 5개). 결과가 없으면 빈 목록 */
    public List<NaverPlace> search(String query) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            log.warn("네이버 검색 키가 없습니다. application-local.yml의 naver.client-id / client-secret을 확인하세요.");
            throw new BusinessException(ErrorCode.MAP_API_ERROR);
        }

        // 네이버는 JSON 본문을 Content-Type: text/html로 보내서 Spring이 JSON으로 알아보지 못한다.
        // → 글자(String)로 받은 뒤 직접 JSON으로 읽는다.
        LocalSearchResponse response;
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)   // UTF-8로 인코딩된다
                            .queryParam("display", MAX_DISPLAY)
                            .build())
                    .header("X-NCP-APIGW-API-KEY-ID", clientId)
                    .header("X-NCP-APIGW-API-KEY", clientSecret)
                    .retrieve()
                    .body(String.class);
            response = (body == null) ? null : jsonMapper.readValue(body, LocalSearchResponse.class);
        } catch (RestClientException | JacksonException e) {
            log.warn("네이버 지역 검색 호출 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.MAP_API_ERROR);
        }

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .map(NaverLocalSearchClient::toPlace)
                .flatMap(Optional::stream)
                .toList();
    }

    /** 검색 결과 1건 → NaverPlace. 좌표가 잘못됐으면 빈 값 (테스트를 위해 package-private) */
    static Optional<NaverPlace> toPlace(LocalSearchItem item) {
        try {
            String address = isBlank(item.roadAddress()) ? item.address() : item.roadAddress();
            double lng = new BigDecimal(item.mapx().trim()).movePointLeft(COORDINATE_SCALE).doubleValue();
            double lat = new BigDecimal(item.mapy().trim()).movePointLeft(COORDINATE_SCALE).doubleValue();
            String category = (item.category() == null) ? "" : item.category().trim();
            return Optional.of(new NaverPlace(stripTags(item.title()), address, lat, lng, category));
        } catch (NumberFormatException | NullPointerException e) {
            return Optional.empty();
        }
    }

    /** "<b>대쿠이</b> 본점" → "대쿠이 본점" (검색어 강조 태그와 HTML 특수문자 처리) */
    static String stripTags(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("<[^>]*>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ===== 네이버 응답 모양 (필요한 필드만) =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocalSearchResponse(List<LocalSearchItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocalSearchItem(String title, String category, String address, String roadAddress, String mapx, String mapy) {
    }
}
