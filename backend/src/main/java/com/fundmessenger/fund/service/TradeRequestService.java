package com.fundmessenger.fund.service;

import com.fundmessenger.chat.entity.ChatRoom;
import com.fundmessenger.chat.service.ChatMessageService;
import com.fundmessenger.chat.service.ChatRoomService;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.MemberTag;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.entity.TeamType;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.fund.dto.*;
import com.fundmessenger.fund.entity.*;
import com.fundmessenger.fund.repository.PositionRepository;
import com.fundmessenger.fund.repository.TradeRequestRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.config.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeRequestService {

    private final TradeRequestRepository tradeRequestRepository;
    private final PositionService positionService;
    private final PositionRepository positionRepository;
    private final TeamRepository teamRepository;
    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final MemberTagRepository memberTagRepository;

    // ========================
    // Read operations
    // ========================

    @Transactional(readOnly = true)
    public TradeRequest getRequestById(Long requestId) {
        return tradeRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("매매요청", requestId));
    }

    @Transactional(readOnly = true)
    public List<TradeRequestResponse> getRequests(Long teamId, RequestStatus status) {
        List<TradeRequest> requests;
        if (status != null) {
            requests = tradeRequestRepository.findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, status);
        } else {
            requests = tradeRequestRepository.findByTeamIdOrderByCreatedAtDesc(teamId);
        }
        return requests.stream().map(TradeRequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TradeRequestResponse> getMyRequests(Long teamId, Long requesterId) {
        List<TradeRequest> requests = tradeRequestRepository
                .findByTeamIdAndRequesterIdOrderByCreatedAtDesc(teamId, requesterId);
        return requests.stream().map(TradeRequestResponse::from).toList();
    }

    // ========================
    // Create operations
    // ========================

    @Transactional
    public TradeRequest createBuyRequest(Long teamId, CreateBuyRequest dto, ClubMembership requester) {
        Team team = validateFundTeam(teamId);

        TradeRequest request = TradeRequest.builder()
                .team(team)
                .requester(requester)
                .requestType(RequestType.BUY)
                .status(RequestStatus.PENDING)
                .targetTicker(dto.getTargetTicker())
                .targetMarket(dto.getTargetMarket())
                .targetTickerName(dto.getTargetTickerName())
                .orderQuantity(dto.getOrderQuantity())
                .buyPrice(dto.getBuyPrice())
                .buyOrders(dto.getBuyOrders())
                .targetRatio(dto.getTargetRatio())
                .takeProfitTargets(dto.getTakeProfitTargets())
                .stopLossTargets(dto.getStopLossTargets())
                .memo(dto.getMemo())
                .build();

        TradeRequest saved = tradeRequestRepository.save(request);
        log.info("매수 요청 생성: id={}, ticker={}, requester={}", saved.getId(), dto.getTargetTicker(), requester.getName());
        return saved;
    }

    @Transactional
    public TradeRequest createSellRequest(Long teamId, CreateSellRequest dto, ClubMembership requester) {
        Team team = validateFundTeam(teamId);

        Position position = positionService.getPositionById(dto.getPositionId());
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new BadRequestException("OPEN 상태의 포지션에서만 매도 요청이 가능합니다");
        }
        if (!position.getTeam().getId().equals(teamId)) {
            throw new ForbiddenException("해당 팀의 포지션이 아닙니다");
        }

        // 매도 수량 검증 — 생성 시점에 즉시 확인
        if (dto.getSellQuantity() == null || dto.getSellQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("매도 수량은 0보다 커야 합니다");
        }
        if (position.getTotalQuantity() != null
                && dto.getSellQuantity().compareTo(position.getTotalQuantity()) > 0) {
            throw new BadRequestException("매도 수량(" + dto.getSellQuantity() + ")이 보유 수량("
                    + position.getTotalQuantity() + ")을 초과합니다");
        }

        TradeRequest request = TradeRequest.builder()
                .team(team)
                .requester(requester)
                .requestType(RequestType.SELL)
                .status(RequestStatus.PENDING)
                .position(position)
                .targetTicker(position.getTicker())
                .targetMarket(position.getMarket())
                .targetTickerName(position.getTickerName())
                .sellQuantity(dto.getSellQuantity())
                .sellPrice(dto.getSellPrice())
                .sellReason(dto.getSellReason())
                .memo(dto.getMemo())
                .build();

        TradeRequest saved = tradeRequestRepository.save(request);
        log.info("매도 요청 생성: id={}, ticker={}, qty={}, requester={}",
                saved.getId(), position.getTicker(), dto.getSellQuantity(), requester.getName());
        return saved;
    }

    // ========================
    // Approval / Rejection / Discussion
    // ========================

    @Transactional
    public TradeRequest approveRequest(Long requestId, ApproveRequest approveData, ClubMembership approver) {
        TradeRequest request = getRequestById(requestId);

        if (request.getStatus() != RequestStatus.PENDING && request.getStatus() != RequestStatus.DISCUSSION) {
            throw new BadRequestException("PENDING 또는 DISCUSSION 상태에서만 승인 가능합니다 (현재: " + request.getStatus() + ")");
        }

        request.setStatus(RequestStatus.APPROVED);
        request.setApprover(approver);
        request.setApprovedAt(LocalDateTime.now());
        request.setExecutedAt(LocalDateTime.now());

        if (request.getRequestType() == RequestType.BUY) {
            // 매수: null이면 요청값 사용
            BigDecimal executedPrice = approveData.getExecutedPrice() != null
                    ? approveData.getExecutedPrice() : request.getBuyPrice();
            BigDecimal executedQuantity = approveData.getExecutedQuantity() != null
                    ? approveData.getExecutedQuantity() : request.getOrderQuantity();
            request.setExecutedPrice(executedPrice);
            request.setExecutedQuantity(executedQuantity);
            handleBuyApproval(request, executedPrice, executedQuantity, approver);
        } else if (request.getRequestType() == RequestType.SELL) {
            // 매도: null이면 요청값 사용
            BigDecimal executedPrice = approveData.getExecutedPrice() != null
                    ? approveData.getExecutedPrice() : request.getSellPrice();
            BigDecimal executedQuantity = approveData.getExecutedQuantity() != null
                    ? approveData.getExecutedQuantity() : request.getSellQuantity();
            request.setExecutedPrice(executedPrice);
            request.setExecutedQuantity(executedQuantity);
            handleSellApproval(request, approveData, approver);
        }

        TradeRequest saved = tradeRequestRepository.save(request);
        log.info("매매요청 승인: id={}, type={}, approver={}", requestId, request.getRequestType(), approver.getName());
        return saved;
    }

    @Transactional
    public TradeRequest rejectRequest(Long requestId, String rejectionReason, ClubMembership rejector) {
        TradeRequest request = getRequestById(requestId);

        if (request.getStatus() != RequestStatus.PENDING && request.getStatus() != RequestStatus.DISCUSSION) {
            throw new BadRequestException("PENDING 또는 DISCUSSION 상태에서만 거절 가능합니다 (현재: " + request.getStatus() + ")");
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setApprover(rejector);
        request.setApprovedAt(LocalDateTime.now());
        request.setRejectionReason(rejectionReason);

        TradeRequest saved = tradeRequestRepository.save(request);
        log.info("매매요청 거절: id={}, reason={}, rejector={}", requestId, rejectionReason, rejector.getName());
        return saved;
    }

    @Transactional
    public TradeRequest startDiscussion(Long requestId) {
        TradeRequest request = getRequestById(requestId);

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("PENDING 상태에서만 토론 전환 가능합니다 (현재: " + request.getStatus() + ")");
        }

        request.setStatus(RequestStatus.DISCUSSION);
        TradeRequest saved = tradeRequestRepository.save(request);

        // ── 토론방 자동 생성 ──
        Team team = saved.getTeam();
        Position position = saved.getPosition(); // 매도면 position 있음, 매수면 null

        // 팀 멤버 조회 (MemberTag 기반)
        List<MemberTag> teamTags = memberTagRepository.findByTeamId(team.getId());
        List<ClubMembership> teamMembers = teamTags.stream()
                .map(MemberTag::getClubMembership)
                .distinct()
                .toList();

        ChatRoom discussionRoom = chatRoomService.createDiscussionRoom(
                team.getClub(), team, saved, position, teamMembers);

        // 시스템 메시지
        String requestTypeKo = saved.getRequestType() == RequestType.BUY ? "매수" : "매도";
        String systemMsg = String.format(
                "[토론 시작] %s님의 %s 요청에 대한 토론이 시작되었습니다.\n\n사유: %s",
                saved.getRequester().getName(), requestTypeKo, saved.getMemo());
        chatMessageService.sendSystemMessage(discussionRoom, saved.getRequester(), systemMsg);

        log.info("매매요청 토론 시작: id={}, discussionRoomId={}", requestId, discussionRoom.getId());
        return saved;
    }

    @Transactional
    public void deleteRequest(Long requestId) {
        TradeRequest request = getRequestById(requestId);
        tradeRequestRepository.delete(request);
        log.info("매매요청 삭제: id={}, ticker={}", requestId, request.getTargetTicker());
    }

    // ========================
    // Private helpers
    // ========================

    @SuppressWarnings("unchecked")
    private void handleBuyApproval(TradeRequest request, BigDecimal executedPrice, BigDecimal executedQuantity,
                                   ClubMembership approver) {
        if (executedPrice == null || executedQuantity == null) {
            throw new BadRequestException("매수 승인 시 체결가격과 수량이 필요합니다");
        }

        Long teamId = request.getTeam().getId();

        // completed=false 플래그 추가
        List<Map<String, Object>> tpTargets = addCompletedFlag(request.getTakeProfitTargets());
        List<Map<String, Object>> slTargets = addCompletedFlag(request.getStopLossTargets());

        // buyPlan 생성
        List<Map<String, Object>> buyPlan = createBuyPlan(request, executedPrice, executedQuantity);

        // 기존 OPEN 포지션 확인 (비관적 락 — 동시 매수 승인 시 포지션 중복 방지)
        Optional<Position> existingOpt = positionRepository.findByTeamIdAndTickerAndMarketAndStatusForUpdate(
                teamId, request.getTargetTicker(), request.getTargetMarket(), PositionStatus.OPEN);

        Position position;
        if (existingOpt.isPresent()) {
            // 기존 포지션에 추가매수
            position = positionService.addToPosition(
                    existingOpt.get().getId(),
                    executedPrice, executedQuantity,
                    buyPlan, tpTargets, slTargets);
        } else {
            // 신규 포지션 생성
            position = positionService.createPositionFromRequest(
                    teamId,
                    request.getTargetTicker(),
                    request.getTargetTickerName(),
                    request.getTargetMarket(),
                    executedPrice, executedQuantity,
                    buyPlan, tpTargets, slTargets,
                    approver);
        }

        request.setPosition(position);
    }

    private void handleSellApproval(TradeRequest request, ApproveRequest approveData, ClubMembership approver) {
        Position linkedPosition = request.getPosition();
        if (linkedPosition == null) {
            throw new BadRequestException("매도 요청에 연결된 포지션이 없습니다");
        }

        // 비관적 락으로 포지션 재조회 — 동시 매도 승인 시 보유 초과 방지
        Long positionId = linkedPosition.getId();
        Position position = positionRepository.findByIdForUpdate(positionId)
                .orElseThrow(() -> new NotFoundException("포지션", positionId));

        BigDecimal sellPrice = approveData.getExecutedPrice() != null
                ? approveData.getExecutedPrice() : request.getSellPrice();
        BigDecimal sellQuantity = approveData.getExecutedQuantity() != null
                ? approveData.getExecutedQuantity() : request.getSellQuantity();

        if (sellQuantity == null) {
            throw new BadRequestException("매도 수량이 필요합니다");
        }

        // 체결가/수량 기록
        request.setExecutedPrice(sellPrice);
        request.setExecutedQuantity(sellQuantity);

        BigDecimal currentQty = position.getTotalQuantity() != null
                ? position.getTotalQuantity() : BigDecimal.ZERO;
        BigDecimal remaining = currentQty.subtract(sellQuantity);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            // 전량 매도 → 포지션 종료
            ClosePositionRequest closeReq = new ClosePositionRequest();
            closeReq.setSellPrice(sellPrice != null ? sellPrice : BigDecimal.ZERO);
            closeReq.setSellQuantity(sellQuantity);
            positionService.closePosition(position.getId(), closeReq, approver);
        } else {
            // 부분 매도
            positionService.reducePosition(position.getId(),
                    sellPrice != null ? sellPrice : BigDecimal.ZERO, sellQuantity);
        }
    }

    /**
     * JSONB 타겟 리스트에 completed=false 추가
     */
    private List<Map<String, Object>> addCompletedFlag(List<Map<String, Object>> targets) {
        if (targets == null || targets.isEmpty()) return null;

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> t : targets) {
            Map<String, Object> copy = new LinkedHashMap<>(t);
            copy.put("completed", false);
            result.add(copy);
        }
        return result;
    }

    /**
     * 매수 요청에서 buyPlan 생성.
     * buyOrders(분할매수)가 있으면 사용, 없으면 단일 buyPrice/orderQuantity로 생성.
     * 첫 번째 항목은 completed=true (승인 = 체결).
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> createBuyPlan(TradeRequest request,
                                                     BigDecimal executedPrice, BigDecimal executedQuantity) {
        List<Map<String, Object>> buyPlan = new ArrayList<>();

        if (request.getBuyOrders() != null && !request.getBuyOrders().isEmpty()) {
            List<Map<String, Object>> orders = request.getBuyOrders();
            for (int i = 0; i < orders.size(); i++) {
                Map<String, Object> order = orders.get(i);
                Map<String, Object> entry = new LinkedHashMap<>();
                // String 경유 변환으로 BigDecimal 정밀도 보존
                entry.put("price", toStringValue(order.get("price")));
                entry.put("quantity", toStringValue(order.get("quantity")));
                entry.put("ratio", toStringValue(order.get("ratio")));
                entry.put("completed", i == 0); // 첫 번째만 체결 완료
                buyPlan.add(entry);
            }
        } else if (executedPrice != null && executedQuantity != null) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("price", executedPrice.setScale(4, RoundingMode.HALF_UP).toPlainString());
            entry.put("quantity", executedQuantity.setScale(8, RoundingMode.HALF_UP).toPlainString());
            entry.put("completed", true);
            buyPlan.add(entry);
        }

        return buyPlan.isEmpty() ? null : buyPlan;
    }

    /**
     * JSONB 값을 String으로 변환 — BigDecimal 정밀도 손실 방지 (double 경유 금지)
     */
    private String toStringValue(Object value) {
        if (value == null) return "0";
        if (value instanceof BigDecimal bd) return bd.toPlainString();
        return value.toString();
    }

    private Team validateFundTeam(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("팀", teamId));
        if (team.getTeamType() != TeamType.FUND) {
            throw new BadRequestException("FUND 타입 팀에서만 매매 요청이 가능합니다");
        }
        if (team.isDeleted()) {
            throw new BadRequestException("삭제된 팀입니다");
        }
        return team;
    }
}
