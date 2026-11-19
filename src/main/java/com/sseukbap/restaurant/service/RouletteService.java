package com.sseukbap.restaurant.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.restaurant.dto.RouletteCandidateResponse;
import com.sseukbap.restaurant.dto.RouletteCandidatesResponse;
import com.sseukbap.restaurant.dto.RouletteSpinRequest;
import com.sseukbap.restaurant.dto.RouletteSpinResponse;
import com.sseukbap.restaurant.dto.RouletteWinnerResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.restaurant.repository.RouletteQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 메뉴 룰렛 (API_SPEC 4장, C)
 * 구내식당(CAFETERIA)은 '전체'를 포함해 항상 후보에서 제외한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouletteService {

    private static final int MIN_CANDIDATES = 2;
    private static final int MAX_CANDIDATES = 8;
    /** 최근 30일 따봉 집계 기간 (랭킹과 같은 기준: 오늘 포함 30일) */
    private static final int THUMBS_PERIOD_DAYS = 30;

    private final RouletteQueryRepository rouletteQueryRepository;
    private final RestaurantRepository restaurantRepository;

    /**
     * 룰렛 후보 조회
     *
     * @param category 카테고리 값. null·빈 값이면 전체
     */
    public RouletteCandidatesResponse getCandidates(String category) {
        // 1. category 검증 (없는 값, 구내식당이면 400)
        String categoryValue = parseCategory(category);

        // 2~3. 무작위 최대 8곳 + 전체 대상 수
        List<RouletteCandidateResponse> candidates = rouletteQueryRepository
                .findRandomCandidates(categoryValue, MAX_CANDIDATES).stream()
                .map(row -> new RouletteCandidateResponse(row.getRestaurantId(), row.getName()))
                .toList();
        long totalCount = rouletteQueryRepository.countTargets(categoryValue);

        return new RouletteCandidatesResponse(
                categoryValue, totalCount, candidates, candidates.size() >= MIN_CANDIDATES);
    }

    /** 룰렛 실행: 후보 중 1곳을 무작위로 선정 */
    public RouletteSpinResponse spin(RouletteSpinRequest request) {
        // 1. 개수(2~8)·중복 검증
        List<Long> ids = request == null ? null : request.candidateIds();
        if (ids == null
                || ids.size() < MIN_CANDIDATES
                || ids.size() > MAX_CANDIDATES
                || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "룰렛 후보를 확인해 주세요.");
        }

        // 2. 없는 식당(삭제 등)·구내식당은 빼고, 2곳 미만이면 중단
        List<Restaurant> spinnable = rouletteQueryRepository.findSpinnable(ids);
        if (spinnable.size() < MIN_CANDIDATES) {
            throw new BusinessException(ErrorCode.NOT_ENOUGH_CANDIDATES);
        }

        // 3. 무작위 1곳
        Restaurant winner = spinnable.get(ThreadLocalRandom.current().nextInt(spinnable.size()));

        // 4. 결과 카드용 요약 (최근 30일 따봉 수는 B의 countThumbsUp 재사용)
        LocalDate today = LocalDate.now();
        long thumbs = restaurantRepository.countThumbsUp(
                winner.getRestaurantId(), today.minusDays(THUMBS_PERIOD_DAYS - 1), today.plusDays(1));

        return new RouletteSpinResponse(
                winner.getRestaurantId(),
                new RouletteWinnerResponse(winner.getRestaurantId(), winner.getName(), winner.getCategory(), thumbs));
    }

    /** "KOREAN" → "KOREAN", 빈 값 → null(전체). 없는 값이나 CAFETERIA면 400 */
    private String parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            RestaurantCategory value = RestaurantCategory.valueOf(category.trim());
            if (value != RestaurantCategory.CAFETERIA) {
                return value.name();
            }
        } catch (IllegalArgumentException ignored) {
            // 아래에서 400
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "카테고리를 확인해 주세요.");
    }
}
