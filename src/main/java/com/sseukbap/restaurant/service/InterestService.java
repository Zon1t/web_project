package com.sseukbap.restaurant.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.dto.InterestStatusResponse;
import com.sseukbap.restaurant.entity.Interest;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.InterestRepository;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관심 등록·해제. 기준: docs/API_SPEC.md 5장 (POST·DELETE /api/restaurants/{restaurantId}/interest)
 * 관심 수는 랭킹에 반영하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class InterestService {

    private final InterestRepository interestRepository;
    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;

    /** 관심 등록 */
    @Transactional
    public InterestStatusResponse createInterest(Long restaurantId, Long memberId) {
        // 1. 식당 확인
        Restaurant restaurant = restaurantRepository.findVisibleById(restaurantId)   // 숨긴 식당은 404 (A3)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        // 2. 이미 등록했으면 중단
        if (interestRepository.existsByRestaurant_RestaurantIdAndMember_MemberId(restaurantId, memberId)) {
            throw new BusinessException(ErrorCode.ALREADY_INTERESTED);
        }

        // 3. 저장. 같은 회원의 요청 두 개가 동시에 2번을 통과하면 DB의 UK_INTEREST가 막아 준다.
        //    saveAndFlush로 바로 INSERT 해서 그 오류를 여기서 잡고 409로 바꾼다.
        Member member = memberRepository.getReferenceById(memberId); // 로그인 회원 (SELECT 없이 참조만)
        try {
            interestRepository.saveAndFlush(Interest.create(restaurant, member));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_INTERESTED);
        }

        // 4. 바뀐 관심 수
        return new InterestStatusResponse(true, interestRepository.countByRestaurant_RestaurantId(restaurantId));
    }

    /** 관심 해제. 등록 내역이 없으면(없는 식당 포함) INTEREST_NOT_FOUND */
    @Transactional
    public InterestStatusResponse deleteInterest(Long restaurantId, Long memberId) {
        // 1. 내 관심 조회
        Interest interest = interestRepository
                .findByRestaurant_RestaurantIdAndMember_MemberId(restaurantId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTEREST_NOT_FOUND));

        // 2. 삭제 (count 전에 DELETE가 반영되도록 flush)
        interestRepository.delete(interest);
        interestRepository.flush();

        // 3. 바뀐 관심 수
        return new InterestStatusResponse(false, interestRepository.countByRestaurant_RestaurantId(restaurantId));
    }
}
