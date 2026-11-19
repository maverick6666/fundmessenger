package com.fundmessenger.board.service;

import com.fundmessenger.board.dto.SemiCategoryResponse;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TagDefinitionRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock private SemiCategoryRepository categoryRepository;
    @Mock private ClubMembershipRepository membershipRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MemberTagRepository memberTagRepository;
    @Mock private TagDefinitionRepository tagDefinitionRepository;
    @Mock private BoardRepository boardRepository;
    @Mock private ClubAuthorizationService authService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private GenerationService generationService;

    private Club club;
    private ClubMembership executive;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        User user = TestFixtures.user(10L, "executive@test.dev");
        executive = TestFixtures.approvedMembership(100L, user, club);
    }

    @Test
    @DisplayName("기수 목록 조회 — generation=true인 태그만 반환된다")
    void getGenerations_returnsGenerationCategories() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(executive);

        SemiCategory gen1 = TestFixtures.semiCategory(1L, club, "1기", true);
        SemiCategory gen2 = TestFixtures.semiCategory(2L, club, "2기", true);

        given(categoryRepository.findByClubIdAndGenerationTrueOrderByDisplayOrderAsc(1L))
                .willReturn(List.of(gen1, gen2));

        // when
        List<SemiCategoryResponse> result = generationService.getGenerations(1L, 10L);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("1기");
        assertThat(result.get(1).getName()).isEqualTo("2기");
    }

    @Test
    @DisplayName("기수 태그 생성 — 중복 없으면 새 기수가 생성된다")
    void createGeneration_success() {
        // given
        given(authService.validateClubAdmin(10L, 1L)).willReturn(executive);
        given(categoryRepository.findByClubIdAndName(1L, "3기")).willReturn(Optional.empty());
        given(categoryRepository.findByClubIdAndGenerationTrueOrderByDisplayOrderAsc(1L))
                .willReturn(List.of(
                        TestFixtures.semiCategory(1L, club, "1기", true),
                        TestFixtures.semiCategory(2L, club, "2기", true)
                ));

        SemiCategory saved = TestFixtures.semiCategory(3L, club, "3기", true);
        saved.setDisplayOrder(2);
        given(categoryRepository.save(any(SemiCategory.class))).willReturn(saved);

        // when
        SemiCategoryResponse response = generationService.createGeneration(1L, "3기", 10L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("3기");
        then(categoryRepository).should().save(any(SemiCategory.class));
    }

    @Test
    @DisplayName("현재 기수 조회 — displayOrder가 가장 높은 기수가 반환된다")
    void getCurrentGeneration_returnsLatest() {
        // given
        SemiCategory latest = TestFixtures.semiCategory(3L, club, "3기", true);
        latest.setDisplayOrder(2);

        given(categoryRepository.findTopByClubIdAndGenerationTrueOrderByDisplayOrderDesc(1L))
                .willReturn(Optional.of(latest));

        // when
        SemiCategory result = generationService.getCurrentGeneration(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("3기");
    }
}
