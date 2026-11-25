package com.sseukbap.auth.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 회사 이메일 = 아이디 (2026-11-25, T3).
 * 형식은 EmailPolicy로 보고, 도메인이 auth.allowed-email-domains(기본 samsung.com)에 있어야 한다.
 * 회원가입·이메일 중복 확인·계정 설정의 이메일 변경이 같이 쓴다.
 */
@Component
public class CompanyEmailPolicy {

    private final List<String> domains;

    public CompanyEmailPolicy(@Value("${auth.allowed-email-domains:samsung.com}") List<String> domains) {
        this.domains = domains.stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .toList();
    }

    /** 앞뒤 공백 제거 + 소문자, 형식·도메인 확인. 형식이 틀리면 400 INVALID_REQUEST, 도메인이 다르면 400 INVALID_EMAIL_DOMAIN */
    public String normalizeAndValidate(String email) {
        String normalized = EmailPolicy.normalizeAndValidate(email);
        String domain = normalized.substring(normalized.lastIndexOf('@') + 1);
        if (!domains.contains(domain)) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_DOMAIN,
                    "회사 이메일(" + domainLabel() + ")만 쓸 수 있습니다.");
        }
        return normalized;
    }

    /** 화면 안내용: "@samsung.com" (여러 개면 쉼표로) */
    public String domainLabel() {
        return domains.stream().map(d -> "@" + d).collect(Collectors.joining(", "));
    }
}
