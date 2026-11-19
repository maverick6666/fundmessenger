package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.CreditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CreditHistoryRepository extends JpaRepository<CreditHistory, Long> {

    List<CreditHistory> findByClubIdOrderByCreatedAtDesc(Long clubId);

    /** 대시보드: 타입별 크레딧 총액 */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CreditHistory c WHERE c.type = :type")
    long sumAmountByType(@Param("type") CreditHistory.CreditType type);

    /** 대시보드: 동아리별 타입별 크레딧 합계 */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CreditHistory c " +
           "WHERE c.club.id = :clubId AND c.type = :type AND c.createdAt >= :since")
    long sumAmountByClubIdAndTypeAndCreatedAtAfter(
            @Param("clubId") Long clubId,
            @Param("type") CreditHistory.CreditType type,
            @Param("since") LocalDateTime since);

    /** 대시보드: 월별 크레딧 흐름 (타입별 그룹) */
    @Query("SELECT FUNCTION('TO_CHAR', c.createdAt, 'YYYY-MM'), c.type, SUM(c.amount) " +
           "FROM CreditHistory c WHERE c.club.id = :clubId AND c.createdAt >= :since " +
           "GROUP BY FUNCTION('TO_CHAR', c.createdAt, 'YYYY-MM'), c.type " +
           "ORDER BY FUNCTION('TO_CHAR', c.createdAt, 'YYYY-MM') ASC")
    List<Object[]> findMonthlyCreditFlow(@Param("clubId") Long clubId, @Param("since") LocalDateTime since);

    /** 대시보드: 동아리별 타입별 크레딧 분포 (전체 기간) */
    @Query("SELECT c.type, SUM(c.amount) FROM CreditHistory c " +
           "WHERE c.club.id = :clubId GROUP BY c.type")
    List<Object[]> findTypeDistribution(@Param("clubId") Long clubId);
}
