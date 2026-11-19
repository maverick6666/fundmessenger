package com.fundmessenger.board.service;

import com.fundmessenger.board.dto.CreateSemiCategoryRequest;
import com.fundmessenger.board.dto.SemiCategoryResponse;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.*;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SemiCategoryServiceTest {

    @Mock private SemiCategoryRepository categoryRepository;
    @Mock private PostRepository postRepository;
    @Mock private ClubAuthorizationService authService;

    @InjectMocks private SemiCategoryService semiCategoryService;

    private Club club;
    private ClubMembership executive;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        User user = TestFixtures.user(10L, "executive@test.dev");
        executive = TestFixtures.approvedMembership(100L, user, club);
    }

    @Test
    @DisplayName("카테고리 생성 성공 — 이름 중복 없으면 저장된다")
    void createCategory_success() {
        // given
        given(authService.validateClubAdmin(10L, 1L)).willReturn(executive);
        given(categoryRepository.findByClubIdAndName(1L, "분석")).willReturn(Optional.empty());

        SemiCategory saved = TestFixtures.semiCategory(1L, club, "분석", false);
        given(categoryRepository.save(any(SemiCategory.class))).willReturn(saved);

        CreateSemiCategoryRequest request = new CreateSemiCategoryRequest();
        request.setName("분석");
        request.setDisplayOrder(0);

        // when
        SemiCategoryResponse response = semiCategoryService.createCategory(1L, request, 10L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("분석");
        then(categoryRepository).should().save(any(SemiCategory.class));
    }

    @Test
    @DisplayName("카테고리 목록 조회 — enabled=true이고 generation=false인 태그만 반환된다")
    void getCategories_returnsList() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(executive);

        SemiCategory enabledTag = TestFixtures.semiCategory(1L, club, "분석", false);
        SemiCategory disabledTag = TestFixtures.semiCategory(2L, club, "비활성", false);
        disabledTag.setEnabled(false);
        SemiCategory generationTag = TestFixtures.semiCategory(3L, club, "1기", true);

        given(categoryRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(1L))
                .willReturn(List.of(enabledTag, disabledTag, generationTag));

        // when
        List<SemiCategoryResponse> result = semiCategoryService.getCategories(1L, 10L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("분석");
    }

    @Test
    @DisplayName("카테고리 활성화 토글 — enabled 플래그가 반전된다")
    void toggleCategoryEnabled_togglesFlag() {
        // given
        given(authService.validateClubAdmin(10L, 1L)).willReturn(executive);

        SemiCategory category = TestFixtures.semiCategory(1L, club, "공지", false);
        category.setEnabled(true);
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category));

        // when
        SemiCategoryResponse response = semiCategoryService.toggleCategoryEnabled(1L, 1L, 10L);

        // then
        assertThat(category.isEnabled()).isFalse();  // true -> false 토글
        assertThat(response).isNotNull();
    }
}
