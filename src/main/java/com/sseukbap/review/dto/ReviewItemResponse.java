package com.sseukbap.review.dto;

import com.sseukbap.review.entity.Review;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리뷰 목록의 리뷰 1건
 *
 * @param tags     선택한 태그 이름 ('#' 없이). 없으면 빈 배열
 * @param imageUrl 사진 주소. 없으면 null
 * @param mine     로그인 회원이 쓴 리뷰인지 ([삭제] 버튼 표시용)
 */
public record ReviewItemResponse(
        Long reviewId,
        String nickname,
        String content,
        List<String> tags,
        String imageUrl,
        LocalDateTime createdAt,
        boolean mine
) {

    public static ReviewItemResponse of(Review review, List<String> tags, Long loginMemberId) {
        return new ReviewItemResponse(
                review.getReviewId(),
                review.getMember().getNickname(),
                review.getContent(),
                tags,
                review.getImagePath(),
                review.getCreatedAt(),
                review.isWrittenBy(loginMemberId));
    }
}
