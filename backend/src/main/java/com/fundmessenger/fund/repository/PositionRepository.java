package com.fundmessenger.fund.repository;

import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.entity.PositionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByTeamIdAndStatusOrderByCreatedAtDesc(Long teamId, PositionStatus status);

    List<Position> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    Optional<Position> findByTeamIdAndTickerAndMarketAndStatus(
            Long teamId, String ticker, String market, PositionStatus status);

    /**
     * 비관적 락: 동시 매수 승인 시 동일 종목 OPEN 포지션 중복 생성 방지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Position p WHERE p.team.id = :teamId AND p.ticker = :ticker " +
           "AND p.market = :market AND p.status = :status")
    Optional<Position> findByTeamIdAndTickerAndMarketAndStatusForUpdate(
            @Param("teamId") Long teamId, @Param("ticker") String ticker,
            @Param("market") String market, @Param("status") PositionStatus status);

    /**
     * 비관적 락: 동시 매도 승인 시 보유 수량 초과 방지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Position p WHERE p.id = :id")
    Optional<Position> findByIdForUpdate(@Param("id") Long id);

    long countByTeamId(Long teamId);

    /** 공개된 종목 리스트 (lastExportedAt 최신순) */
    List<Position> findByTeamIdAndPublishedTrueOrderByLastExportedAtDesc(Long teamId);

    /** 공개된 종목 + 상태 필터 (lastExportedAt 최신순) */
    List<Position> findByTeamIdAndPublishedTrueAndStatusOrderByLastExportedAtDesc(Long teamId, PositionStatus status);
}
