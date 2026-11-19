package com.fundmessenger.fund.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.config.CerebrasClient;
import com.fundmessenger.fund.dto.GenerateReportRequest;
import com.fundmessenger.fund.dto.GenerateReportResponse;
import com.fundmessenger.fund.entity.*;
import com.fundmessenger.fund.repository.AssetSnapshotRepository;
import com.fundmessenger.fund.repository.CashAdjustmentRepository;
import com.fundmessenger.fund.repository.DecisionNoteRepository;
import com.fundmessenger.fund.repository.PositionRepository;
import com.fundmessenger.fund.repository.TradeRequestRepository;
import com.fundmessenger.jongtobang.entity.CanonicalNews;
import com.fundmessenger.jongtobang.repository.CanonicalNewsRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.ExternalServiceException;
import com.fundmessenger.config.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    private final CerebrasClient cerebrasClient;
    private final PositionRepository positionRepository;
    private final TradeRequestRepository tradeRequestRepository;
    private final AssetSnapshotRepository assetSnapshotRepository;
    private final CashAdjustmentRepository cashAdjustmentRepository;
    private final CanonicalNewsRepository canonicalNewsRepository;
    private final DecisionNoteRepository noteRepository;
    private final TeamRepository teamRepository;
    private final PositionService positionService;

    private static final String SYSTEM_PROMPT = """
            당신은 대학 투자동아리의 펀드 매니저입니다.
            아래 데이터를 바탕으로 종합 운용보고서를 작성하세요.

            ## 원칙
            - "~이다", "~한다" 체.
            - 한국어로 작성.
            - 마크다운 형식으로 작성. 첫 줄 ## 제목. H3으로 섹션 구분.
            - 수치는 제공된 데이터를 그대로 사용. 추측/계산 금지.
            - 데이터가 비어있는 항목은 "해당 기간 내 데이터 없음"으로 표기.
            - 투자 조언 금지. 객관적 사실 기반 서술.

            ## 출력 형식
            JSON으로 응답하세요:
            {"title": "보고서 제목 (20자 이내)", "content": "마크다운 본문"}
            """;

    @Transactional
    public GenerateReportResponse generateOperationReport(
            Long clubId, Long teamId,
            GenerateReportRequest request,
            ClubMembership membership) {

        if (!cerebrasClient.isAvailable()) {
            throw new ExternalServiceException("AI", "서비스가 설정되지 않았습니다");
        }

        Team team = positionService.validateFundTeam(teamId);
        Club club = team.getClub();


        int periodDays = request.getPeriodDays() != null ? request.getPeriodDays() : 7;
        Set<String> items = Set.copyOf(request.getSelectedItems());

        // 데이터 수집
        StringBuilder dataSection = new StringBuilder();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(periodDays);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        dataSection.append("기간: ").append(startDate.format(dateFmt))
                .append(" ~ ").append(endDate.format(dateFmt)).append("\n\n");

        if (items.contains("ASSET_TREND")) {
            dataSection.append("### 자산 변동 추이\n");
            List<AssetSnapshot> snapshots = assetSnapshotRepository
                    .findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(teamId, startDate, endDate);
            if (snapshots.isEmpty()) {
                dataSection.append("데이터 없음\n\n");
            } else {
                for (AssetSnapshot s : snapshots) {
                    dataSection.append(String.format("- %s: KRW현금=%s, KRW평가=%s, USD현금=%s, USD평가=%s, 총자산KRW=%s, 환율=%s, 실현손익=%s, 미실현손익=%s\n",
                            s.getSnapshotDate().format(dateFmt),
                            s.getKrwCash(), s.getKrwEvaluation(),
                            s.getUsdCash(), s.getUsdEvaluation(),
                            s.getTotalKrw(), s.getExchangeRate(),
                            s.getRealizedPnl(), s.getUnrealizedPnl()));
                }
                dataSection.append("\n");
            }
        }

        if (items.contains("TRADE_HISTORY")) {
            dataSection.append("### 매매 기록 (승인/실행 완료)\n");
            List<TradeRequest> trades = tradeRequestRepository
                    .findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, RequestStatus.EXECUTED);
            List<TradeRequest> periodTrades = trades.stream()
                    .filter(t -> t.getCreatedAt().isAfter(startDateTime))
                    .toList();
            if (periodTrades.isEmpty()) {
                dataSection.append("데이터 없음\n\n");
            } else {
                for (TradeRequest t : periodTrades) {
                    dataSection.append(String.format("- [%s] %s %s(%s) 수량=%s, 체결가=%s, 일시=%s\n",
                            t.getRequestType(),
                            t.getTargetTickerName() != null ? t.getTargetTickerName() : "",
                            t.getTargetTicker() != null ? t.getTargetTicker() : "",
                            t.getTargetMarket() != null ? t.getTargetMarket() : "",
                            t.getExecutedQuantity() != null ? t.getExecutedQuantity() : t.getOrderQuantity(),
                            t.getExecutedPrice() != null ? t.getExecutedPrice() : "-",
                            t.getExecutedAt() != null ? t.getExecutedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "-"));
                }
                dataSection.append("\n");
            }
        }

        if (items.contains("NEW_CLOSED_POSITIONS")) {
            dataSection.append("### 신규/종료 포지션\n");
            List<Position> allPositions = positionRepository.findByTeamIdOrderByCreatedAtDesc(teamId);

            List<Position> newPositions = allPositions.stream()
                    .filter(p -> p.getOpenedAt() != null && p.getOpenedAt().isAfter(startDateTime))
                    .toList();
            List<Position> closedPositions = allPositions.stream()
                    .filter(p -> p.getStatus() == PositionStatus.CLOSED
                            && p.getClosedAt() != null && p.getClosedAt().isAfter(startDateTime))
                    .toList();

            if (newPositions.isEmpty() && closedPositions.isEmpty()) {
                dataSection.append("데이터 없음\n\n");
            } else {
                if (!newPositions.isEmpty()) {
                    dataSection.append("신규:\n");
                    for (Position p : newPositions) {
                        dataSection.append(String.format("- %s(%s/%s) 평균매수가=%s, 수량=%s, 개시일=%s\n",
                                p.getTickerName(), p.getTicker(), p.getMarket(),
                                p.getAverageBuyPrice(), p.getTotalQuantity(),
                                p.getOpenedAt().toLocalDate().format(dateFmt)));
                    }
                }
                if (!closedPositions.isEmpty()) {
                    dataSection.append("종료:\n");
                    for (Position p : closedPositions) {
                        dataSection.append(String.format("- %s(%s/%s) 실현손익=%s, 수익률=%s%%, 종료일=%s\n",
                                p.getTickerName(), p.getTicker(), p.getMarket(),
                                p.getRealizedProfitLoss(),
                                p.getProfitRate() != null ? p.getProfitRate() : "-",
                                p.getClosedAt().toLocalDate().format(dateFmt)));
                    }
                }
                dataSection.append("\n");
            }
        }

        if (items.contains("PORTFOLIO_WEIGHT")) {
            dataSection.append("### 포트폴리오 비중\n");
            List<Position> openPositions = positionRepository
                    .findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, PositionStatus.OPEN);
            if (openPositions.isEmpty()) {
                dataSection.append("보유 포지션 없음\n\n");
            } else {
                BigDecimal totalBuy = openPositions.stream()
                        .map(Position::getTotalBuyAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                for (Position p : openPositions) {
                    BigDecimal weight = totalBuy.compareTo(BigDecimal.ZERO) > 0
                            ? p.getTotalBuyAmount().multiply(BigDecimal.valueOf(100))
                            .divide(totalBuy, 1, java.math.RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    dataSection.append(String.format("- %s(%s): 매수금액=%s, 비중=%.1f%%\n",
                            p.getTickerName(), p.getTicker(), p.getTotalBuyAmount(), weight));
                }
                dataSection.append("\n");
            }
        }

        if (items.contains("NEWS_SUMMARY")) {
            dataSection.append("### 뉴스 요약\n");
            List<CanonicalNews> news = canonicalNewsRepository
                    .findRecentForMatching(startDateTime, PageRequest.of(0, 20));
            if (news.isEmpty()) {
                dataSection.append("데이터 없음\n\n");
            } else {
                for (CanonicalNews n : news) {
                    dataSection.append(String.format("- %s\n", n.getTitle()));
                }
                dataSection.append("\n");
            }
        }

        if (items.contains("EXCHANGE_RATE")) {
            dataSection.append("### 환율 변동\n");
            List<AssetSnapshot> snapshots = assetSnapshotRepository
                    .findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(teamId, startDate, endDate);
            List<AssetSnapshot> withRate = snapshots.stream()
                    .filter(s -> s.getExchangeRate() != null && s.getExchangeRate().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            if (withRate.isEmpty()) {
                dataSection.append("데이터 없음\n\n");
            } else {
                for (AssetSnapshot s : withRate) {
                    dataSection.append(String.format("- %s: USD/KRW=%s\n",
                            s.getSnapshotDate().format(dateFmt), s.getExchangeRate()));
                }
                dataSection.append("\n");
            }
        }

        // AI 호출
        String userPrompt = dataSection.toString();
        log.info("운용보고서 AI 호출: teamId={}, items={}, 데이터길이={}자", teamId, items, userPrompt.length());

        JsonNode result = cerebrasClient.generateJsonUser(SYSTEM_PROMPT, userPrompt);
        if (result == null) {
            throw new ExternalServiceException("AI", "보고서 생성 실패 — 응답 없음");
        }

        String title = result.has("title") ? result.get("title").asText() : "운용보고서";
        String content = result.has("content") ? result.get("content").asText() : result.toString();

        // DecisionNote 생성 (noteType=REPORT)
        DecisionNote note = DecisionNote.builder()
                .team(team)
                .title(title)
                .content(content)
                .noteType(NoteType.REPORT)
                .author(membership)
                .build();
        DecisionNote saved = noteRepository.save(note);

        log.info("운용보고서 생성 완료: noteId={}, teamId={}", saved.getId(), teamId);

        return GenerateReportResponse.builder()
                .noteId(saved.getId())
                .title(title)
                .content(content)
                .creditsUsed(0)
                .build();
    }
}
