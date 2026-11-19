package com.fundmessenger.club.service;

import com.fundmessenger.club.dto.ApplyRequest;
import com.fundmessenger.club.dto.SchoolResponse;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.SchoolRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock SchoolRepository schoolRepository;
    @Mock ClubRepository clubRepository;
    @Mock ClubMembershipRepository membershipRepository;
    @Mock UserRepository userRepository;

    @InjectMocks OnboardingService sut;

    User user;
    School school;
    Club club;

    @BeforeEach
    void setUp() {
        user = TestFixtures.user(1L, "newuser@test.dev");
        school = TestFixtures.school(1L, "연세대학교");
        club = TestFixtures.club(1L, "VALUE", school);
    }

    @Test
    @DisplayName("동아리 가입 신청 — PENDING 상태의 멤버십이 생성된다")
    void applyToClub_createsPendingMembership() {
        // given
        ApplyRequest request = new ApplyRequest();
        request.setClubId(1L);
        request.setName("홍길동");
        request.setStudentId("20260001");
        request.setPhone("010-1234-5678");

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(clubRepository.findById(1L)).willReturn(Optional.of(club));
        given(membershipRepository.findByUserIdAndClubId(1L, 1L)).willReturn(Optional.empty());
        given(membershipRepository.findByStudentIdInSameSchoolByOtherUser("20260001", 1L, 1L))
                .willReturn(Optional.empty());

        // when
        sut.applyToClub(1L, request);

        // then
        then(membershipRepository).should().save(argThat(m ->
                m.getStatus() == MemberStatus.PENDING &&
                m.getName().equals("홍길동") &&
                m.getStudentId().equals("20260001")
        ));
    }

    @Test
    @DisplayName("동아리 가입 신청 — 동일 학번으로 다른 계정이 이미 가입되어 있으면 BadRequestException")
    void applyToClub_duplicateStudentId_fails() {
        // given
        ApplyRequest request = new ApplyRequest();
        request.setClubId(1L);
        request.setName("홍길동");
        request.setStudentId("20260001");
        request.setPhone("010-1234-5678");

        User otherUser = TestFixtures.user(2L, "other@test.dev");
        ClubMembership existing = TestFixtures.approvedMembership(10L, otherUser, club);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(clubRepository.findById(1L)).willReturn(Optional.of(club));
        given(membershipRepository.findByUserIdAndClubId(1L, 1L)).willReturn(Optional.empty());
        given(membershipRepository.findByStudentIdInSameSchoolByOtherUser("20260001", 1L, 1L))
                .willReturn(Optional.of(existing));

        // when & then
        assertThatThrownBy(() -> sut.applyToClub(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이미 해당 학번으로 가입된 계정");
    }

    @Test
    @DisplayName("학교 목록 조회 — 등록된 학교 목록을 반환한다")
    void getSchools_returnsList() {
        // given
        School s1 = TestFixtures.school(1L, "연세대학교");
        School s2 = TestFixtures.school(2L, "고려대학교");
        given(schoolRepository.findAll()).willReturn(List.of(s1, s2));

        // when
        List<SchoolResponse> result = sut.getSchools();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("연세대학교");
    }
}
