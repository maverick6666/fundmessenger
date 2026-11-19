package com.fundmessenger.fund.dto;

import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.entity.PositionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponse {

    private Long id;
    private Long teamId;
    private String ticker;
    private String tickerName;
    private String market;
    private PositionStatus status;
    private boolean infoConfirmed;

    // 보유 정보
    private BigDecimal averageBuyPrice;
    private BigDecimal totalQuantity;
    private BigDecimal totalBuyAmount;

    // 매매 계획
    private List<Map<String, Object>> buyPlan;
    private List<Map<String, Object>> takeProfitTargets;
    private List<Map<String, Object>> stopLossTargets;

    // 잔여 계획 건수
    private int remainingBuys;
    private int remainingTakeProfits;
    private int remainingStopLosses;

    // 매도/실현 손익
    private BigDecimal averageSellPrice;
    private BigDecimal totalSellAmount;
    private BigDecimal realizedProfitLoss;
    private BigDecimal profitRate;
    private Long holdingPeriodHours;

    // 수명주기
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private String openerName;
    private Long openerId;
    private String closerName;
    private Long closerId;
    private LocalDateTime createdAt;

    // 공개 설정
    private boolean published;
    private LocalDateTime publishedAt;
    private LocalDateTime lastExportedAt;

    // 경고 상태
    private PositionStatusInfo statusInfo;

    public static PositionResponse from(Position p, int remainingBuys, int remainingTp, int remainingSl,
                                         PositionStatusInfo statusInfo) {
        return PositionResponse.builder()
                .id(p.getId())
                .teamId(p.getTeam().getId())
                .ticker(p.getTicker())
                .tickerName(p.getTickerName())
                .market(p.getMarket())
                .status(p.getStatus())
                .infoConfirmed(p.isInfoConfirmed())
                .averageBuyPrice(p.getAverageBuyPrice())
                .totalQuantity(p.getTotalQuantity())
                .totalBuyAmount(p.getTotalBuyAmount())
                .buyPlan(p.getBuyPlan())
                .takeProfitTargets(p.getTakeProfitTargets())
                .stopLossTargets(p.getStopLossTargets())
                .remainingBuys(remainingBuys)
                .remainingTakeProfits(remainingTp)
                .remainingStopLosses(remainingSl)
                .averageSellPrice(p.getAverageSellPrice())
                .totalSellAmount(p.getTotalSellAmount())
                .realizedProfitLoss(p.getRealizedProfitLoss())
                .profitRate(p.getProfitRate())
                .holdingPeriodHours(p.getHoldingPeriodHours())
                .openedAt(p.getOpenedAt())
                .closedAt(p.getClosedAt())
                .openerName(p.getOpener() != null ? p.getOpener().getName() : null)
                .openerId(p.getOpener() != null ? p.getOpener().getId() : null)
                .closerName(p.getCloser() != null ? p.getCloser().getName() : null)
                .closerId(p.getCloser() != null ? p.getCloser().getId() : null)
                .createdAt(p.getCreatedAt())
                .published(p.isPublished())
                .publishedAt(p.getPublishedAt())
                .lastExportedAt(p.getLastExportedAt())
                .statusInfo(statusInfo)
                .build();
    }
}
