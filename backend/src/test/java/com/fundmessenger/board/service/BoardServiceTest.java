package com.fundmessenger.board.service;

import com.fundmessenger.board.dto.CreatePostRequest;
import com.fundmessenger.board.dto.PostDetailResponse;
import com.fundmessenger.board.dto.BoardResponse;
import com.fundmessenger.board.entity.*;
import com.fundmessenger.board.repository.*;
import com.fundmessenger.club.entity.*;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.drive.repository.DriveFileRepository;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock private BoardRepository boardRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private MemberTagRepository memberTagRepository;
    @Mock private SemiCategoryRepository semiCategoryRepository;
    @Mock private DriveFileRepository driveFileRepository;
    @Mock private ClubAuthorizationService authService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private BoardService boardService;

    // 공통 픽스처
    private User user1, user2;
    private Club club;
    private ClubMembership member, executive;
    private Board everyoneBoard, executiveBoard;
    private Team team;
    private Board teamBoard;
    private SemiCategory tag;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");

        user1 = TestFixtures.user(10L, "member@test.dev");
        user2 = TestFixtures.user(20L, "executive@test.dev");

        member = TestFixtures.approvedMembership(100L, user1, club);
        executive = TestFixtures.approvedMembership(200L, user2, club);

        everyoneBoard = TestFixtures.everyoneBoard(1L, club);
        executiveBoard = TestFixtures.executiveBoard(2L, club);

        team = TestFixtures.team(1L, club, "GrowthFund");
        teamBoard = TestFixtures.teamBoard(3L, club, team);

        tag = TestFixtures.semiCategory(1L, club, "분석", false);
    }

    // ========== getBoards ==========

    @Nested
    @DisplayName("getBoards")
    class GetBoards {

        @Test
        @DisplayName("동아리의 게시판 목록을 반환한다")
        void returnsListOfBoardsForClub() {
            // given
            given(authService.validateClubMember(10L, 1L)).willReturn(member);
            given(boardRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(1L))
                    .willReturn(List.of(everyoneBoard, executiveBoard));
            given(postRepository.countPostsByClubBoards(1L)).willReturn(List.of(
                    new Object[]{1L, 5L},
                    new Object[]{2L, 3L}
            ));
            // checkCanWrite calls authService.isExecutive for EXECUTIVE_ONLY board
            given(authService.isExecutive(member)).willReturn(false);

            // when
            List<BoardResponse> result = boardService.getBoards(1L, 10L);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("자유게시판");
        }
    }

    // ========== createPost ==========

    @Nested
    @DisplayName("createPost")
    class CreatePost {

        private CreatePostRequest request;

        @BeforeEach
        void setUpRequest() {
            request = new CreatePostRequest();
            request.setTitle("테스트 글");
            request.setContent("테스트 내용");
            request.setSemiCategoryIds(List.of(1L));
        }

        @Test
        @DisplayName("EVERYONE 게시판에 일반 멤버가 글을 작성할 수 있다")
        void createPostEveryoneBoardSucceeds() {
            // given
            request.setBoardIds(List.of(1L));
            given(authService.validateClubMember(10L, 1L)).willReturn(member);
            given(boardRepository.findById(1L)).willReturn(Optional.of(everyoneBoard));
            given(semiCategoryRepository.findById(1L)).willReturn(Optional.of(tag));

            Post savedPost = buildPost(1L, member, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));
            given(postRepository.save(any(Post.class))).willReturn(savedPost);

            // when
            PostDetailResponse result = boardService.createPost(1L, request, 10L);

            // then
            assertThat(result.getTitle()).isEqualTo("테스트 글");
            then(postRepository).should().save(any(Post.class));
        }

        @Test
        @DisplayName("EXECUTIVE_ONLY 게시판에 임원이 글을 작성할 수 있다")
        void createPostExecutiveBoardSucceedsForExecutive() {
            // given
            request.setBoardIds(List.of(2L));
            given(authService.validateClubMember(20L, 1L)).willReturn(executive);
            given(boardRepository.findById(2L)).willReturn(Optional.of(executiveBoard));
            given(authService.isExecutive(executive)).willReturn(true);
            given(semiCategoryRepository.findById(1L)).willReturn(Optional.of(tag));

            Post savedPost = buildPost(2L, executive, new HashSet<>(Set.of(executiveBoard)), new HashSet<>(Set.of(tag)));
            given(postRepository.save(any(Post.class))).willReturn(savedPost);

            // when
            PostDetailResponse result = boardService.createPost(1L, request, 20L);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("EXECUTIVE_ONLY 게시판에 일반 멤버가 글을 작성하면 SecurityException 발생")
        void createPostExecutiveBoardFailsForRegular() {
            // given
            request.setBoardIds(List.of(2L));
            given(authService.validateClubMember(10L, 1L)).willReturn(member);
            given(boardRepository.findById(2L)).willReturn(Optional.of(executiveBoard));
            given(authService.isExecutive(member)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> boardService.createPost(1L, request, 10L))
                    .isInstanceOf(com.fundmessenger.config.exception.ForbiddenException.class)
                    .hasMessageContaining("임원급만");
        }

        // TEAM_ONLY 테스트 제거됨 (WritePermission.TEAM_ONLY 폐기)
    }

    // ========== updatePost ==========

    @Nested
    @DisplayName("updatePost")
    class UpdatePost {

        @Test
        @DisplayName("작성자가 글을 수정할 수 있다")
        void updatePostByAuthorSucceeds() {
            // given
            Post post = buildPost(10L, member, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));

            CreatePostRequest request = new CreatePostRequest();
            request.setTitle("수정된 제목");
            request.setContent("수정된 내용");
            request.setBoardIds(List.of(1L));
            request.setSemiCategoryIds(List.of(1L));

            given(authService.validateClubMember(10L, 1L)).willReturn(member);
            given(postRepository.findById(10L)).willReturn(Optional.of(post));
            given(boardRepository.findById(1L)).willReturn(Optional.of(everyoneBoard));
            given(semiCategoryRepository.findById(1L)).willReturn(Optional.of(tag));

            // when
            PostDetailResponse result = boardService.updatePost(1L, 10L, request, 10L);

            // then
            assertThat(result.getTitle()).isEqualTo("수정된 제목");
        }

        @Test
        @DisplayName("작성자도 아니고 관리자도 아닌 사용자가 수정하면 SecurityException 발생")
        void updatePostByNonAuthorNonAdminFails() {
            // given
            Post post = buildPost(10L, member, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));

            CreatePostRequest request = new CreatePostRequest();
            request.setTitle("수정");
            request.setContent("내용");
            request.setBoardIds(List.of(1L));
            request.setSemiCategoryIds(List.of(1L));

            given(authService.validateClubMember(20L, 1L)).willReturn(executive);
            given(postRepository.findById(10L)).willReturn(Optional.of(post));
            given(authService.isExecutive(executive)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> boardService.updatePost(1L, 10L, request, 20L))
                    .isInstanceOf(com.fundmessenger.config.exception.ForbiddenException.class)
                    .hasMessageContaining("권한이 없습니다");
        }
    }

    // ========== deletePost ==========

    @Nested
    @DisplayName("deletePost")
    class DeletePost {

        @Test
        @DisplayName("작성자가 글을 삭제할 수 있다")
        void deletePostByAuthorSucceeds() {
            // given
            Post post = buildPost(10L, member, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));

            given(authService.validateClubMember(10L, 1L)).willReturn(member);
            given(postRepository.findById(10L)).willReturn(Optional.of(post));

            // when
            boardService.deletePost(1L, 10L, 10L);

            // then
            then(postRepository).should().delete(post);
            then(postCommentRepository).should().deleteByPostId(10L);
        }

        @Test
        @DisplayName("관리자가 다른 사람의 글을 삭제할 수 있다")
        void deletePostByAdminSucceeds() {
            // given
            Post post = buildPost(10L, member, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));

            given(authService.validateClubMember(20L, 1L)).willReturn(executive);
            given(postRepository.findById(10L)).willReturn(Optional.of(post));
            given(authService.isExecutive(executive)).willReturn(true);

            // when
            boardService.deletePost(1L, 10L, 20L);

            // then
            then(postRepository).should().delete(post);
        }
    }

    // ========== toggleLike ==========

    @Nested
    @DisplayName("toggleLike")
    class ToggleLike {

        @Test
        @DisplayName("좋아요가 없으면 추가한다")
        void addsLikeWhenNotLiked() {
            // given
            Post post = buildPost(10L, executive, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));

            given(authService.validateClubMember(10L, 1L)).willReturn(member);
            given(postLikeRepository.findByPostIdAndMemberId(10L, 100L)).willReturn(Optional.empty());

            Post updatedPost = buildPost(10L, executive, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));
            updatedPost.setLikeCount(1);
            // findById is called twice: once for the toggle, once to re-read count
            given(postRepository.findById(10L)).willReturn(Optional.of(post), Optional.of(updatedPost));

            // when
            Map<String, Object> result = boardService.toggleLike(1L, 10L, 10L);

            // then
            assertThat(result.get("liked")).isEqualTo(true);
            then(postLikeRepository).should().save(any(PostLike.class));
            then(postRepository).should().incrementLikeCount(10L);
        }

        @Test
        @DisplayName("이미 좋아요했으면 취소한다")
        void removesLikeWhenAlreadyLiked() {
            // given
            Post post = buildPost(10L, executive, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));
            post.setLikeCount(1);
            PostLike existingLike = PostLike.builder().post(post).member(member).build();

            given(authService.validateClubMember(10L, 1L)).willReturn(member);
            given(postLikeRepository.findByPostIdAndMemberId(10L, 100L)).willReturn(Optional.of(existingLike));

            Post updatedPost = buildPost(10L, executive, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));
            updatedPost.setLikeCount(0);
            // findById is called twice
            given(postRepository.findById(10L)).willReturn(Optional.of(post), Optional.of(updatedPost));

            // when
            Map<String, Object> result = boardService.toggleLike(1L, 10L, 10L);

            // then
            assertThat(result.get("liked")).isEqualTo(false);
            then(postLikeRepository).should().delete(existingLike);
            then(postRepository).should().decrementLikeCount(10L);
        }
    }

    // ========== getPostById ==========

    @Nested
    @DisplayName("getPostById")
    class GetPostById {

        @Test
        @DisplayName("postId로 글 상세를 반환한다")
        void returnsPostDetail() {
            // given
            Post post = buildPost(10L, member, new HashSet<>(Set.of(everyoneBoard)), new HashSet<>(Set.of(tag)));

            given(authService.validateClubMember(10L, 1L)).willReturn(member);
            given(postRepository.findById(10L)).willReturn(Optional.of(post));
            given(postLikeRepository.existsByPostIdAndMemberId(10L, 100L)).willReturn(false);

            // when
            PostDetailResponse result = boardService.getPostById(1L, 10L, 10L);

            // then
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getTitle()).isEqualTo("테스트 글");
            assertThat(result.isLiked()).isFalse();
        }
    }

    // ========== 헬퍼 ==========

    private Post buildPost(Long id, ClubMembership author, Set<Board> boards, Set<SemiCategory> categories) {
        Post p = Post.builder()
                .boards(boards)
                .author(author)
                .title("테스트 글")
                .content("테스트 내용")
                .semiCategories(categories)
                .build();
        p.setId(id);
        p.setCreatedAt(java.time.LocalDateTime.now());
        return p;
    }
}
