package com.fundmessenger.fund.repository;

import com.fundmessenger.fund.entity.CashAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashAdjustmentRepository extends JpaRepository<CashAdjustment, Long> {

    List<CashAdjustment> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
