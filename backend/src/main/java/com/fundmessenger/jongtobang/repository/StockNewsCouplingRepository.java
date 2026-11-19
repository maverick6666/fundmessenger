package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.StockNewsCoupling;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockNewsCouplingRepository extends JpaRepository<StockNewsCoupling, Long> {

    /** 특정 종목의 뉴스: 1차 감성우선(호재/악재→중립), 2차 updatedAt DESC */
    @Query("SELECT c FROM StockNewsCoupling c WHERE c.stock.id = :stockId " +
           "ORDER BY CASE WHEN c.canonicalNews.sentimentType IN ('bullish','bearish') THEN 0 ELSE 1 END, " +
           "c.canonicalNews.updatedAt DESC")
    List<StockNewsCoupling> findByStockIdSentimentFirst(@Param("stockId") Long stockId, Pageable pageable);

    /** 특정 종목의 전체 뉴스 수 */
    long countByStockId(Long stockId);

    /** 배치 카운트: 여러 종목의 뉴스 수를 한 번에 조회 (N+1 방지) */
    @Query("SELECT c.stock.id, COUNT(c) FROM StockNewsCoupling c WHERE c.stock.id IN :stockIds GROUP BY c.stock.id")
    List<Object[]> countByStockIds(@Param("stockIds") List<Long> stockIds);

    Optional<StockNewsCoupling> findByStockIdAndCanonicalNewsId(Long stockId, Long canonicalNewsId);

    /** 특정 종목의 coupling을 생성일 역순으로 조회 (급등 이유 검색에서 최근 뉴스 존재 확인용) */
    List<StockNewsCoupling> findByStockIdOrderByCreatedAtDesc(Long stockId);

    /** 특정 canonical news의 모든 coupling */
    List<StockNewsCoupling> findByCanonicalNewsId(Long canonicalNewsId);

    /** 최근 N시간 이내 생성된 커플링이 있는 종목 ID 목록 */
    @Query("SELECT DISTINCT c.stock.id FROM StockNewsCoupling c WHERE c.canonicalNews.createdAt >= :since")
    List<Long> findStockIdsWithNewNews(@Param("since") LocalDateTime since);

    /** 뉴스 커플링이 있는 종목 ID 목록 */
    @Query("SELECT DISTINCT c.stock.id FROM StockNewsCoupling c")
    List<Long> findDistinctStockIds();

    /** Atomic counter: commentCount */
    @Modifying
    @Query("UPDATE StockNewsCoupling c SET c.commentCount = c.commentCount + 1 WHERE c.id = :id")
    void incrementCommentCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE StockNewsCoupling c SET c.commentCount = CASE WHEN c.commentCount > 0 THEN c.commentCount - 1 ELSE 0 END WHERE c.id = :id")
    void decrementCommentCount(@Param("id") Long id);

    /** Atomic counter: likeCount */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE StockNewsCoupling c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE StockNewsCoupling c SET c.likeCount = CASE WHEN c.likeCount > 0 THEN c.likeCount - 1 ELSE 0 END WHERE c.id = :id")
    void decrementLikeCount(@Param("id") Long id);
}
