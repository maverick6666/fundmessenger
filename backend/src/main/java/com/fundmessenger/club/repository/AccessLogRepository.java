package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    boolean existsByUserIdAndClubIdAndDate(Long userId, Long clubId, LocalDate date);

    @Query("SELECT a.date FROM AccessLog a WHERE a.user.id = :userId AND a.club.id = :clubId " +
           "AND a.date >= :startDate AND a.date <= :endDate ORDER BY a.date ASC")
    List<LocalDate> findDatesByUserIdAndClubIdAndDateBetween(
            @Param("userId") Long userId,
            @Param("clubId") Long clubId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 기간 내 접속한 고유 사용자 수 */
    @Query("SELECT COUNT(DISTINCT a.user.id) FROM AccessLog a " +
           "WHERE a.club.id = :clubId AND a.date >= :startDate AND a.date <= :endDate")
    long countDistinctUsersByClubIdAndDateBetween(
            @Param("clubId") Long clubId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 기간 내 멤버별 접속일 수 (userId, count) */
    @Query("SELECT a.user.id, COUNT(a) FROM AccessLog a " +
           "WHERE a.club.id = :clubId AND a.date >= :startDate AND a.date <= :endDate " +
           "GROUP BY a.user.id")
    List<Object[]> countAccessDaysByMember(
            @Param("clubId") Long clubId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 대시보드: 일별 고유 접속자 수 (DAU 라인차트) */
    @Query("SELECT a.date, COUNT(DISTINCT a.user.id) FROM AccessLog a " +
           "WHERE a.club.id = :clubId AND a.date >= :startDate AND a.date <= :endDate " +
           "GROUP BY a.date ORDER BY a.date ASC")
    List<Object[]> findDailyAccessCounts(
            @Param("clubId") Long clubId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 대시보드: 오늘 접속자 수 */
    @Query("SELECT COUNT(DISTINCT a.user.id) FROM AccessLog a " +
           "WHERE a.club.id = :clubId AND a.date = :date")
    long countDistinctUsersByClubIdAndDate(
            @Param("clubId") Long clubId,
            @Param("date") LocalDate date);
}
