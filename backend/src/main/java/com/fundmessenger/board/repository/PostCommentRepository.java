package com.fundmessenger.board.repository;

import com.fundmessenger.board.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    List<PostComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);

    @Modifying
    @Query("DELETE FROM PostComment c WHERE c.post IN (SELECT p FROM Post p JOIN p.boards b WHERE b.id = :boardId)")
    void deleteByBoardId(@Param("boardId") Long boardId);

    /** AI 응답 대기 중인 댓글 (반응형 봇용) */
    @Query("SELECT c FROM PostComment c WHERE c.needsAiResponse = true AND c.post IN (SELECT p FROM Post p JOIN p.boards b WHERE b.club.id = :clubId)")
    List<PostComment> findNeedsAiResponseByClubId(@Param("clubId") Long clubId);

    /** 대시보드: 동아리 내 기간 댓글 수 */
    @Query("SELECT COUNT(c) FROM PostComment c JOIN c.post p JOIN p.boards b " +
           "WHERE b.club.id = :clubId AND c.createdAt >= :since")
    long countByClubIdAndCreatedAtAfter(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);

    /** 대시보드: 주간 댓글 수 추이 */
    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('week', c.created_at), 'YYYY-MM-DD'), COUNT(c.id) " +
           "FROM post_comments c JOIN posts p ON c.post_id = p.id " +
           "JOIN post_boards pb ON p.id = pb.post_id JOIN boards b ON pb.board_id = b.id " +
           "WHERE b.club_id = :clubId AND c.created_at >= :since " +
           "GROUP BY DATE_TRUNC('week', c.created_at) " +
           "ORDER BY DATE_TRUNC('week', c.created_at) ASC", nativeQuery = true)
    List<Object[]> findWeeklyCommentCounts(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);
}
