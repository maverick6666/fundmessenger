package com.fundmessenger.fund.repository;

import com.fundmessenger.fund.entity.PlanSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanSnapshotRepository extends JpaRepository<PlanSnapshot, Long> {

    List<PlanSnapshot> findByPositionIdOrderByVersionDesc(Long positionId);

    int countByPositionId(Long positionId);
}
