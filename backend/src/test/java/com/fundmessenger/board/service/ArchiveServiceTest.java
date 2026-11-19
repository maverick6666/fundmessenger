package com.fundmessenger.board.service;

import com.fundmessenger.board.dto.PostResponse;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.entity.SemiCategory;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.board.repository.SemiCategoryRepository;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.ClubMembershipRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

    @Mock private SemiCategoryRepository categoryRepository;
    @Mock private ClubMembershipRepository membershipRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PostRepository postRepository;
    @Mock private DriveFileRepository driveFileRepository;
    @Mock private ClubAuthorizationService authService;

    @InjectMocks private ArchiveService archiveService;

    private Club club;
    private ClubMembership member;
    private SemiCategory generation;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        User user = TestFixtures.user(10L, "member@test.dev");
        member = TestFixtures.approvedMembership(100L, user, club);

        generation = TestFixtures.semiCategory(1L, club, "1기", true);
    }

    @Test
    @DisplayName("아카이브 게시글 조회 — 특정 기수의 게시글이 반환된다")
    void getArchivedPosts_returnsPosts() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(member);
        given(categoryRepository.findById(1L)).willReturn(Optional.of(generation));

        Post post = Post.builder()
                .author(member)
                .title("1기 분석 보고서")
                .content("내용")
                .boards(new HashSet<>())
                .semiCategories(new HashSet<>(Set.of(generation)))
                .build();
        post.setId(1L);
        post.setCreatedAt(LocalDateTime.now());

        Page<Post> page = new PageImpl<>(List.of(post));
        given(postRepository.findByAuthorGenerationId(eq(1L), eq(1L), any(PageRequest.class)))
                .willReturn(page);

        // when
        Page<PostResponse> result = archiveService.getArchivePosts(1L, 1L, 0, 20, 10L);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("1기 분석 보고서");
    }

    @Test
    @DisplayName("아카이브 기수 요약 — 팀/멤버/게시글/파일 수가 포함된 요약이 반환된다")
    void getArchiveSummary_returnsGenerationSummary() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(member);
        given(categoryRepository.findById(1L)).willReturn(Optional.of(generation));

        // 기수 소속 팀
        Team team = TestFixtures.team(1L, club, "GrowthFund");
        team.setGenerations(new HashSet<>(Set.of(generation)));
        given(teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(1L))
                .willReturn(List.of(team));

        // 기수 소속 멤버
        member.setGenerations(new HashSet<>(Set.of(generation)));
        given(membershipRepository.findByClubIdAndStatus(1L, MemberStatus.APPROVED))
                .willReturn(List.of(member));

        // 게시글/파일 수
        Page<Post> postPage = new PageImpl<>(List.of(), PageRequest.of(0, 1), 5);
        given(postRepository.findByAuthorGenerationId(eq(1L), eq(1L), any(PageRequest.class)))
                .willReturn(postPage);
        given(driveFileRepository.countByUploaderGenerationId(1L, 1L)).willReturn(3L);

        // when
        Map<String, Object> result = archiveService.getArchiveSummary(1L, 1L, 10L);

        // then
        assertThat(result.get("teamCount")).isEqualTo(1);
        assertThat(result.get("memberCount")).isEqualTo(1);
        assertThat(result.get("postCount")).isEqualTo(5L);
        assertThat(result.get("fileCount")).isEqualTo(3L);
    }
}
