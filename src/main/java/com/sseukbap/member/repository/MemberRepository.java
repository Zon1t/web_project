package com.sseukbap.member.repository;

import com.sseukbap.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    /** 로그인에서 사용 */
    Optional<Member> findByLoginId(String loginId);

    /** 아이디 중복 확인 (대소문자 무시, 2026-11-24). User01이 있으면 user01도 가입할 수 없다 */
    boolean existsByLoginIdIgnoreCase(String loginId);

    /** 로그인: 소문자 통일 전에 대문자로 가입한 계정도 로그인되도록 (2026-11-24) */
    List<Member> findAllByLoginIdIgnoreCase(String loginId);

    /**
     * 아이디로 회원 찾기 (로그인·비밀번호 찾기 공통, 2026-11-24).
     * 소문자로 저장된 아이디를 먼저 찾고, 소문자 통일 전에 대문자를 섞어 가입한 계정이면 대소문자 무시로 한 번 더 찾는다.
     * 대소문자만 다른 계정이 둘 이상이면 어느 쪽인지 알 수 없으므로 빈 값.
     *
     * @param normalizedLoginId 앞뒤 공백 제거 + 소문자로 바꾼 아이디 (AuthService.normalizeLoginId)
     */
    default Optional<Member> findByLoginIdForLogin(String normalizedLoginId) {
        Optional<Member> exact = findByLoginId(normalizedLoginId);
        if (exact.isPresent()) {
            return exact;
        }
        List<Member> candidates = findAllByLoginIdIgnoreCase(normalizedLoginId);
        return (candidates.size() == 1) ? Optional.of(candidates.get(0)) : Optional.empty();
    }
}
