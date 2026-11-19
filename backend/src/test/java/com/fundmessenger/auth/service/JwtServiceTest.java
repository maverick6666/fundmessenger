package com.fundmessenger.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-32-chars-minimum!", 3600000L);
    }

    @Test
    @DisplayName("generateToken: userId와 email이 포함된 유효한 토큰을 생성한다")
    void generateToken_createsValidToken() {
        // given
        Long userId = 1L;
        String email = "test@example.com";

        // when
        String token = jwtService.generateToken(userId, email);

        // then
        assertThat(token).isNotNull().isNotBlank();
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("getUserId: 토큰에서 올바른 userId를 추출한다")
    void getUserId_extractsCorrectUserId() {
        // given
        Long userId = 42L;
        String token = jwtService.generateToken(userId, "user@test.com");

        // when
        Long extracted = jwtService.getUserId(token);

        // then
        assertThat(extracted).isEqualTo(42L);
    }

    @Test
    @DisplayName("getEmail: 토큰에서 올바른 email을 추출한다")
    void getEmail_extractsCorrectEmail() {
        // given
        String email = "hong@yonsei.ac.kr";
        String token = jwtService.generateToken(1L, email);

        // when
        String extracted = jwtService.getEmail(token);

        // then
        assertThat(extracted).isEqualTo("hong@yonsei.ac.kr");
    }

    @Test
    @DisplayName("isValid: 유효한 토큰이면 true를 반환한다")
    void isValid_returnsTrueForValidToken() {
        // given
        String token = jwtService.generateToken(1L, "test@test.com");

        // when & then
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid: 만료된 토큰이면 false를 반환한다")
    void isValid_returnsFalseForExpiredToken() throws InterruptedException {
        // given - 1ms 만료 시간으로 서비스 생성
        JwtService shortLived = new JwtService("test-secret-key-32-chars-minimum!", 1L);
        String token = shortLived.generateToken(1L, "test@test.com");

        // when - 토큰 만료 대기
        Thread.sleep(50);

        // then
        assertThat(shortLived.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("isValid: 변조된 토큰이면 false를 반환한다")
    void isValid_returnsFalseForTamperedToken() {
        // given
        String token = jwtService.generateToken(1L, "test@test.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        // when & then
        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("isValid: null 토큰이면 false를 반환한다")
    void isValid_returnsFalseForNullToken() {
        assertThat(jwtService.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("isValid: 빈 문자열 토큰이면 false를 반환한다")
    void isValid_returnsFalseForEmptyToken() {
        assertThat(jwtService.isValid("")).isFalse();
    }
}
