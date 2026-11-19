package com.fundmessenger.auth.controller;

import com.fundmessenger.auth.dto.AuthResponse;
import com.fundmessenger.auth.dto.DevLoginRequest;
import com.fundmessenger.auth.service.AuthService;
import com.fundmessenger.auth.service.OAuth2Service;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.file.service.FileStorageService;
import com.fundmessenger.user.entity.User;
import com.fundmessenger.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuth2Service oAuth2Service;
    private final UserRepository userRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final FileStorageService fileStorageService;

    private static final String OAUTH_STATE_COOKIE = "oauth2_state";
    private static final int STATE_COOKIE_MAX_AGE = 300; // 5분

    // Cloudflare Worker 프록시 환경에서 쿠키 전달 문제 우회: 인메모리 state 저장
    private final java.util.concurrent.ConcurrentHashMap<String, Long> stateStore = new java.util.concurrent.ConcurrentHashMap<>();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.environment}")
    private String environment;

    /**
     * 소셜 로그인 시작 → 프로바이더 로그인 페이지로 리다이렉트
     * CSRF 방어: 랜덤 state 생성 → HttpOnly 쿠키에 저장 + URL 파라미터로 전달
     * GET /api/auth/oauth2/{provider}
     */
    @GetMapping("/oauth2/{provider}")
    public void redirectToProvider(
            @PathVariable String provider,
            HttpServletResponse response
    ) throws IOException {
        // 1. 랜덤 state 생성
        String state = oAuth2Service.generateState();

        // 2. 인메모리에 state 저장 (5분 만료)
        stateStore.put(state, System.currentTimeMillis() + STATE_COOKIE_MAX_AGE * 1000L);
        // 만료된 state 정리
        long now = System.currentTimeMillis();
        stateStore.entrySet().removeIf(e -> e.getValue() < now);

        // 3. state 포함한 인가 URL로 리다이렉트
        String authUrl = oAuth2Service.getAuthorizationUrl(provider, state);
        log.debug("OAuth2 redirect: provider={}", provider);
        response.sendRedirect(authUrl);
    }

    /**
     * 소셜 로그인 콜백 → state 검증 → JWT 발급 → 프론트엔드로 리다이렉트
     * GET /api/auth/oauth2/callback/{provider}?code=xxx&state=yyy
     */
    @GetMapping("/oauth2/callback/{provider}")
    public void handleCallback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        log.debug("OAuth2 callback: provider={}", provider);

        try {
            // 1. State 검증 (인메모리)
            if (state == null || state.isBlank()) {
                throw new SecurityException("OAuth2 state가 없습니다");
            }
            Long expiry = stateStore.remove(state);
            if (expiry == null || expiry < System.currentTimeMillis()) {
                throw new SecurityException("OAuth2 state가 만료되었거나 존재하지 않습니다");
            }

            // 3. 인가코드로 사용자 정보 조회
            var userInfo = oAuth2Service.getUserInfo(provider, code);
            AuthResponse authResponse = authService.socialLogin(userInfo);

            // 4. 프론트엔드로 리다이렉트 (토큰 + 상태 전달)
            String redirectUrl = frontendUrl + "/auth/callback"
                    + "?token=" + URLEncoder.encode(authResponse.getToken(), StandardCharsets.UTF_8)
                    + "&status=" + authResponse.getUserStatus();

            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            log.error("OAuth2 callback 실패: provider={}", provider, e);
            String errorUrl = frontendUrl + "/login?error="
                    + URLEncoder.encode("소셜 로그인에 실패했습니다", StandardCharsets.UTF_8);
            response.sendRedirect(errorUrl);
        }
    }

    /**
     * DEV 모드 로그인 (development 환경에서만 작동)
     * POST /api/auth/dev-login
     */
    @PostMapping("/dev-login")
    public ResponseEntity<?> devLogin(@Valid @RequestBody DevLoginRequest request) {
        if (!"development".equals(environment)) {
            return ResponseEntity.status(403).body("개발 환경에서만 사용 가능합니다");
        }
        AuthResponse authResponse = authService.devLogin(request.getRole());
        return ResponseEntity.ok(authResponse);
    }

    /**
     * 현재 로그인 사용자 정보 조회
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(Authentication auth) {
        // SEC-11: Spring Security가 인증을 강제하므로 auth는 항상 non-null
        Long userId = (Long) auth.getPrincipal();
        AuthResponse authResponse = authService.getCurrentUser(userId);
        return ResponseEntity.ok(authResponse);
    }

    /**
     * 프로필 수정 (bio, phone, avatar)
     * PUT /api/auth/me/profile
     */
    @PutMapping("/me/profile")
    public ResponseEntity<?> updateProfile(
            Authentication auth,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) MultipartFile avatar
    ) {
        Long userId = (Long) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        if (bio != null) user.setBio(bio.length() > 100 ? bio.substring(0, 100) : bio);
        if (phone != null) user.setPhone(phone.length() > 30 ? phone.substring(0, 30) : phone);

        if (avatar != null && !avatar.isEmpty()) {
            String storedName = fileStorageService.storeImage(avatar);
            user.setAvatarUrl("/api/files/images/" + storedName);
        }

        userRepository.save(user);
        return ResponseEntity.ok(java.util.Map.of(
                "bio", user.getBio() != null ? user.getBio() : "",
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
        ));
    }

    /**
     * 프로필 조회
     * GET /api/auth/me/profile
     */
    @GetMapping("/me/profile")
    public ResponseEntity<?> getProfile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        return ResponseEntity.ok(java.util.Map.of(
                "bio", user.getBio() != null ? user.getBio() : "",
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
        ));
    }

    /**
     * DEV 모드 계정 리셋 — E2E 테스트 재실행용 (development 환경 전용)
     * DEV_7 등 온보딩 테스트 후 PENDING 상태가 된 계정을 NO_CLUB 상태로 복원
     * DELETE /api/auth/dev-reset/{accountKey}
     */
    @DeleteMapping("/dev-reset/{accountKey}")
    public ResponseEntity<?> devReset(@PathVariable String accountKey) {
        if (!"development".equals(environment)) {
            return ResponseEntity.status(403).body("개발 환경에서만 사용 가능합니다");
        }
        String email = "dev-" + accountKey.toLowerCase() + "@fundmessenger.dev";
        userRepository.findByEmail(email).ifPresent(user -> {
            var memberships = clubMembershipRepository.findByUserId(user.getId());
            clubMembershipRepository.deleteAll(memberships);
            log.info("DEV reset: {} ({}) — {} memberships deleted", accountKey, email, memberships.size());
        });
        return ResponseEntity.ok().build();
    }

    // ========== Private ==========

    /** OAuth2 state 검증: 쿠키의 값과 콜백 파라미터 비교 */
    private void validateOAuthState(String callbackState, HttpServletRequest request) {
        if (callbackState == null || callbackState.isBlank()) {
            throw new SecurityException("OAuth2 state 파라미터가 누락되었습니다");
        }

        String cookieState = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (OAUTH_STATE_COOKIE.equals(cookie.getName())) {
                    cookieState = cookie.getValue();
                    break;
                }
            }
        }

        if (cookieState == null) {
            throw new SecurityException("OAuth2 state 쿠키가 만료되었거나 존재하지 않습니다");
        }

        if (!cookieState.equals(callbackState)) {
            log.warn("OAuth2 state mismatch: cookie={}, callback={}", cookieState, callbackState);
            throw new SecurityException("OAuth2 state 검증 실패 — CSRF 공격 가능성");
        }
    }

    /** State 쿠키 삭제 */
    private void clearStateCookie(HttpServletResponse response) {
        boolean isProduction = !"development".equals(environment);
        String cookieValue = OAUTH_STATE_COOKIE + "="
                + "; Path=/api/auth/oauth2/callback"
                + "; Max-Age=0"
                + "; HttpOnly"
                + (isProduction ? "; Secure; SameSite=None" : "");
        response.addHeader("Set-Cookie", cookieValue);
    }
}
