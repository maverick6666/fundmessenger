package com.fundmessenger.fund.repository;

import com.fundmessenger.fund.entity.FundAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundAuditLogRepository extends JpaRepository<FundAuditLog, Long> {

    List<FundAuditLog> findByPositionIdOrderByCreatedAtDesc(Long positionId);
}
