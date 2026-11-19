package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.StockThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockThreadRepository extends JpaRepository<StockThread, Long> {

    /** 종목별 스레드 (최신순) — Stock FK 기반 */
    Page<StockThread> findByStockIdOrderByCreatedAtDesc(Long stockId, Pageable pageable);

    /** 종목별 스레드 (최신순) — 동아리 스코핑 (SEC-1) */
    Page<StockThread> findByStockIdAndClubIdOrderByCreatedAtDesc(Long stockId, Long clubId, Pageable pageable);

    /** 종목별 스레드 (추천순 = likeCount*3 + replyCount*2) */
    @Query("SELECT t FROM StockThread t WHERE t.stock.id = :stockId ORDER BY (t.likeCount * 3 + t.replyCount * 2) DESC, t.createdAt DESC")
    Page<StockThread> findByStockIdOrderByPopularity(@Param("stockId") Long stockId, Pageable pageable);

    /** 종목별 스레드 (추천순) — 동아리 스코핑 (SEC-1) */
    @Query("SELECT t FROM StockThread t WHERE t.stock.id = :stockId AND t.club.id = :clubId ORDER BY (t.likeCount * 3 + t.replyCount * 2) DESC, t.createdAt DESC")
    Page<StockThread> findByStockIdAndClubIdOrderByPopularity(@Param("stockId") Long stockId, @Param("clubId") Long clubId, Pageable pageable);

    /** 종목별 스레드 수 */
    long countByStockId(Long stockId);

    /** 종목별 스레드 수 — 동아리 스코핑 (SEC-1) */
    long countByStockIdAndClubId(Long stockId, Long clubId);

    /** 배치 카운트: 여러 종목의 스레드 수를 한 번에 조회 (N+1 방지) */
    @Query("SELECT t.stock.id, COUNT(t) FROM StockThread t WHERE t.stock.id IN :stockIds GROUP BY t.stock.id")
    List<Object[]> countByStockIds(@Param("stockIds") List<Long> stockIds);

    /** 배치 카운트 — 동아리 스코핑 (SEC-1) */
    @Query("SELECT t.stock.id, COUNT(t) FROM StockThread t WHERE t.stock.id IN :stockIds AND t.club.id = :clubId GROUP BY t.stock.id")
    List<Object[]> countByStockIdsAndClubId(@Param("stockIds") List<Long> stockIds, @Param("clubId") Long clubId);

    /** 동아리 전체 스레드 (최신순) — 레거시 호환 */
    Page<StockThread> findByClubIdOrderByCreatedAtDesc(Long clubId, Pageable pageable);

    /** 동아리 내 특정 종목코드 스레드 — 레거시 호환 */
    Page<StockThread> findByClubIdAndStockCodeOrderByCreatedAtDesc(Long clubId, String stockCode, Pageable pageable);

    /** 동아리 내 활성 종목 목록 — 레거시 호환 */
    @Query("SELECT DISTINCT t.stockCode, t.stockName, t.market FROM StockThread t WHERE t.club.id = :clubId ORDER BY t.stockName ASC")
    List<Object[]> findDistinctStocksByClubId(@Param("clubId") Long clubId);

    long countByClubIdAndStockCode(Long clubId, String stockCode);

    /** Atomic counter: replyCount */
    @Modifying
    @Query("UPDATE StockThread t SET t.replyCount = t.replyCount + 1 WHERE t.id = :id")
    void incrementReplyCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE StockThread t SET t.replyCount = CASE WHEN t.replyCount > 0 THEN t.replyCount - 1 ELSE 0 END WHERE t.id = :id")
    void decrementReplyCount(@Param("id") Long id);

    /** Atomic counter: likeCount */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE StockThread t SET t.likeCount = t.likeCount + 1 WHERE t.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE StockThread t SET t.likeCount = CASE WHEN t.likeCount > 0 THEN t.likeCount - 1 ELSE 0 END WHERE t.id = :id")
    void decrementLikeCount(@Param("id") Long id);
}
