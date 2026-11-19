package com.fundmessenger.jongtobang.repository;

import com.fundmessenger.jongtobang.entity.ScoreDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ScoreDailySnapshotRepository extends JpaRepository<ScoreDailySnapshot, Long> {

    long countBySnapshotDateBetween(LocalDate from, LocalDate to);

    List<ScoreDailySnapshot> findBySnapshotDateBetween(LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM ScoreDailySnapshot s WHERE s.snapshotDate BETWEEN :from AND :to")
    int deleteBySnapshotDateBetween(LocalDate from, LocalDate to);

    @Query("SELECT MIN(s.snapshotDate) FROM ScoreDailySnapshot s")
    LocalDate findMinSnapshotDate();

    @Query("SELECT MAX(s.snapshotDate) FROM ScoreDailySnapshot s")
    LocalDate findMaxSnapshotDate();
}
