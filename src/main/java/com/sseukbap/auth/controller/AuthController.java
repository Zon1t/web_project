package com.sseukbap.auth.controller;

import com.sseukbap.auth.dto.CheckEmailResponse;
import com.sseukbap.auth.dto.CheckIdResponse;
import com.sseukbap.auth.dto.CheckNicknameResponse;
import com.sseukbap.auth.dto.LoginRequest;
import com.sseukbap.auth.dto.MemberInfoResponse;
import com.sseukbap.auth.dto.SignupRequest;
import com.sseukbap.auth.dto.SignupResponse;
import com.sseukbap.auth.service.AuthService;
import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.common.interceptor.LoginMember;
import com.sseukbap.common.interceptor.SessionConst;
import com.sseukbap.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 아이디 중복 확인 (로그인 불필요) */
    @GetMapping("/check-id")
    public ApiResponse<CheckIdResponse> checkId(@RequestParam(required = false) String loginId) {
        // required = false: 값이 없을 때도 Service에서 API별 문구로 400을 내려주기 위해
        return ApiResponse.success(authService.checkLoginId(loginId));
    }

    /** 이메일 중복 확인 (로그인 불필요, 2026-11-25 T3) */
    @GetMapping("/check-email")
    public ApiResponse<CheckEmailResponse> checkEmail(@RequestParam(required = false) String email) {
        // required = false: 값이 없을 때도 Service에서 API별 문구로 400을 내려주기 위해
        return ApiResponse.success(authService.checkEmail(email));
    }

    /** 닉네임 중복 확인 (로그인 불필요) */
    @GetMapping("/check-nickname")
    public ApiResponse<CheckNicknameResponse> checkNickname(@RequestParam(required = false) String nickname) {
        return ApiResponse.success(authService.checkNickname(nickname));
    }

    /** 회원가입 (로그인 불필요) */
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    /** 로그인 (로그인 불필요). 성공 시 JSESSIONID 쿠키가 발급된다 */
    @PostMapping("/login")
    public ApiResponse<MemberInfoResponse> login(@Valid @RequestBody LoginRequest request,
                                                 HttpServletRequest httpRequest) {
        // 1~3. 아이디(또는 이메일)·비밀번호 확인 (실패 시 401 LOGIN_FAILED)
        MemberInfoResponse member = authService.login(request);

        // 4. 기존 세션이 있으면 무효화하고 새 세션 생성
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = httpRequest.getSession(true);

        // 5. 세션에 회원 ID 저장
        newSession.setAttribute(SessionConst.LOGIN_MEMBER_ID, member.memberId());

        // 6. 회원 정보 반환
        return ApiResponse.success(member);
    }

    /** 로그아웃. 세션이 없으면 인터셉터가 401로 막는다 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.success();
    }

    /** 로그인 사용자 조회. 모든 화면의 공통 헤더가 진입 시 호출한다 */
    @GetMapping("/me")
    public ApiResponse<MemberInfoResponse> me(@LoginMember Long memberId, HttpServletRequest httpRequest) {
        // 1. 세션 확인은 인터셉터가 처리함
        // 2. 회원이 DB에 없으면(탈퇴 등) 세션을 무효화하고 401
        Optional<MemberInfoResponse> member = authService.getMe(memberId);
        if (member.isEmpty()) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 3. 회원 정보 반환
        return ApiResponse.success(member.get());
    }
}
