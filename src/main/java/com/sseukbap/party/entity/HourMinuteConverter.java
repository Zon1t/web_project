package com.sseukbap.party.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 자바 LocalTime ↔ DB 문자열 'HH:mm' 변환기
 * MEAL_PARTY.MEAL_TIME, CONFIRMED_TIME 컬럼이 VARCHAR2(5)라서 사용한다.
 * 사용법: 필드에 @Convert(converter = HourMinuteConverter.class)
 */
@Converter
public class HourMinuteConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convertToDatabaseColumn(LocalTime time) {
        return time == null ? null : time.format(FORMAT);
    }

    @Override
    public LocalTime convertToEntityAttribute(String value) {
        return value == null ? null : LocalTime.parse(value, FORMAT);
    }
}
