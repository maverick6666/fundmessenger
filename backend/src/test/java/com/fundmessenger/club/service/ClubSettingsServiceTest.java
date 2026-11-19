package com.fundmessenger.club.service;

import com.fundmessenger.club.dto.ClubSettingsResponse;
import com.fundmessenger.club.dto.UpdateClubSettingsRequest;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.ClubSettings;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.ClubSettingsRepository;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ClubSettingsServiceTest {

    @Mock ClubSettingsRepository settingsRepository;
    @Mock ClubRepository clubRepository;
    @Mock ClubAuthorizationService authService;

    @InjectMocks ClubSettingsService sut;

    User user;
    Club club;
    ClubMembership membership;

    @BeforeEach
    void setUp() {
        user = TestFixtures.user(1L, "admin@test.dev");
        club = TestFixtures.club(1L, "테스트동아리");
        membership = TestFixtures.approvedMembership(1L, user, club);
    }

    @Test
    @DisplayName("설정 조회 — 기존 설정이 있으면 그대로 반환, 없으면 기본값 생성 후 반환")
    void getOrCreateSettings_returnsExistingOrCreatesNew() {
        // given — 기존 설정이 없는 경우
        given(authService.validateClubMember(1L, 1L)).willReturn(membership);
        given(settingsRepository.findById(1L)).willReturn(Optional.empty());
        given(clubRepository.findById(1L)).willReturn(Optional.of(club));

        ClubSettings newSettings = ClubSettings.builder().club(club).build();
        given(settingsRepository.save(any(ClubSettings.class))).willReturn(newSettings);

        // when
        ClubSettingsResponse result = sut.getSettings(1L, 1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAiAccessMode()).isEqualTo("EXECUTIVE_ONLY");
        then(settingsRepository).should().save(any(ClubSettings.class));
    }

    @Test
    @DisplayName("설정 수정 — 동아리 설명, AI 모드가 변경된다")
    void updateSettings_savesChanges() {
        // given
        ClubSettings existing = ClubSettings.builder().club(club).build();

        UpdateClubSettingsRequest request = new UpdateClubSettingsRequest();
        request.setClubDescription("투자 동아리입니다");
        request.setAiAccessMode("ALL");

        given(authService.validateClubAdmin(1L, 1L)).willReturn(membership);
        given(settingsRepository.findById(1L)).willReturn(Optional.of(existing));

        // when
        ClubSettingsResponse result = sut.updateSettings(1L, request, 1L);

        // then
        assertThat(existing.getClubDescription()).isEqualTo("투자 동아리입니다");
        assertThat(existing.getAiAccessMode()).isEqualTo("ALL");
        assertThat(result.getAiAccessMode()).isEqualTo("ALL");
    }
}
