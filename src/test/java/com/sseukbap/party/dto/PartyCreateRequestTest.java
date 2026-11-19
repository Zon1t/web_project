package com.sseukbap.party.dto;

import com.sseukbap.party.entity.MealType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PartyCreateRequest 입력값 검증 (스프링·DB 없이 실행)
 * 여기서 걸리는 값은 Controller의 @Valid에서 400 INVALID_REQUEST가 된다.
 */
class PartyCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 정상_요청은_통과한다() {
        assertThat(validate(new PartyCreateRequest(1L, MealType.LUNCH, LocalTime.of(12, 0), 4, "같이 드실 분"))).isEmpty();
        assertThat(validate(new PartyCreateRequest(1L, MealType.LUNCH, LocalTime.of(12, 0), 2, null))).isEmpty();
        assertThat(validate(new PartyCreateRequest(1L, MealType.LUNCH, LocalTime.of(12, 0), 12, null))).isEmpty();
    }

    @Test
    void 필수값이_없으면_실패한다() {
        assertThat(validate(new PartyCreateRequest(null, MealType.LUNCH, LocalTime.of(12, 0), 4, null))).isNotEmpty();
        assertThat(validate(new PartyCreateRequest(1L, null, LocalTime.of(12, 0), 4, null))).isNotEmpty();
        assertThat(validate(new PartyCreateRequest(1L, MealType.LUNCH, null, 4, null))).isNotEmpty();
        assertThat(validate(new PartyCreateRequest(1L, MealType.LUNCH, LocalTime.of(12, 0), null, null))).isNotEmpty();
    }

    @Test
    void 정원은_2에서_12() {
        assertThat(validate(new PartyCreateRequest(1L, MealType.LUNCH, LocalTime.of(12, 0), 1, null))).isNotEmpty();
        assertThat(validate(new PartyCreateRequest(1L, MealType.LUNCH, LocalTime.of(12, 0), 13, null))).isNotEmpty();
    }

    @Test
    void 한마디는_50자까지() {
        String fifty = "가".repeat(50);
        assertThat(validate(new PartyCreateRequest(1L, MealType.LUNCH, LocalTime.of(12, 0), 4, fifty))).isEmpty();
        assertThat(validate(new PartyCreateRequest(1L, MealType.LUNCH, LocalTime.of(12, 0), 4, fifty + "가"))).isNotEmpty();
    }

    @Test
    void 실패_문구는_입력값을_확인해_주세요() {
        Set<ConstraintViolation<PartyCreateRequest>> violations =
                validate(new PartyCreateRequest(1L, MealType.LUNCH, LocalTime.of(12, 0), 1, null));
        assertThat(violations).extracting(ConstraintViolation::getMessage).containsOnly("입력값을 확인해 주세요.");
    }

    private Set<ConstraintViolation<PartyCreateRequest>> validate(PartyCreateRequest request) {
        return validator.validate(request);
    }
}
