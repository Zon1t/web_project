package com.sseukbap.common.interceptor;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/admin/** 요청이 관리자(MEMBER.ROLE = 'ADMIN')의 것인지 확인한다. (2026-11-24 A3)
 *
 * - 로그인 확인은 LoginCheckInterceptor가 먼저 한다. (WebConfig에서 그 다음 순서로 등록)
 * - 역할은 세션에 저장하지 않고 요청마다 DB에서 읽는다. → SQL로 역할을 바꾸면 바로 반영된다.
 * - 관리자가 아니거나 탈퇴한 회원이면 403 FORBIDDEN.
 */
@Component
@RequiredArgsConstructor
public class AdminCheckInterceptor implements HandlerInterceptor {

    private final MemberRepository memberRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object memberId = (session == null) ? null : session.getAttribute(SessionConst.LOGIN_MEMBER_ID);
        if (!(memberId instanceof Long id)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        boolean admin = memberRepository.findById(id)
                .filter(member -> !member.isWithdrawn())
                .map(Member::isAdmin)
                .orElse(false);
        if (!admin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
