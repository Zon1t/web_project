package com.sseukbap.party.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대화 전송 요청값 검증 (스프링·DB 없이 실행)
 * 실패 문구는 API_SPEC대로 "메시지를 입력해 주세요."
 */
class PartyChatCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 한_글자부터_200자까지_통과() {
        assertThat(validator.validate(new PartyChatCreateRequest("네"))).isEmpty();
        assertThat(validator.validate(new PartyChatCreateRequest("가".repeat(200)))).isEmpty();
    }

    @Test
    void 빈_값_공백만_200자_초과는_실패() {
        for (String content : new String[]{null, "", "    ", "가".repeat(201)}) {
            Set<ConstraintViolation<PartyChatCreateRequest>> violations =
                    validator.validate(new PartyChatCreateRequest(content));
            assertThat(violations).isNotEmpty();
            assertThat(violations).extracting(ConstraintViolation::getMessage).containsOnly("메시지를 입력해 주세요.");
        }
    }

    @Test
    void 앞뒤_공백은_지우고_200자를_센다() {
        PartyChatCreateRequest request = new PartyChatCreateRequest("  " + "가".repeat(200) + "  ");

        assertThat(request.content()).hasSize(200);
        assertThat(validator.validate(request)).isEmpty();
    }
}
