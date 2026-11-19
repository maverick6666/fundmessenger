package com.fundmessenger.auth.dto;

import com.fundmessenger.user.entity.SocialProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 소셜 프로바이더에서 가져온 사용자 정보를 통일된 형태로 담는 DTO
 */
@Getter
@AllArgsConstructor
@Builder
public class OAuth2UserInfo {
    private SocialProvider provider;
    private String providerId;
    private String email;
}
