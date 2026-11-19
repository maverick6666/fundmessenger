package com.fundmessenger.board.repository;

import com.fundmessenger.board.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 게시판별 글 (M:N boards) — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.author a LEFT JOIN FETCH a.user " +
           "LEFT JOIN FETCH p.semiCategories " +
           "LEFT JOIN FETCH p.boards " +
           "LEFT JOIN FETCH p.teams " +
           "JOIN p.boards b " +
           "WHERE b.id = :boardId " +
           "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Post p JOIN p.boards b WHERE b.id = :boardId")
    Page<Post> findByBoardIdOrderByCreatedAtDesc(@Param("boardId") Long boardId, Pageable pageable);

    /** 게시판별 + M:N 태그 필터 — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.author a LEFT JOIN FETCH a.user " +
           "LEFT JOIN FETCH p.semiCategories " +
           "LEFT JOIN FETCH p.boards " +
           "LEFT JOIN FETCH p.teams " +
           "JOIN p.boards b " +
           "LEFT JOIN p.semiCategories sc " +
           "WHERE b.id = :boardId " +
           "AND (:tagId IS NULL OR sc.id = :tagId) " +
           "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Post p JOIN p.boards b LEFT JOIN p.semiCategories sc WHERE b.id = :boardId AND (:tagId IS NULL OR sc.id = :tagId)")
    Page<Post> findByBoardWithTagFilter(
            @Param("boardId") Long boardId,
            @Param("tagId") Long tagId,
            Pageable pageable);

    /** 게시판별 글 수 (M:N) */
    @Query("SELECT COUNT(DISTINCT p) FROM Post p JOIN p.boards b WHERE b.id = :boardId")
    long countByBoardId(@Param("boardId") Long boardId);

    /** 배치 카운트: 동아리 내 모든 게시판의 글 수를 한 번에 조회 (N+1 방지) */
    @Query("SELECT b.id, COUNT(DISTINCT p) FROM Post p JOIN p.boards b WHERE b.club.id = :clubId GROUP BY b.id")
    List<Object[]> countPostsByClubBoards(@Param("clubId") Long clubId);

    /** 팀별 글 수 (M:N) — 사이드바용 */
    @Query("SELECT t.id, COUNT(DISTINCT p) FROM Post p JOIN p.teams t JOIN p.boards b WHERE b.club.id = :clubId GROUP BY t.id")
    List<Object[]> countByTeamGrouped(@Param("clubId") Long clubId);

    long countByAuthorId(Long authorId);

    /** 특정 멤버가 작성한 글 (동아리 범위, M:N boards) — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.author a LEFT JOIN FETCH a.user " +
           "LEFT JOIN FETCH p.semiCategories " +
           "LEFT JOIN FETCH p.boards " +
           "LEFT JOIN FETCH p.teams " +
           "LEFT JOIN p.boards b " +
           "WHERE p.author.id = :authorId " +
           "AND (b.club.id = :clubId OR p.boards IS EMPTY) " +
           "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Post p LEFT JOIN p.boards b WHERE p.author.id = :authorId AND (b.club.id = :clubId OR p.boards IS EMPTY)")
    Page<Post> findByAuthorIdAndClubId(@Param("authorId") Long authorId, @Param("clubId") Long clubId, Pageable pageable);

    /** 특정 멤버가 작성한 글 + 태그 필터 — FETCH JOIN으로 N+1 방지 */
    @Query(value = "SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.author a LEFT JOIN FETCH a.user " +
           "LEFT JOIN FETCH p.semiCategories " +
           "LEFT JOIN FETCH p.boards " +
           "LEFT JOIN FETCH p.teams " +
           "LEFT JOIN p.boards b " +
           "LEFT JOIN p.semiCategories sc " +
           "WHERE p.author.id = :authorId " +
           "AND (b.club.id = :clubId OR p.boards IS EMPTY) " +
           "AND sc.id = :tagId " +
           "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Post p LEFT JOIN p.boards b LEFT JOIN p.semiCategories sc WHERE p.author.id = :authorId AND (b.club.id = :clubId OR p.boards IS EMPTY) AND sc.id = :tagId")
    Page<Post> findByAuthorIdAndClubIdAndSemiCategoryId(
            @Param("authorId") Long authorId, @Param("clubId") Long clubId,
            @Param("tagId") Long tagId, Pageable pageable);

    /** Cross-board: 팀별 + 태그 교차 필터 — Post.teams M:N 기준 */
    @Query(value = "SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.author a LEFT JOIN FETCH a.user " +
           "LEFT JOIN FETCH p.semiCategories " +
           "LEFT JOIN FETCH p.boards " +
           "LEFT JOIN FETCH p.teams " +
           "JOIN p.boards b " +
           "LEFT JOIN p.teams t " +
           "LEFT JOIN p.semiCategories sc " +
           "WHERE b.club.id = :clubId " +
           "AND (:teamId IS NULL OR t.id = :teamId) " +
           "AND (:tagId IS NULL OR sc.id = :tagId) " +
           "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Post p JOIN p.boards b LEFT JOIN p.teams t LEFT JOIN p.semiCategories sc WHERE b.club.id = :clubId AND (:teamId IS NULL OR t.id = :teamId) AND (:tagId IS NULL OR sc.id = :tagId)")
    Page<Post> findWithFilters(
            @Param("clubId") Long clubId,
            @Param("teamId") Long teamId,
            @Param("tagId") Long tagId,
            Pageable pageable);

    /** 다중 팀 + 다중 태그 교차 필터 */
    @Query(value = "SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.author a LEFT JOIN FETCH a.user " +
           "LEFT JOIN FETCH p.semiCategories " +
           "LEFT JOIN FETCH p.boards " +
           "LEFT JOIN FETCH p.teams " +
           "JOIN p.boards b " +
           "JOIN p.teams t " +
           "JOIN p.semiCategories sc " +
           "WHERE b.club.id = :clubId " +
           "AND t.id IN :teamIds " +
           "AND sc.id IN :tagIds " +
           "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Post p JOIN p.boards b JOIN p.teams t JOIN p.semiCategories sc WHERE b.club.id = :clubId AND t.id IN :teamIds AND sc.id IN :tagIds")
    Page<Post> findByTeamIdsAndTagIds(
            @Param("clubId") Long clubId,
            @Param("teamIds") List<Long> teamIds,
            @Param("tagIds") List<Long> tagIds,
            Pageable pageable);

    /** 다중 팀 필터만 */
    @Query(value = "SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.author a LEFT JOIN FETCH a.user " +
           "LEFT JOIN FETCH p.semiCategories " +
           "LEFT JOIN FETCH p.boards " +
           "LEFT JOIN FETCH p.teams " +
           "JOIN p.boards b " +
           "JOIN p.teams t " +
           "WHERE b.club.id = :clubId " +
           "AND t.id IN :teamIds " +
           "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Post p JOIN p.boards b JOIN p.teams t WHERE b.club.id = :clubId AND t.id IN :teamIds")
    Page<Post> findByTeamIds(
            @Param("clubId") Long clubId,
            @Param("teamIds") List<Long> teamIds,
            Pageable pageable);

    /** 다중 태그 필터만 */
    @Query(value = "SELECT DISTINCT p FROM Post p " +
           "LEFT JOIN FETCH p.author a LEFT JOIN FETCH a.user " +
           "LEFT JOIN FETCH p.semiCategories " +
           "LEFT JOIN FETCH p.boards " +
           "LEFT JOIN FETCH p.teams " +
           "JOIN p.boards b " +
           "JOIN p.semiCategories sc " +
           "WHERE b.club.id = :clubId " +
           "AND sc.id IN :tagIds " +
           "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Post p JOIN p.boards b JOIN p.semiCategories sc WHERE b.club.id = :clubId AND sc.id IN :tagIds")
    Page<Post> findByTagIds(
            @Param("clubId") Long clubId,
            @Param("tagIds") List<Long> tagIds,
            Pageable pageable);

    /** 아카이브: 작성자의 기수(membership_generations)로 조회 */
    @Query("SELECT DISTINCT p FROM Post p " +
           "JOIN p.boards b " +
           "JOIN p.author m " +
           "JOIN m.generations g " +
           "WHERE b.club.id = :clubId " +
           "AND g.id = :generationId " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findByAuthorGenerationId(
            @Param("clubId") Long clubId,
            @Param("generationId") Long generationId,
            Pageable pageable);

    /** 게시판의 모든 글에서 해당 게시판 연결만 해제 (M:N — 게시판 삭제 시) */
    @Modifying
    @Query(value = "DELETE FROM post_boards WHERE board_id = :boardId", nativeQuery = true)
    void unlinkAllFromBoard(@Param("boardId") Long boardId);

    /** M:N 조인테이블에서 특정 태그 제거 (카테고리 삭제 시) */
    @Modifying
    @Query(value = "DELETE FROM post_semi_categories WHERE semi_category_id = :categoryId", nativeQuery = true)
    void removeTagFromAllPosts(@Param("categoryId") Long categoryId);

    /** CB-7: 재조명 대상 — 최근 1주일 미재조명 글 (좋아요순 → 최신순) */
    @Query("SELECT DISTINCT p FROM Post p " +
           "JOIN p.boards b " +
           "WHERE b.club.id = :clubId " +
           "AND b.boardType <> com.fundmessenger.board.entity.BoardType.AI " +
           "AND p.featuredAt IS NULL " +
           "AND p.createdAt >= :since " +
           "ORDER BY p.likeCount DESC, p.createdAt DESC")
    List<Post> findFeaturedCandidatesRecent(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);

    /** CB-7: 재조명 백필 — 전체 미재조명 글 (좋아요순 → 최신순) */
    @Query("SELECT DISTINCT p FROM Post p " +
           "JOIN p.boards b " +
           "WHERE b.club.id = :clubId " +
           "AND b.boardType <> com.fundmessenger.board.entity.BoardType.AI " +
           "AND p.featuredAt IS NULL " +
           "ORDER BY p.likeCount DESC, p.createdAt DESC")
    List<Post> findFeaturedCandidatesAll(@Param("clubId") Long clubId);

    /** Atomic counter: likeCount */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementLikeCount(@Param("id") Long id);

    /** 대시보드: 동아리 내 기간 글 수 */
    @Query("SELECT COUNT(DISTINCT p) FROM Post p JOIN p.boards b " +
           "WHERE b.club.id = :clubId AND p.createdAt >= :since")
    long countByClubIdAndCreatedAtAfter(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);

    /** 대시보드: 주간 글 수 추이 */
    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('week', p.created_at), 'YYYY-MM-DD'), COUNT(DISTINCT p.id) " +
           "FROM posts p JOIN post_boards pb ON p.id = pb.post_id JOIN boards b ON pb.board_id = b.id " +
           "WHERE b.club_id = :clubId AND p.created_at >= :since " +
           "GROUP BY DATE_TRUNC('week', p.created_at) " +
           "ORDER BY DATE_TRUNC('week', p.created_at) ASC", nativeQuery = true)
    List<Object[]> findWeeklyPostCounts(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);
}
