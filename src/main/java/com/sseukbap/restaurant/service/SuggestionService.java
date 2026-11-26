package com.sseukbap.restaurant.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.dto.SuggestionCreateResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.Suggestion;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.restaurant.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 식당 정보 수정 제안. 기준: docs/API_SPEC.md 5장
 * 제안 처리 방식(누가 반영하는지)은 미정이라 저장만 한다.
 */
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private static final int CONTENT_MAX = 500;

    private final SuggestionRepository suggestionRepository;
    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public SuggestionCreateResponse createSuggestion(Long restaurantId, Long memberId, String content) {
        // 1. 내용: 앞뒤 공백 제거 후 1~500자
        String trimmed = (content == null) ? "" : content.trim();
        if (trimmed.isEmpty() || trimmed.length() > CONTENT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "제안 내용을 입력해 주세요.");
        }

        // 2. 식당 확인
        Restaurant restaurant = restaurantRepository.findVisibleById(restaurantId)   // 숨긴 식당은 404 (A3)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        // 3. 저장 (제안한 회원 = 로그인 회원)
        Member member = memberRepository.getReferenceById(memberId); // SELECT 없이 참조만
        Suggestion suggestion = suggestionRepository.save(Suggestion.create(restaurant, member, trimmed));

        // 4. 생성된 suggestionId
        return new SuggestionCreateResponse(suggestion.getSuggestionId());
    }
}
