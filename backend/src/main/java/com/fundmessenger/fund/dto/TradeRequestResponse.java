package com.fundmessenger.fund.dto;

import com.fundmessenger.fund.entity.TradeRequest;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
public class TradeRequestResponse {

    private Long id;
    private Long teamId;
    private String requestType;
    private String status;

    // 요청자/승인자
    private Long requesterId;
    private String requesterName;
    private Long approverId;
    private String approverName;

    // 매수 필드
    private String targetTicker;
    private String targetMarket;
    private String targetTickerName;
    private BigDecimal orderQuantity;
    private BigDecimal buyPrice;
    private List<Map<String, Object>> buyOrders;
    private BigDecimal targetRatio;
    private List<Map<String, Object>> takeProfitTargets;
    private List<Map<String, Object>> stopLossTargets;

    // 매도 필드
    private Long positionId;
    private BigDecimal sellQuantity;
    private BigDecimal sellPrice;
    private String sellReason;

    // 공통
    private String memo;

    // 승인/거절
    private LocalDateTime approvedAt;
    private BigDecimal executedPrice;
    private BigDecimal executedQuantity;
    private LocalDateTime executedAt;
    private String rejectionReason;

    // 시간
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TradeRequestResponse from(TradeRequest req) {
        return TradeRequestResponse.builder()
                .id(req.getId())
                .teamId(req.getTeam().getId())
                .requestType(req.getRequestType().name())
                .status(req.getStatus().name())
                .requesterId(req.getRequester().getId())
                .requesterName(req.getRequester().getName())
                .approverId(req.getApprover() != null ? req.getApprover().getId() : null)
                .approverName(req.getApprover() != null ? req.getApprover().getName() : null)
                .targetTicker(req.getTargetTicker())
                .targetMarket(req.getTargetMarket())
                .targetTickerName(req.getTargetTickerName())
                .orderQuantity(req.getOrderQuantity())
                .buyPrice(req.getBuyPrice())
                .buyOrders(req.getBuyOrders())
                .targetRatio(req.getTargetRatio())
                .takeProfitTargets(req.getTakeProfitTargets())
                .stopLossTargets(req.getStopLossTargets())
                .positionId(req.getPosition() != null ? req.getPosition().getId() : null)
                .sellQuantity(req.getSellQuantity())
                .sellPrice(req.getSellPrice())
                .sellReason(req.getSellReason())
                .memo(req.getMemo())
                .approvedAt(req.getApprovedAt())
                .executedPrice(req.getExecutedPrice())
                .executedQuantity(req.getExecutedQuantity())
                .executedAt(req.getExecutedAt())
                .rejectionReason(req.getRejectionReason())
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();
    }
}
