package com.sseukbap.restaurant.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.restaurant.dto.RankingItemResponse;
import com.sseukbap.restaurant.dto.RankingResponse;
import com.sseukbap.restaurant.repository.RestaurantListRow;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 따봉 랭킹 (C 담당)
 *
 * 집계 쿼리는 B가 만든 RestaurantRepository.findListRows를 그대로 재사용한다.
 * (식당 목록과 같은 기준·같은 정렬이라 화면마다 따봉 수가 달라지지 않는다)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 10;
    private static final int THUMBS_PERIOD_DAYS = 30;   // 최근 30일 (오늘 포함)

    private final RestaurantRepository restaurantRepository;

    /**
     * @param limit 1~10, 없으면 10. 숫자가 아니거나 범위 밖이면 400
     */
    public RankingResponse getRankings(String limit) {
        // 1. limit 검증
        int size = parseLimit(limit);

        // 2. 기간: 오늘 - 29일 ~ 오늘
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(THUMBS_PERIOD_DAYS - 1);

        // 3~5. 30일 따봉 수·리뷰 수 집계 + 정렬 (따봉 수 → 리뷰 수 → 등록 순)
        //      검색어·카테고리 조건 없이 전체 식당을 조회한다. 쿼리는 to를 포함하지 않으므로 다음 날을 넘긴다.
        List<RestaurantListRow> rows = restaurantRepository.findListRows(null, null, from, to.plusDays(1));

        // 6~7. 따봉 1건 이상만, 정렬 순서대로 1위부터, limit만큼
        List<RankingItemResponse> rankings = new ArrayList<>();
        for (RestaurantListRow row : rows) {
            if (rankings.size() >= size) {
                break;
            }
            if (row.getThumbsCount30d() == null || row.getThumbsCount30d() < 1) {
                break;   // 따봉 수 내림차순이라 0이 나오면 뒤는 모두 0
            }
            rankings.add(RankingItemResponse.of(rankings.size() + 1, row));
        }

        return new RankingResponse(from, to, rankings);
    }

    private int parseLimit(String limit) {
        if (limit == null || limit.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int value = Integer.parseInt(limit.trim());
            if (value >= MIN_LIMIT && value <= MAX_LIMIT) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 400 처리
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "조회 조건을 확인해 주세요.");
    }
}
