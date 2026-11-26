package com.sseukbap.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 식당 정보 수정 제안 요청
 *
 * 비었는지는 여기서(@Valid), 앞뒤 공백을 뺀 길이(1~500자)는 Service에서 확인한다.
 *
 * @param content 제안 내용 (예: "카테고리가 일식이 아니라 한식이에요")
 */
public record SuggestionCreateRequest(
        @NotBlank(message = "제안 내용을 입력해 주세요.") String content
) {
}
