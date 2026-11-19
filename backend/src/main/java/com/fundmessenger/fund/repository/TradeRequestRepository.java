package com.fundmessenger.fund.repository;

import com.fundmessenger.fund.entity.RequestStatus;
import com.fundmessenger.fund.entity.TradeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRequestRepository extends JpaRepository<TradeRequest, Long> {

    List<TradeRequest> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    List<TradeRequest> findByTeamIdAndStatusOrderByCreatedAtDesc(Long teamId, RequestStatus status);

    List<TradeRequest> findByTeamIdAndRequesterIdOrderByCreatedAtDesc(Long teamId, Long requesterId);

    List<TradeRequest> findByPositionId(Long positionId);

    /** 체결된 요청만 (공개용 매매 흐름) */
    List<TradeRequest> findByPositionIdAndStatusOrderByCreatedAtAsc(Long positionId, RequestStatus status);
}
