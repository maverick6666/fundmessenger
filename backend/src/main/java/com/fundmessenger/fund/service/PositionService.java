package com.fundmessenger.fund.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.entity.TeamType;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.fund.dto.*;
import com.fundmessenger.fund.entity.*;
import com.fundmessenger.fund.repository.DecisionNoteRepository;
import com.fundmessenger.fund.repository.FundAuditLogRepository;
import com.fundmessenger.fund.repository.PlanSnapshotRepository;
import com.fundmessenger.fund.repository.PositionRepository;
import com.fundmessenger.fund.repository.TradeRequestRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;
    private final TeamRepository teamRepository;
    private final TradeRequestRepository tradeRequestRepository;
    private final DecisionNoteRepository decisionNoteRepository;
    private final FundAuditLogRepository auditLogRepository;
    private final PlanSnapshotRepository planSnapshotRepository;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ========================
    // Read operations
    // ========================

    @Transactional(readOnly = true)
    public Position getPositionById(Long positionId) {
        return positionRepository.findById(positionId)
                .orElseThrow(() -> new NotFoundException("포지션", positionId));
    }

    @Transactional(readOnly = true)
    public List<Position> getPositions(Long teamId, PositionStatus status) {
        validateFundTeam(teamId);
        if (status != null) {
            return positionRepository.findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, status);
        }
        return positionRepository.findByTeamIdOrderByCreatedAtDesc(teamId);
    }

    // ========================
    // Create operations
    // ========================

    /**
     * 포지션 직접 생성 (매니저가 직접 생성 — isInfoConfirmed=true)
     */
    @Transactional
    public Position createPosition(Long teamId, CreatePositionRequest request, ClubMembership opener) {
        Team team = validateFundTeam(teamId);

        // 비관적 락으로 동일 종목+시장의 OPEN 포지션 중복 체크 — 동시 생성 방지
        positionRepository.findByTeamIdAndTickerAndMarketAndStatusForUpdate(
                teamId, request.getTicker(), request.getMarket(), PositionStatus.OPEN
        ).ifPresent(existing -> {
            throw new BadRequestException("이미 진행 중인 포지션이 존재합니다: " + request.getTicker());
        });

        BigDecimal buyAmount = request.getBuyPrice().multiply(request.getQuantity());

        Position position = Position.builder()
                .team(team)
                .ticker(request.getTicker())
                .tickerName(request.getTickerName())
                .market(request.getMarket())
                .status(PositionStatus.OPEN)
                .averageBuyPrice(request.getBuyPrice())
                .totalQuantity(request.getQuantity())
                .totalBuyAmount(buyAmount)
                .buyPlan(request.getBuyPlan())
                .takeProfitTargets(request.getTakeProfitTargets())
                .stopLossTargets(request.getStopLossTargets())
                .infoConfirmed(true)
                .opener(opener)
                .build();

        Position saved = positionRepository.save(position);
        log.info("Position created: {} ({}) id={}, teamId={}", request.getTicker(), request.getMarket(), saved.getId(), teamId);
        return saved;
    }

    /**
     * TradeRequest 승인 후 포지션 생성 (isInfoConfirmed=false)
     * TradeRequest 구현 전이므로 메서드만 준비
     */
    @Transactional
    public Position createPositionFromRequest(
            Long teamId, String ticker, String tickerName, String market,
            BigDecimal buyPrice, BigDecimal quantity,
            List<Map<String, Object>> buyPlan,
            List<Map<String, Object>> takeProfitTargets,
            List<Map<String, Object>> stopLossTargets,
            ClubMembership opener) {

        Team team = validateFundTeam(teamId);

        // 비관적 락으로 중복 체크 — 동시 생성 방지
        positionRepository.findByTeamIdAndTickerAndMarketAndStatusForUpdate(
                teamId, ticker, market, PositionStatus.OPEN
        ).ifPresent(existing -> {
            throw new BadRequestException("이미 진행 중인 포지션이 존재합니다: " + ticker);
        });

        Position position = Position.builder()
                .team(team)
                .ticker(ticker)
                .tickerName(tickerName)
                .market(market)
                .status(PositionStatus.OPEN)
                .averageBuyPrice(buyPrice)
                .totalQuantity(quantity)
                .totalBuyAmount(buyPrice.multiply(quantity))
                .buyPlan(buyPlan)
                .takeProfitTargets(takeProfitTargets)
                .stopLossTargets(stopLossTargets)
                .infoConfirmed(false)
                .opener(opener)
                .build();

        Position saved = positionRepository.save(position);
        log.info("Position created from request: {} ({}) id={}", ticker, market, saved.getId());
        return saved;
    }

    // ========================
    // Update operations
    // ========================

    /**
     * 추가 매수 — 평균가 재계산
     */
    @Transactional
    public Position addToPosition(Long positionId, BigDecimal additionalPrice, BigDecimal additionalQuantity,
                                   List<Map<String, Object>> buyPlan,
                                   List<Map<String, Object>> takeProfitTargets,
                                   List<Map<String, Object>> stopLossTargets) {
        Position position = getPositionById(positionId);

        BigDecimal oldAmount = safe(position.getTotalBuyAmount());
        BigDecimal additionalAmount = additionalPrice.multiply(additionalQuantity);
        BigDecimal newTotalAmount = oldAmount.add(additionalAmount);
        BigDecimal newTotalQty = safe(position.getTotalQuantity()).add(additionalQuantity);

        position.setAverageBuyPrice(newTotalAmount.divide(newTotalQty, 4, RoundingMode.HALF_UP));
        position.setTotalQuantity(newTotalQty);
        position.setTotalBuyAmount(newTotalAmount);
        position.setInfoConfirmed(false);

        // 기존 계획에 병합
        mergePlanList(position, buyPlan, "buy");
        mergePlanList(position, takeProfitTargets, "tp");
        mergePlanList(position, stopLossTargets, "sl");

        Position saved = positionRepository.save(position);
        log.info("Position {} updated with additional buy: qty={}, price={}", positionId, additionalQuantity, additionalPrice);
        return saved;
    }

    /**
     * 포지션 종료 — P&L 계산, holdingPeriodHours 계산
     */
    @Transactional
    public Position closePosition(Long positionId, ClosePositionRequest request, ClubMembership closer) {
        Position position = getPositionById(positionId);

        if (position.getStatus() == PositionStatus.CLOSED) {
            throw new BadRequestException("이미 종료된 포지션입니다");
        }

        BigDecimal sellAmount = request.getSellPrice().multiply(request.getSellQuantity());

        position.setStatus(PositionStatus.CLOSED);
        position.setAverageSellPrice(request.getSellPrice());
        position.setTotalSellAmount(sellAmount);
        position.setClosedAt(LocalDateTime.now());
        position.setCloser(closer);

        // P&L 계산 — 잔여 수량에 대한 손익만 추가 (이전 reducePosition으로 이미 일부 누적됨)
        BigDecimal remainingQty = safe(position.getTotalQuantity());
        BigDecimal avgBuy = safe(position.getAverageBuyPrice());
        BigDecimal remainingCostBasis = avgBuy.multiply(remainingQty);
        BigDecimal closingPnl = sellAmount.subtract(remainingCostBasis);
        BigDecimal totalRealized = safe(position.getRealizedProfitLoss()).add(closingPnl);
        position.setRealizedProfitLoss(totalRealized);

        // 전체 수익률 = 총 실현손익 / 총 매수금액 (원래 전체 기준)
        BigDecimal originalBuyAmount = safe(position.getTotalBuyAmount());
        if (originalBuyAmount.compareTo(BigDecimal.ZERO) > 0) {
            position.setProfitRate(totalRealized.divide(originalBuyAmount, 4, RoundingMode.HALF_UP));
        }

        // 보유 기간
        if (position.getOpenedAt() != null) {
            Duration duration = Duration.between(position.getOpenedAt(), position.getClosedAt());
            position.setHoldingPeriodHours(duration.toHours());
        }

        Position saved = positionRepository.save(position);

        logAudit(saved, closer, "POSITION_CLOSE",
                Map.of("sellPrice", String.valueOf(request.getSellPrice()),
                       "sellQuantity", String.valueOf(request.getSellQuantity()),
                       "pnl", String.valueOf(totalRealized)));

        log.info("Position {} closed, P&L={}, rate={}", positionId, saved.getRealizedProfitLoss(), saved.getProfitRate());
        return saved;
    }

    /**
     * 부분 매도 — 수량 감소, 실현손익 누적
     */
    @Transactional
    public Position reducePosition(Long positionId, BigDecimal sellPrice, BigDecimal sellQuantity) {
        Position position = getPositionById(positionId);
        BigDecimal currentQty = safe(position.getTotalQuantity());

        if (sellQuantity.compareTo(currentQty) > 0) {
            throw new BadRequestException("매도 수량이 보유 수량을 초과합니다");
        }

        BigDecimal avgBuy = safe(position.getAverageBuyPrice());
        BigDecimal realizedPnl = sellPrice.subtract(avgBuy).multiply(sellQuantity);

        position.setTotalQuantity(currentQty.subtract(sellQuantity));
        position.setTotalBuyAmount(position.getTotalQuantity().multiply(avgBuy));
        position.setRealizedProfitLoss(safe(position.getRealizedProfitLoss()).add(realizedPnl));

        Position saved = positionRepository.save(position);
        log.info("Position {} reduced by {}, remaining={}", positionId, sellQuantity, saved.getTotalQuantity());
        return saved;
    }

    /**
     * 매매 계획 항목 완료 토글
     * - 매수 체크 → 수량/가격 증가
     * - 익절/손절 체크 → 수량 감소 + 실현손익 누적
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Position togglePlanItem(Long positionId, String planType, int index, ClubMembership actor) {
        Position position = getPositionById(positionId);

        if (position.getStatus() == PositionStatus.CLOSED) {
            throw new BadRequestException("종료된 포지션은 수정할 수 없습니다");
        }

        if ("BUY".equalsIgnoreCase(planType)) {
            List<Map<String, Object>> buyPlan = position.getBuyPlan();
            if (buyPlan == null || index >= buyPlan.size()) {
                throw new BadRequestException("유효하지 않은 인덱스입니다");
            }

            Map<String, Object> item = buyPlan.get(index);
            boolean oldCompleted = Boolean.TRUE.equals(item.get("completed"));

            if (oldCompleted) {
                throw new BadRequestException("체결된 매수는 취소할 수 없습니다. 매도 계획을 사용하세요.");
            }

            BigDecimal buyPrice = toBigDecimal(item.get("price"));
            BigDecimal buyQuantity = toBigDecimal(item.get("quantity"));

            if (buyPrice.compareTo(BigDecimal.ZERO) > 0 && buyQuantity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal oldQty = safe(position.getTotalQuantity());
                BigDecimal oldAmount = safe(position.getTotalBuyAmount());
                BigDecimal additionalAmount = buyPrice.multiply(buyQuantity);

                position.setTotalQuantity(oldQty.add(buyQuantity));
                position.setTotalBuyAmount(oldAmount.add(additionalAmount));

                if (position.getTotalQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    position.setAverageBuyPrice(
                            position.getTotalBuyAmount().divide(position.getTotalQuantity(), 4, RoundingMode.HALF_UP));
                }
            }

            item.put("completed", true);
            position.setBuyPlan(new ArrayList<>(buyPlan));

        } else if ("TAKE_PROFIT".equalsIgnoreCase(planType) || "STOP_LOSS".equalsIgnoreCase(planType)) {
            boolean isTakeProfit = "TAKE_PROFIT".equalsIgnoreCase(planType);
            String label = isTakeProfit ? "익절" : "손절";

            List<Map<String, Object>> targets = isTakeProfit
                    ? position.getTakeProfitTargets()
                    : position.getStopLossTargets();

            if (targets == null || index >= targets.size()) {
                throw new BadRequestException("유효하지 않은 인덱스입니다");
            }

            Map<String, Object> item = targets.get(index);
            boolean oldCompleted = Boolean.TRUE.equals(item.get("completed"));

            if (oldCompleted) {
                throw new BadRequestException("체결된 " + label + "은 취소할 수 없습니다");
            }

            BigDecimal sellPrice = toBigDecimal(item.get("price"));
            BigDecimal sellQuantity = toBigDecimal(item.get("quantity"));

            if (sellPrice.compareTo(BigDecimal.ZERO) > 0 && sellQuantity.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentQty = safe(position.getTotalQuantity());

                if (sellQuantity.compareTo(currentQty) > 0) {
                    throw new BadRequestException(
                            label + " 수량(" + sellQuantity + ")이 보유 수량(" + currentQty + ")보다 많습니다");
                }

                BigDecimal avgBuy = safe(position.getAverageBuyPrice());
                BigDecimal realizedPnl = sellPrice.subtract(avgBuy).multiply(sellQuantity);

                position.setTotalQuantity(currentQty.subtract(sellQuantity));
                position.setTotalBuyAmount(position.getTotalQuantity().multiply(avgBuy));
                position.setRealizedProfitLoss(safe(position.getRealizedProfitLoss()).add(realizedPnl));
            }

            item.put("completed", true);
            if (isTakeProfit) {
                position.setTakeProfitTargets(new ArrayList<>(targets));
            } else {
                position.setStopLossTargets(new ArrayList<>(targets));
            }
        } else {
            throw new BadRequestException("유효하지 않은 planType입니다: " + planType);
        }

        logAudit(position, actor, "PLAN_TOGGLE",
                Map.of("planType", planType, "index", index, "completed", true));

        return positionRepository.save(position);
    }

    /**
     * 매매계획 업데이트 (buyPlan, takeProfitTargets, stopLossTargets)
     * 기존 계획을 스냅샷으로 저장한 뒤 덮어씀
     */
    @Transactional
    public Position updatePlans(Long positionId, UpdatePositionRequest request, ClubMembership actor) {
        Position position = getPositionById(positionId);

        // 기존 계획 스냅샷 저장 (덮어쓰기 전)
        savePlanSnapshot(position, actor);

        if (request.getBuyPlan() != null) {
            position.setBuyPlan(request.getBuyPlan());
        }
        if (request.getTakeProfitTargets() != null) {
            position.setTakeProfitTargets(request.getTakeProfitTargets());
        }
        if (request.getStopLossTargets() != null) {
            position.setStopLossTargets(request.getStopLossTargets());
        }

        logAudit(position, actor, "PLAN_SAVE", Map.of("memo", "계획 저장"));

        return positionRepository.save(position);
    }

    /**
     * 포지션 핵심 필드 인라인 수정 (averageBuyPrice, totalQuantity, tickerName)
     * null이 아닌 필드만 업데이트. 가격/수량 변경 시 totalBuyAmount 재계산.
     */
    @Transactional
    public Position updatePositionFields(Long positionId, UpdatePositionFieldsRequest request,
                                          ClubMembership actor) {
        Position position = getPositionById(positionId);

        if (position.getStatus() != PositionStatus.OPEN) {
            throw new BadRequestException("OPEN 상태의 포지션만 수정할 수 있습니다");
        }

        boolean recalculate = false;

        if (request.getAverageBuyPrice() != null) {
            BigDecimal oldPrice = position.getAverageBuyPrice();
            position.setAverageBuyPrice(request.getAverageBuyPrice());
            recalculate = true;
            logAudit(position, actor, "FIELD_UPDATE",
                    Map.of("field", "averageBuyPrice", "before", String.valueOf(oldPrice), "after", String.valueOf(request.getAverageBuyPrice())));
        }
        if (request.getTotalQuantity() != null) {
            BigDecimal oldQty = position.getTotalQuantity();
            position.setTotalQuantity(request.getTotalQuantity());
            recalculate = true;
            logAudit(position, actor, "FIELD_UPDATE",
                    Map.of("field", "totalQuantity", "before", String.valueOf(oldQty), "after", String.valueOf(request.getTotalQuantity())));
        }
        if (request.getTickerName() != null) {
            String oldName = position.getTickerName();
            position.setTickerName(request.getTickerName());
            logAudit(position, actor, "FIELD_UPDATE",
                    Map.of("field", "tickerName", "before", String.valueOf(oldName), "after", request.getTickerName()));
        }

        if (recalculate) {
            BigDecimal price = safe(position.getAverageBuyPrice());
            BigDecimal qty = safe(position.getTotalQuantity());
            position.setTotalBuyAmount(price.multiply(qty));
        }

        Position saved = positionRepository.save(position);
        log.info("Position {} fields updated inline", positionId);
        return saved;
    }

    /**
     * 매니저 체결 확인
     */
    @Transactional
    public Position confirmPositionInfo(Long positionId) {
        Position position = getPositionById(positionId);
        position.setInfoConfirmed(true);
        return positionRepository.save(position);
    }

    /**
     * 종목 공개/비공개 토글
     */
    @Transactional
    public Position togglePublish(Long positionId) {
        Position position = getPositionById(positionId);
        boolean newValue = !position.isPublished();
        position.setPublished(newValue);
        if (newValue && position.getPublishedAt() == null) {
            position.setPublishedAt(java.time.LocalDateTime.now());
        }
        return positionRepository.save(position);
    }

    /**
     * 포지션 삭제 — 관련 TradeRequest/DecisionNote FK 해제 후 삭제
     */
    @Transactional
    public void deletePosition(Long positionId) {
        Position position = getPositionById(positionId);

        // 관련 TradeRequest의 position FK를 null로 해제
        List<TradeRequest> relatedRequests = tradeRequestRepository.findByPositionId(positionId);
        for (TradeRequest req : relatedRequests) {
            req.setPosition(null);
        }
        tradeRequestRepository.saveAll(relatedRequests);

        // 관련 DecisionNote 삭제
        decisionNoteRepository.deleteAll(
                decisionNoteRepository.findByPositionIdOrderByCreatedAtDesc(positionId));

        positionRepository.delete(position);
        log.info("Position {} deleted (unlinked {} trade requests)", positionId, relatedRequests.size());
    }

    // ========================
    // Status info computation
    // ========================

    public PositionStatusInfo getPositionStatusInfo(Position position) {
        if (position.getStatus() == PositionStatus.CLOSED) {
            return PositionStatusInfo.builder().status("closed").build();
        }

        BigDecimal currentQty = safe(position.getTotalQuantity());

        int pendingTp = countPendingItems(position.getTakeProfitTargets());
        int pendingSl = countPendingItems(position.getStopLossTargets());
        int completedTp = countCompletedItems(position.getTakeProfitTargets());
        int completedSl = countCompletedItems(position.getStopLossTargets());

        // 잔량 0이지만 아직 OPEN → 종료 필요
        if (currentQty.compareTo(BigDecimal.ZERO) <= 0) {
            return PositionStatusInfo.builder()
                    .status("needs_close").alert("danger")
                    .message("잔량 0 - 포지션 종료 필요").build();
        }

        // 매도 계획 모두 소진 → 새 계획 필요
        if (pendingTp == 0 && pendingSl == 0 && (completedTp > 0 || completedSl > 0)) {
            return PositionStatusInfo.builder()
                    .status("no_plan").alert("warning")
                    .message("매도 계획 없음").build();
        }

        // 매도 계획 자체가 없음
        int totalValidTp = countValidItems(position.getTakeProfitTargets());
        int totalValidSl = countValidItems(position.getStopLossTargets());
        if (totalValidTp == 0 && totalValidSl == 0) {
            return PositionStatusInfo.builder()
                    .status("no_plan").alert("warning")
                    .message("매도 계획 없음").build();
        }

        return PositionStatusInfo.builder().status("normal").build();
    }

    public int countRemainingItems(List<Map<String, Object>> items) {
        return countPendingItems(items);
    }

    /**
     * 포지션 → 응답 DTO 변환 (컨트롤러에서 사용)
     */
    @Transactional(readOnly = true)
    public PositionResponse toResponse(Position position) {
        int remainingBuys = countRemainingItems(position.getBuyPlan());
        int remainingTp = countRemainingItems(position.getTakeProfitTargets());
        int remainingSl = countRemainingItems(position.getStopLossTargets());
        PositionStatusInfo statusInfo = getPositionStatusInfo(position);
        return PositionResponse.from(position, remainingBuys, remainingTp, remainingSl, statusInfo);
    }

    // ========================
    // Validation
    // ========================

    public Team validateFundTeam(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("팀", teamId));
        if (team.getTeamType() != TeamType.FUND) {
            throw new BadRequestException("FUND 타입 팀에서만 포지션을 관리할 수 있습니다");
        }
        if (team.isDeleted()) {
            throw new BadRequestException("삭제된 팀입니다");
        }
        return team;
    }

    // ========================
    // Private helpers
    // ========================

    private void mergePlanList(Position position, List<Map<String, Object>> newItems, String type) {
        if (newItems == null || newItems.isEmpty()) return;

        switch (type) {
            case "buy" -> {
                List<Map<String, Object>> existing = position.getBuyPlan() != null
                        ? new ArrayList<>(position.getBuyPlan()) : new ArrayList<>();
                existing.addAll(newItems);
                position.setBuyPlan(existing);
            }
            case "tp" -> {
                List<Map<String, Object>> existing = position.getTakeProfitTargets() != null
                        ? new ArrayList<>(position.getTakeProfitTargets()) : new ArrayList<>();
                existing.addAll(newItems);
                position.setTakeProfitTargets(existing);
            }
            case "sl" -> {
                List<Map<String, Object>> existing = position.getStopLossTargets() != null
                        ? new ArrayList<>(position.getStopLossTargets()) : new ArrayList<>();
                existing.addAll(newItems);
                position.setStopLossTargets(existing);
            }
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (value instanceof String s) {
            try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
        }
        return BigDecimal.ZERO;
    }

    private boolean hasValue(Object value) {
        if (value == null) return false;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) {
            try { return Double.parseDouble(s) != 0.0; } catch (NumberFormatException e) { return false; }
        }
        return false;
    }

    private int countValidItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return 0;
        return (int) items.stream()
                .filter(i -> hasValue(i.get("price")) && hasValue(i.get("quantity")))
                .count();
    }

    private int countCompletedItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return 0;
        return (int) items.stream()
                .filter(i -> hasValue(i.get("price")) && hasValue(i.get("quantity"))
                        && Boolean.TRUE.equals(i.get("completed")))
                .count();
    }

    private int countPendingItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return 0;
        return (int) items.stream()
                .filter(i -> hasValue(i.get("price")) && hasValue(i.get("quantity"))
                        && !Boolean.TRUE.equals(i.get("completed")))
                .count();
    }

    // ========================
    // 공개용 조회 (게시판 펀드팀 뷰)
    // ========================

    @Transactional(readOnly = true)
    public List<com.fundmessenger.fund.dto.DecisionNoteResponse> getExportedNotesByPosition(Long positionId) {
        return decisionNoteRepository.findByPositionIdAndExportEnabledTrueOrderByCreatedAtDesc(positionId)
                .stream()
                .map(com.fundmessenger.fund.dto.DecisionNoteResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.fundmessenger.fund.dto.TradeRequestResponse> getExecutedRequestsByPosition(Long positionId) {
        return tradeRequestRepository.findByPositionIdAndStatusOrderByCreatedAtAsc(
                        positionId, com.fundmessenger.fund.entity.RequestStatus.EXECUTED)
                .stream()
                .map(com.fundmessenger.fund.dto.TradeRequestResponse::from)
                .toList();
    }

    // ========================
    // Team member requests
    // ========================

    /**
     * 토론 요청 — 팀원이 매니저/팀장에게 토론을 요청
     */
    @Transactional
    public void requestDiscussion(Long positionId, ClubMembership requester) {
        Position position = getPositionById(positionId);
        logAudit(position, requester, "DISCUSSION_REQUEST", Map.of());
    }

    /**
     * 조기종료 요청 — 팀원이 매니저/팀장에게 조기종료를 요청
     */
    @Transactional
    public void requestEarlyClose(Long positionId, ClubMembership requester) {
        Position position = getPositionById(positionId);
        logAudit(position, requester, "EARLY_CLOSE_REQUEST", Map.of());
    }

    // ========================
    // Audit & Snapshot helpers
    // ========================

    private void logAudit(Position position, ClubMembership actor, String action, Map<String, Object> detail) {
        String detailJson = null;
        try {
            detailJson = OBJECT_MAPPER.writeValueAsString(detail);
        } catch (Exception e) {
            log.warn("Audit log detail serialization failed: {}", e.getMessage());
            detailJson = detail.toString();
        }

        FundAuditLog auditLog = FundAuditLog.builder()
                .position(position)
                .actorId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? actor.getName() : null)
                .action(action)
                .detail(detailJson)
                .build();
        auditLogRepository.save(auditLog);
    }

    private void savePlanSnapshot(Position position, ClubMembership savedBy) {
        int nextVersion = planSnapshotRepository.countByPositionId(position.getId()) + 1;
        PlanSnapshot snapshot = PlanSnapshot.builder()
                .position(position)
                .savedById(savedBy != null ? savedBy.getId() : null)
                .savedByName(savedBy != null ? savedBy.getName() : null)
                .buyPlan(position.getBuyPlan())
                .takeProfitTargets(position.getTakeProfitTargets())
                .stopLossTargets(position.getStopLossTargets())
                .version(nextVersion)
                .build();
        planSnapshotRepository.save(snapshot);
    }
}
