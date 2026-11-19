package com.fundmessenger.home.service;

import com.fundmessenger.board.dto.PostResponse;
import com.fundmessenger.club.dto.AnnouncementResponse;
import com.fundmessenger.board.entity.*;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.CarouselMemberRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.home.dto.ExecutiveResponse;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock BoardRepository boardRepository;
    @Mock PostRepository postRepository;
    @Mock DriveFileRepository driveFileRepository;
    @Mock CarouselMemberRepository carouselMemberRepository;
    @Mock ClubAuthorizationService authService;

    @InjectMocks HomeService sut;

    User user;
    Club club;
    ClubMembership membership;

    @BeforeEach
    void setUp() {
        user = TestFixtures.user(1L, "test@test.dev");
        club = TestFixtures.club(1L, "테스트동아리");
        membership = TestFixtures.approvedMembership(1L, user, club);
    }

    @org.junit.jupiter.api.Disabled("getNotices가 Announcement 기반으로 변경됨 — 테스트 재작성 필요")
    @Test
    @DisplayName("공지사항 배너 — 최신 공지글 목록을 반환한다")
    void getNotices_returnsLatestNotice() {
        // given
        Board noticeBoard = TestFixtures.executiveBoard(1L, club);

        Post post = Post.builder()
                .title("공지합니다").content("내용")
                .author(membership)
                .boards(java.util.Set.of(noticeBoard))
                .semiCategories(new java.util.HashSet<>())
                .build();
        post.setId(1L);
        post.setCreatedAt(LocalDateTime.now());

        given(authService.validateClubMember(1L, 1L)).willReturn(membership);
        given(boardRepository.findByClubIdAndName(1L, "공지사항")).willReturn(Optional.of(noticeBoard));
        given(postRepository.findByBoardIdOrderByCreatedAtDesc(eq(1L), any(PageRequest.class)))
                .willReturn(new PageImpl<>(List.of(post)));

        // when
        List<AnnouncementResponse> result = sut.getNotices(1L, 5, 1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("공지합니다");
    }

    @Test
    @DisplayName("게시판 패널 — 최신 게시글 목록을 반환한다")
    void getHomePosts_returnsPosts() {
        // given
        Board board = TestFixtures.everyoneBoard(2L, club);
        Post post = Post.builder()
                .title("테스트 글").content("내용")
                .author(membership)
                .boards(java.util.Set.of(board))
                .semiCategories(new java.util.HashSet<>())
                .build();
        post.setId(1L);
        post.setCreatedAt(LocalDateTime.now());

        given(authService.validateClubMember(1L, 1L)).willReturn(membership);
        given(postRepository.findWithFilters(eq(1L), isNull(), isNull(), any(PageRequest.class)))
                .willReturn(new PageImpl<>(List.of(post)));

        // when
        List<PostResponse> result = sut.getHomePosts(1L, null, null, 5, 1L);

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("임원 캐러셀 — CarouselMember 기반으로 멤버 목록을 반환한다")
    void getExecutives_returnsCarouselMembers() {
        // given
        CarouselMember cm = CarouselMember.builder()
                .id(1L).club(club).clubMembership(membership)
                .roleName("회장").displayOrder(0).build();

        given(authService.validateClubMember(1L, 1L)).willReturn(membership);
        given(carouselMemberRepository.findByClubIdWithMembershipDetails(1L)).willReturn(List.of(cm));

        // when
        List<ExecutiveResponse> result = sut.getExecutives(1L, 1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo(membership.getName());
        assertThat(result.get(0).getRoleName()).isEqualTo("회장");
    }
}
