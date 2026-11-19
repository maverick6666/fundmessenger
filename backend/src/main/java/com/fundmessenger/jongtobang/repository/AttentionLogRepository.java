package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.AttentionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AttentionLogRepository extends JpaRepository<AttentionLog, Long> {

    long countByCalculatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<AttentionLog> findByCalculatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Modifying
    @Query("DELETE FROM AttentionLog a WHERE a.calculatedAt BETWEEN :from AND :to")
    int deleteByCalculatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT MIN(a.calculatedAt) FROM AttentionLog a")
    LocalDateTime findMinCalculatedAt();

    @Query("SELECT MAX(a.calculatedAt) FROM AttentionLog a")
    LocalDateTime findMaxCalculatedAt();
}
