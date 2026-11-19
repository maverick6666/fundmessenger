package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.ThreadLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ThreadLikeRepository extends JpaRepository<ThreadLike, Long> {

    Optional<ThreadLike> findByThreadIdAndMemberId(Long threadId, Long memberId);

    boolean existsByThreadIdAndMemberId(Long threadId, Long memberId);

    /** 스레드 삭제 시 전체 좋아요 벌크 삭제 (SEC-8) */
    @Modifying
    @Query("DELETE FROM ThreadLike tl WHERE tl.thread.id = :threadId")
    void deleteAllByThreadId(@Param("threadId") Long threadId);
}
