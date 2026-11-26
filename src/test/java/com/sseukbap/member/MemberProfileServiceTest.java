package com.sseukbap.member;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.dto.MemberProfileResponse;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.member.service.MemberProfileService;
import com.sseukbap.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 프로필 카드 (2026-11-25). 회원은 테스트 안에서 만든다 */
@SpringBootTest
@Transactional
class MemberProfileServiceTest {

    @Autowired MemberProfileService memberProfileService;
    @Autowired MemberRepository memberRepository;
    @Autowired TestData data;

    @Test
    void 닉네임_아이디_회사_이메일_근무지를_준다() {
        Member member = data.member();
        member.changeEmail(member.getLoginId() + "@samsung.com");
        memberRepository.flush();

        MemberProfileResponse profile = memberProfileService.getProfile(member.getMemberId());

        assertThat(profile.nickname()).isEqualTo(member.getNickname());
        assertThat(profile.loginId()).isEqualTo(member.getLoginId());
        assertThat(profile.email()).isEqualTo(member.getLoginId() + "@samsung.com");
        assertThat(profile.workplace()).isEqualTo(MemberProfileResponse.WORKPLACE);
        assertThat(profile.joinedAt()).isNotNull();
    }

    @Test
    void 없거나_탈퇴한_회원은_404() {
        Member member = data.member();
        member.withdraw(LocalDateTime.now());
        memberRepository.flush();

        for (Long id : new Long[]{member.getMemberId(), -1L}) {
            assertThatThrownBy(() -> memberProfileService.getProfile(id))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        }
    }
}
