package com.fundmessenger.board.service;

import com.fundmessenger.board.dto.PostCommentResponse;
import com.fundmessenger.board.entity.*;
import com.fundmessenger.board.repository.PostCommentRepository;
import com.fundmessenger.board.repository.PostRepository;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PostCommentServiceTest {

    @Mock private PostCommentRepository commentRepository;
    @Mock private PostRepository postRepository;
    @Mock private ClubAuthorizationService authService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PostCommentService postCommentService;

    private User user1, user2;
    private Club club;
    private ClubMembership member1, member2;
    private Board board;
    private Post post;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "테스트동아리");
        user1 = TestFixtures.user(10L, "author@test.dev");
        user2 = TestFixtures.user(20L, "other@test.dev");
        member1 = TestFixtures.approvedMembership(100L, user1, club);
        member2 = TestFixtures.approvedMembership(200L, user2, club);

        board = TestFixtures.everyoneBoard(1L, club);
        post = Post.builder()
                .boards(new HashSet<>(Set.of(board)))
                .author(member1)
                .title("테스트 게시글")
                .content("내용")
                .build();
        post.setId(1L);
        post.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("댓글 작성 성공 — 게시글에 댓글을 달면 저장되고 응답이 반환된다")
    void createComment_success() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(member1);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        PostComment saved = PostComment.builder()
                .post(post)
                .author(member1)
                .content("좋은 글이네요")
                .build();
        saved.setId(1L);
        saved.setCreatedAt(LocalDateTime.now());

        given(commentRepository.save(any(PostComment.class))).willReturn(saved);

        // when
        PostCommentResponse response = postCommentService.createComment(1L, 1L, "좋은 글이네요", null, 10L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("좋은 글이네요");
        then(commentRepository).should().save(any(PostComment.class));
    }

    @Test
    @DisplayName("댓글 삭제 성공 — 작성자가 본인 댓글을 삭제할 수 있다")
    void deleteComment_byAuthor_success() {
        // given
        given(authService.validateClubMember(10L, 1L)).willReturn(member1);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        PostComment comment = PostComment.builder()
                .post(post)
                .author(member1)
                .content("삭제할 댓글")
                .build();
        comment.setId(10L);
        comment.setCreatedAt(LocalDateTime.now());

        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));
        given(authService.isExecutive(member1)).willReturn(false);

        // when
        postCommentService.deleteComment(1L, 1L, 10L, 10L);

        // then
        then(commentRepository).should().delete(comment);
    }

    @Test
    @DisplayName("댓글 삭제 실패 — 작성자가 아니고 임원도 아니면 SecurityException")
    void deleteComment_byNonAuthor_fails() {
        // given
        given(authService.validateClubMember(20L, 1L)).willReturn(member2);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        PostComment comment = PostComment.builder()
                .post(post)
                .author(member1)  // member1이 작성
                .content("타인의 댓글")
                .build();
        comment.setId(10L);
        comment.setCreatedAt(LocalDateTime.now());

        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));
        given(authService.isExecutive(member2)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> postCommentService.deleteComment(1L, 1L, 10L, 20L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("권한");
    }
}
