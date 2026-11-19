package com.fundmessenger.board.repository;

import com.fundmessenger.board.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndMemberId(Long postId, Long memberId);

    boolean existsByPostIdAndMemberId(Long postId, Long memberId);

    void deleteByPostId(Long postId);

    /** 대시보드: 동아리 내 기간 좋아요 수 */
    @Query("SELECT COUNT(l) FROM PostLike l JOIN l.post p JOIN p.boards b " +
           "WHERE b.club.id = :clubId AND l.createdAt >= :since")
    long countByClubIdAndCreatedAtAfter(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);
}
