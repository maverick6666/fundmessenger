package com.fundmessenger.auth.service;

import com.fundmessenger.auth.dto.AuthResponse;
import com.fundmessenger.auth.dto.OAuth2UserInfo;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberStatus;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.user.entity.SocialProvider;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ClubMembershipRepository membershipRepository;
    private final JwtService jwtService;

    /**
     * 소셜 로그인: 사용자 찾기/생성 → JWT 발급 → 상태 포함 응답
     */
    @Transactional
    public AuthResponse socialLogin(OAuth2UserInfo userInfo) {
        User user = userRepository
                .findBySocialProviderAndSocialProviderId(
                        userInfo.getProvider(), userInfo.getProviderId())
                .orElseGet(() -> createUser(userInfo));

        user.setLastActiveAt(LocalDateTime.now());
        return buildResponse(user);
    }

    /**
     * DEV 모드 로그인: 테스트 계정 생성/로그인
     * - "SUPER_ADMIN" → superAdmin=true 인 관리자 계정
     * - "DEV_1"~"DEV_7" → 미소속 일반 계정 (역할 테스트용)
     */
    @Transactional
    public AuthResponse devLogin(String accountKey) {
        boolean isSuperAdmin = "SUPER_ADMIN".equals(accountKey);
        String email = "dev-" + accountKey.toLowerCase() + "@fundmessenger.dev";

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .socialProvider(SocialProvider.DEV)
                            .socialProviderId("dev-" + accountKey.toLowerCase())
                            .superAdmin(isSuperAdmin)
                            .build();
                    return userRepository.save(newUser);
                });

        user.setLastActiveAt(LocalDateTime.now());
        return buildResponse(user);
    }

    /**
     * 현재 사용자 상태 조회 (토큰 기반)
     */
    public AuthResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        return buildResponse(user);
    }

    // ========== Private ==========

    private AuthResponse buildResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        String status = getUserStatus(user);

        AuthResponse.AuthResponseBuilder builder = AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .userStatus(status)
                .superAdmin(user.isSuperAdmin());

        // PENDING 상태일 때 학교/동아리/신청일 정보 포함
        if ("PENDING".equals(status)) {
            membershipRepository.findByUserId(user.getId()).stream()
                    .filter(m -> m.getStatus() == MemberStatus.PENDING)
                    .findFirst()
                    .ifPresent(m -> {
                        builder.pendingSchoolName(m.getClub().getSchool().getName());
                        builder.pendingClubName(m.getClub().getName());
                        builder.pendingAppliedAt(m.getAppliedAt().toLocalDate().toString());
                    });
        }

        return builder.build();
    }

    private User createUser(OAuth2UserInfo info) {
        // 최초 가입자 = 슈퍼어드민 (User 테이블이 비어있으면)
        boolean isFirstUser = userRepository.count() == 0;

        User user = User.builder()
                .email(info.getEmail())
                .socialProvider(info.getProvider())
                .socialProviderId(info.getProviderId())
                .superAdmin(isFirstUser)
                .build();
        User saved = userRepository.save(user);

        if (isFirstUser) {
            log.info("최초 가입자 → 슈퍼어드민 설정: id={}, email={}", saved.getId(), saved.getEmail());
        }
        log.info("새 사용자 생성: id={}, email={}, provider={}",
                saved.getId(), saved.getEmail(), saved.getSocialProvider());
        return saved;
    }

    /**
     * 사용자 상태 결정 로직:
     * - SUPER_ADMIN → 동아리 없어도 바로 서비스 진입
     * - NO_CLUB: 어떤 동아리에도 가입 신청 안 한 상태 → 온보딩 필요
     * - PENDING: 가입 신청 했지만 승인 대기 중
     * - APPROVED: 승인 완료, 서비스 이용 가능
     */
    private String getUserStatus(User user) {
        if (user.isSuperAdmin()) {
            return "SUPER_ADMIN";
        }

        List<ClubMembership> memberships = membershipRepository.findByUserId(user.getId());

        if (memberships.isEmpty()) {
            return "NO_CLUB";
        }

        boolean hasApproved = memberships.stream()
                .anyMatch(m -> m.getStatus() == MemberStatus.APPROVED);
        if (hasApproved) {
            return "APPROVED";
        }

        boolean hasPending = memberships.stream()
                .anyMatch(m -> m.getStatus() == MemberStatus.PENDING);
        if (hasPending) {
            return "PENDING";
        }

        return "NO_CLUB";  // 모두 거절된 경우
    }

    /** Long userId → User 로드 후 상태 확인 (오버로드) */
    private String getUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        return getUserStatus(user);
    }
}
