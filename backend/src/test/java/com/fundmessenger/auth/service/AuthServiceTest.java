package com.fundmessenger.auth.service;

import com.fundmessenger.auth.dto.AuthResponse;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClubMembershipRepository membershipRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    // ========== devLogin ==========

    @Nested
    @DisplayName("devLogin")
    class DevLogin {

        @Test
        @DisplayName("DEV_1 로그인 시 신규 사용자를 생성하고 토큰을 반환한다")
        void devLogin_createsNewUser_returnsToken() {
            // given
            String accountKey = "DEV_1";
            String expectedEmail = "dev-dev_1@fundmessenger.dev";
            User newUser = TestFixtures.user(1L, expectedEmail);

            given(userRepository.findByEmail(expectedEmail)).willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(newUser);
            given(jwtService.generateToken(1L, expectedEmail)).willReturn("test-jwt-token");
            given(membershipRepository.findByUserId(1L)).willReturn(Collections.emptyList());

            // when
            AuthResponse response = authService.devLogin(accountKey);

            // then
            assertThat(response.getToken()).isEqualTo("test-jwt-token");
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getEmail()).isEqualTo(expectedEmail);
            assertThat(response.isSuperAdmin()).isFalse();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User captured = userCaptor.getValue();
            assertThat(captured.getEmail()).isEqualTo(expectedEmail);
            assertThat(captured.isSuperAdmin()).isFalse();
        }

        @Test
        @DisplayName("이미 존재하는 DEV_1 사용자로 재로그인하면 기존 사용자를 반환한다")
        void devLogin_existingUser_returnsExisting() {
            // given
            String accountKey = "DEV_1";
            String email = "dev-dev_1@fundmessenger.dev";
            User existingUser = TestFixtures.user(1L, email);

            given(userRepository.findByEmail(email)).willReturn(Optional.of(existingUser));
            given(jwtService.generateToken(1L, email)).willReturn("existing-token");
            given(membershipRepository.findByUserId(1L)).willReturn(Collections.emptyList());

            // when
            AuthResponse response = authService.devLogin(accountKey);

            // then
            assertThat(response.getToken()).isEqualTo("existing-token");
            assertThat(response.getUserId()).isEqualTo(1L);
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("SUPER_ADMIN 키로 로그인하면 superAdmin=true 사용자를 생성한다")
        void devLogin_superAdmin_returnsSuperAdminTrue() {
            // given
            String accountKey = "SUPER_ADMIN";
            String email = "dev-super_admin@fundmessenger.dev";
            User superAdminUser = TestFixtures.superAdmin(1L);
            superAdminUser.setEmail(email);

            given(userRepository.findByEmail(email)).willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(superAdminUser);
            given(jwtService.generateToken(anyLong(), anyString())).willReturn("admin-token");

            // when
            AuthResponse response = authService.devLogin(accountKey);

            // then
            assertThat(response.isSuperAdmin()).isTrue();
            assertThat(response.getUserStatus()).isEqualTo("SUPER_ADMIN");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().isSuperAdmin()).isTrue();
        }
    }

    // ========== getCurrentUser ==========

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUser {

        @Test
        @DisplayName("유효한 userId로 호출하면 올바른 상태를 포함한 AuthResponse를 반환한다")
        void getCurrentUser_validUserId_returnsAuthResponse() {
            // given
            User user = TestFixtures.user(1L, "test@dev.com");
            var club = TestFixtures.club(1L, "테스트동아리");
            ClubMembership membership = TestFixtures.approvedMembership(1L, user, club);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtService.generateToken(1L, "test@dev.com")).willReturn("token");
            given(membershipRepository.findByUserId(1L)).willReturn(List.of(membership));

            // when
            AuthResponse response = authService.getCurrentUser(1L);

            // then
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getEmail()).isEqualTo("test@dev.com");
            assertThat(response.getUserStatus()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("존재하지 않는 userId로 호출하면 RuntimeException을 던진다")
        void getCurrentUser_invalidUserId_throws() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.getCurrentUser(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    // ========== getUserStatus (private, tested through buildResponse) ==========

    @Nested
    @DisplayName("getUserStatus — buildResponse를 통한 간접 테스트")
    class GetUserStatus {

        @Test
        @DisplayName("멤버십이 없으면 NO_CLUB 상태를 반환한다")
        void noMemberships_returnsNoClub() {
            // given
            User user = TestFixtures.user(1L, "noclub@dev.com");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtService.generateToken(1L, "noclub@dev.com")).willReturn("token");
            given(membershipRepository.findByUserId(1L)).willReturn(Collections.emptyList());

            // when
            AuthResponse response = authService.getCurrentUser(1L);

            // then
            assertThat(response.getUserStatus()).isEqualTo("NO_CLUB");
        }

        @Test
        @DisplayName("PENDING 멤버십만 있으면 PENDING 상태를 반환한다")
        void pendingMembership_returnsPending() {
            // given
            User user = TestFixtures.user(2L, "pending@dev.com");
            var club = TestFixtures.club(1L, "테스트동아리");
            ClubMembership pending = TestFixtures.pendingMembership(1L, user, club);

            given(userRepository.findById(2L)).willReturn(Optional.of(user));
            given(jwtService.generateToken(2L, "pending@dev.com")).willReturn("token");
            given(membershipRepository.findByUserId(2L)).willReturn(List.of(pending));

            // when
            AuthResponse response = authService.getCurrentUser(2L);

            // then
            assertThat(response.getUserStatus()).isEqualTo("PENDING");
            assertThat(response.getPendingClubName()).isEqualTo("테스트동아리");
            assertThat(response.getPendingSchoolName()).isEqualTo("테스트대학교");
        }

        @Test
        @DisplayName("APPROVED 멤버십이 있으면 APPROVED 상태를 반환한다")
        void approvedMembership_returnsApproved() {
            // given
            User user = TestFixtures.user(3L, "approved@dev.com");
            var club = TestFixtures.club(1L, "테스트동아리");
            ClubMembership approved = TestFixtures.approvedMembership(1L, user, club);

            given(userRepository.findById(3L)).willReturn(Optional.of(user));
            given(jwtService.generateToken(3L, "approved@dev.com")).willReturn("token");
            given(membershipRepository.findByUserId(3L)).willReturn(List.of(approved));

            // when
            AuthResponse response = authService.getCurrentUser(3L);

            // then
            assertThat(response.getUserStatus()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("superAdmin 사용자는 멤버십과 관계없이 SUPER_ADMIN 상태를 반환한다")
        void superAdminUser_returnsSuperAdminStatus() {
            // given
            User admin = TestFixtures.superAdmin(10L);

            given(userRepository.findById(10L)).willReturn(Optional.of(admin));
            given(jwtService.generateToken(anyLong(), anyString())).willReturn("token");

            // when
            AuthResponse response = authService.getCurrentUser(10L);

            // then
            assertThat(response.getUserStatus()).isEqualTo("SUPER_ADMIN");
            assertThat(response.isSuperAdmin()).isTrue();
        }
    }
}
