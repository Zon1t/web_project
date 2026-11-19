package com.sseukbap.member.repository;

import com.sseukbap.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    /** 로그인에서 사용 */
    Optional<Member> findByLoginId(String loginId);
}
