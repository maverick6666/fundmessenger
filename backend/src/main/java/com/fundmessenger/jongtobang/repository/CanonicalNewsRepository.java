package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.CanonicalNews;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CanonicalNewsRepository extends JpaRepository<CanonicalNews, Long> {

    List<CanonicalNews> findAllByOrderByUpdatedAtDesc();

    List<CanonicalNews> findTop10ByAuthorNameIsNotNullOrderByCreatedAtDesc();

    /**
     * Layer 0 매칭 윈도우: 최근 N일 이내, 최대 limit건 (updatedAt 최신순)
     * 컨텍스트 윈도우 지속가능성 — canonical 수천 건이어도 프롬프트 토큰 안전
     */
    @Query("SELECT c FROM CanonicalNews c WHERE c.updatedAt >= :since ORDER BY c.updatedAt DESC")
    List<CanonicalNews> findRecentForMatching(@Param("since") LocalDateTime since, Pageable pageable);
}
