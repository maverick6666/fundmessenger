package com.fundmessenger.auth.service;

import com.fundmessenger.auth.dto.OAuth2UserInfo;
import com.fundmessenger.user.entity.SocialProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * 소셜 프로바이더별 OAuth2 인증 처리
 * - 인가 URL 생성 (CSRF state 포함)
 * - 인가코드 → 액세스토큰 교환
 * - 액세스토큰 → 사용자정보 조회
 */
@Slf4j
@Service
public class OAuth2Service {

    private final WebClient webClient;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Google
    @Value("${oauth2.google.client-id}")
    private String googleClientId;
    @Value("${oauth2.google.client-secret}")
    private String googleClientSecret;
    @Value("${oauth2.google.redirect-uri}")
    private String googleRedirectUri;

    // Kakao
    @Value("${oauth2.kakao.client-id}")
    private String kakaoClientId;
    @Value("${oauth2.kakao.client-secret}")
    private String kakaoClientSecret;
    @Value("${oauth2.kakao.redirect-uri}")
    private String kakaoRedirectUri;

    // Naver
    @Value("${oauth2.naver.client-id}")
    private String naverClientId;
    @Value("${oauth2.naver.client-secret}")
    private String naverClientSecret;
    @Value("${oauth2.naver.redirect-uri}")
    private String naverRedirectUri;

    public OAuth2Service(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // ========== CSRF State 생성 ==========

    /** 암호학적으로 안전한 랜덤 state 값 생성 */
    public String generateState() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ========== 인가 URL 생성 (state 포함) ==========

    public String getAuthorizationUrl(String provider, String state) {
        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);

        return switch (provider.toLowerCase()) {
            case "google" -> "https://accounts.google.com/o/oauth2/v2/auth"
                    + "?client_id=" + googleClientId
                    + "&redirect_uri=" + googleRedirectUri
                    + "&response_type=code"
                    + "&scope=openid%20email%20profile"
                    + "&access_type=offline"
                    + "&state=" + encodedState;
            case "kakao" -> "https://kauth.kakao.com/oauth/authorize"
                    + "?client_id=" + kakaoClientId
                    + "&redirect_uri=" + kakaoRedirectUri
                    + "&response_type=code"
                    + "&scope=account_email"
                    + "&state=" + encodedState;
            case "naver" -> "https://nid.naver.com/oauth2.0/authorize"
                    + "?client_id=" + naverClientId
                    + "&redirect_uri=" + naverRedirectUri
                    + "&response_type=code"
                    + "&state=" + encodedState;
            default -> throw new IllegalArgumentException("지원하지 않는 프로바이더: " + provider);
        };
    }

    // ========== 인가코드 → 사용자 정보 ==========

    public OAuth2UserInfo getUserInfo(String provider, String code) {
        return switch (provider.toLowerCase()) {
            case "google" -> getGoogleUserInfo(code);
            case "kakao" -> getKakaoUserInfo(code);
            case "naver" -> getNaverUserInfo(code);
            default -> throw new IllegalArgumentException("지원하지 않는 프로바이더: " + provider);
        };
    }

    // ========== Google ==========

    private OAuth2UserInfo getGoogleUserInfo(String code) {
        // 1. 코드 → 토큰
        var tokenParams = new LinkedMultiValueMap<String, String>();
        tokenParams.add("code", code);
        tokenParams.add("client_id", googleClientId);
        tokenParams.add("client_secret", googleClientSecret);
        tokenParams.add("redirect_uri", googleRedirectUri);
        tokenParams.add("grant_type", "authorization_code");

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = webClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(tokenParams))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String accessToken = (String) tokenResponse.get("access_token");

        // 2. 토큰 → 사용자 정보
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = webClient.get()
                .uri("https://www.googleapis.com/oauth2/v2/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        log.debug("Google user info: {}", userInfo);

        return OAuth2UserInfo.builder()
                .provider(SocialProvider.GOOGLE)
                .providerId((String) userInfo.get("id"))
                .email((String) userInfo.get("email"))
                .build();
    }

    // ========== Kakao ==========

    private OAuth2UserInfo getKakaoUserInfo(String code) {
        // 1. 코드 → 토큰
        var tokenParams = new LinkedMultiValueMap<String, String>();
        tokenParams.add("code", code);
        tokenParams.add("client_id", kakaoClientId);
        tokenParams.add("client_secret", kakaoClientSecret);
        tokenParams.add("redirect_uri", kakaoRedirectUri);
        tokenParams.add("grant_type", "authorization_code");

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = webClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(tokenParams))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String accessToken = (String) tokenResponse.get("access_token");

        // 2. 토큰 → 사용자 정보
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = webClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        log.debug("Kakao user info: {}", userInfo);

        String id = String.valueOf(userInfo.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> kakaoAccount = (Map<String, Object>) userInfo.get("kakao_account");
        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;

        return OAuth2UserInfo.builder()
                .provider(SocialProvider.KAKAO)
                .providerId(id)
                .email(email)
                .build();
    }

    // ========== Naver ==========

    private OAuth2UserInfo getNaverUserInfo(String code) {
        // 1. 코드 → 토큰
        var tokenParams = new LinkedMultiValueMap<String, String>();
        tokenParams.add("code", code);
        tokenParams.add("client_id", naverClientId);
        tokenParams.add("client_secret", naverClientSecret);
        tokenParams.add("grant_type", "authorization_code");
        // Note: Naver requires state in token request as well
        // State validation is handled at the controller level via cookie

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = webClient.post()
                .uri("https://nid.naver.com/oauth2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(tokenParams))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String accessToken = (String) tokenResponse.get("access_token");

        // 2. 토큰 → 사용자 정보
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = webClient.get()
                .uri("https://openapi.naver.com/v1/nid/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        log.debug("Naver user info: {}", userInfo);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) userInfo.get("response");
        String id = (String) response.get("id");
        String email = (String) response.get("email");

        return OAuth2UserInfo.builder()
                .provider(SocialProvider.NAVER)
                .providerId(id)
                .email(email)
                .build();
    }
}
