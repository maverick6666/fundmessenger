package com.fundmessenger.fund.repository;

import com.fundmessenger.fund.entity.AssetSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AssetSnapshotRepository extends JpaRepository<AssetSnapshot, Long> {

    List<AssetSnapshot> findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long teamId, LocalDate from, LocalDate to);

    List<AssetSnapshot> findByTeamIdOrderBySnapshotDateDesc(Long teamId);
}
