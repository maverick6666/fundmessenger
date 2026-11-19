package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.NewsComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsCommentRepository extends JpaRepository<NewsComment, Long> {

    List<NewsComment> findByCouplingIdOrderByCreatedAtAsc(Long couplingId);

    long countByCouplingId(Long couplingId);

    long countByCouplingIdAndCreatedAtAfter(Long couplingId, LocalDateTime since);

    /** AI 응답 대기 중인 뉴스 댓글 */
    List<NewsComment> findByNeedsAiResponseTrue();
}
