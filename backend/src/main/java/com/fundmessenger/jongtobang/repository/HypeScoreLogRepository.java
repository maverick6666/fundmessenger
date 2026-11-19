package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.HypeScoreLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface HypeScoreLogRepository extends JpaRepository<HypeScoreLog, Long> {

    long countByCalculatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<HypeScoreLog> findByCalculatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Modifying
    @Query("DELETE FROM HypeScoreLog h WHERE h.calculatedAt BETWEEN :from AND :to")
    int deleteByCalculatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT MIN(h.calculatedAt) FROM HypeScoreLog h")
    LocalDateTime findMinCalculatedAt();

    @Query("SELECT MAX(h.calculatedAt) FROM HypeScoreLog h")
    LocalDateTime findMaxCalculatedAt();
}
