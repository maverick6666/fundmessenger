package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.ThreadReply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ThreadReplyRepository extends JpaRepository<ThreadReply, Long> {

    /** 스레드의 답글 (시간순) */
    Page<ThreadReply> findByThreadIdOrderByCreatedAtAsc(Long threadId, Pageable pageable);

    /** 스레드의 답글 수 */
    long countByThreadId(Long threadId);

    /** AI 응답 대기 중인 답글 (반응형 봇용) */
    List<ThreadReply> findByNeedsAiResponseTrueAndThread_Club_Id(Long clubId);

    /** 스레드 삭제 시 전체 답글 벌크 삭제 (SEC-8) */
    @Modifying
    @Query("DELETE FROM ThreadReply tr WHERE tr.thread.id = :threadId")
    void deleteAllByThreadId(@Param("threadId") Long threadId);
}
