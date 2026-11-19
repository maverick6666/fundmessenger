package com.fundmessenger.board.service;

import com.fundmessenger.board.dto.PostCommentResponse;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.entity.PostComment;
import com.fundmessenger.board.repository.PostCommentRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.config.InputSanitizer;
import com.fundmessenger.notification.entity.NotificationType;
import com.fundmessenger.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostCommentService {

    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;
    private final ClubAuthorizationService authService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<PostCommentResponse> getComments(Long clubId, Long postId, Long userId) {
        authService.validateClubMember(userId, clubId);
        validatePostInClub(postId, clubId);

        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(PostCommentResponse::from)
                .toList();
    }

    @Transactional
    public PostCommentResponse createComment(Long clubId, Long postId, String content, Long parentCommentId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        Post post = validatePostInClub(postId, clubId);

        PostComment parentComment = null;
        boolean needsAiResponse = false;

        if (parentCommentId != null) {
            parentComment = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new IllegalArgumentException("부모 댓글을 찾을 수 없습니다: " + parentCommentId));
            // depth-1 제한: 부모가 이미 대댓글이면 거부
            if (parentComment.getParentComment() != null) {
                throw new IllegalArgumentException("대댓글에는 답글을 달 수 없습니다 (depth-1 제한)");
            }
            // 부모가 AI 생성이면 AI 응답 필요 플래그
            if (parentComment.isAiGenerated()) {
                needsAiResponse = true;
            }
        }

        PostComment comment = PostComment.builder()
                .post(post)
                .parentComment(parentComment)
                .author(membership)
                .content(InputSanitizer.sanitizePlainText(content.trim()))
                .needsAiResponse(needsAiResponse)
                .build();

        PostComment saved = commentRepository.save(comment);
        log.info("게시글 댓글 작성: postId={}, commentId={}, parentId={}", postId, saved.getId(), parentCommentId);

        // 알림 발행: 대댓글이면 부모 댓글 작성자에게, 아니면 게시글 작성자에게
        Long authorUserId = post.getAuthor().getUser().getId();
        if (parentComment != null) {
            Long parentAuthorUserId = parentComment.getAuthor().getUser().getId();
            if (!parentAuthorUserId.equals(userId)) {
                eventPublisher.publishEvent(NotificationEvent.of(
                        clubId, parentAuthorUserId, NotificationType.COMMENT_REPLY,
                        membership.getName() + "님이 답글을 달았습니다",
                        content.trim().length() > 50 ? content.trim().substring(0, 50) + "…" : content.trim(),
                        "/club/boards/posts/" + postId
                ));
            }
        } else if (!authorUserId.equals(userId)) {
            eventPublisher.publishEvent(NotificationEvent.of(
                    clubId, authorUserId, NotificationType.POST_COMMENT,
                    membership.getName() + "님이 댓글을 달았습니다",
                    content.trim().length() > 50 ? content.trim().substring(0, 50) + "…" : content.trim(),
                    "/club/boards/posts/" + postId
            ));
        }

        return PostCommentResponse.from(saved);
    }

    @Transactional
    public void deleteComment(Long clubId, Long postId, Long commentId, Long userId) {
        ClubMembership membership = authService.validateClubMember(userId, clubId);
        validatePostInClub(postId, clubId);

        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다: " + commentId));

        if (!comment.getPost().getId().equals(postId)) {
            throw new IllegalArgumentException("해당 게시글의 댓글이 아닙니다");
        }

        boolean isAuthor = comment.getAuthor().getId().equals(membership.getId());
        boolean isAdmin = authService.isExecutive(membership);

        if (!isAuthor && !isAdmin) {
            throw new SecurityException("댓글을 삭제할 권한이 없습니다");
        }

        commentRepository.delete(comment);
        log.info("게시글 댓글 삭제: postId={}, commentId={}", postId, commentId);
    }

    private Post validatePostInClub(Long postId, Long clubId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다: " + postId));
        boolean belongsToClub = post.getBoards().stream()
                .anyMatch(b -> b.getClub().getId().equals(clubId));
        if (!belongsToClub) {
            throw new SecurityException("해당 동아리의 게시글이 아닙니다");
        }
        return post;
    }
}
