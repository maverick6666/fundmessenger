package com.fundmessenger.fund.service;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.fund.dto.MemberRankingResponse;
import com.fundmessenger.fund.dto.MyStatsResponse;
import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.entity.PositionStatus;
import com.fundmessenger.fund.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FundStatsService {

    private final PositionRepository positionRepository;
    private final MemberTagRepository memberTagRepository;

    /**
     * 팀 멤버 랭킹: CLOSED 포지션 기준 실현손익 내림차순
     */
    public List<MemberRankingResponse> getTeamRanking(Long teamId) {
        List<Position> closedPositions = positionRepository
                .findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, PositionStatus.CLOSED);

        // 팀 멤버 목록 (membershipId → name)
        Set<Long> teamMemberIds = memberTagRepository.findByTeamId(teamId).stream()
                .map(mt -> mt.getClubMembership().getId())
                .collect(Collectors.toSet());

        // opener 기준으로 그룹핑 (opener가 팀 멤버인 것만)
        Map<Long, List<Position>> byOpener = closedPositions.stream()
                .filter(p -> p.getOpener() != null && teamMemberIds.contains(p.getOpener().getId()))
                .collect(Collectors.groupingBy(p -> p.getOpener().getId()));

        List<MemberRankingResponse> rankings = new ArrayList<>();

        for (Map.Entry<Long, List<Position>> entry : byOpener.entrySet()) {
            Long membershipId = entry.getKey();
            List<Position> positions = entry.getValue();

            int totalTrades = positions.size();
            long wins = positions.stream()
                    .filter(p -> p.getRealizedProfitLoss() != null
                            && p.getRealizedProfitLoss().compareTo(BigDecimal.ZERO) > 0)
                    .count();

            BigDecimal winRate = totalTrades > 0
                    ? BigDecimal.valueOf(wins)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalTrades), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal realizedPnl = positions.stream()
                    .map(p -> p.getRealizedProfitLoss() != null ? p.getRealizedProfitLoss() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal avgProfitRate = positions.stream()
                    .map(p -> p.getProfitRate() != null ? p.getProfitRate() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP);

            String memberName = positions.get(0).getOpener().getName();

            rankings.add(MemberRankingResponse.builder()
                    .membershipId(membershipId)
                    .memberName(memberName)
                    .totalTrades(totalTrades)
                    .winRate(winRate)
                    .realizedPnl(realizedPnl)
                    .avgProfitRate(avgProfitRate)
                    .build());
        }

        // realizedPnl 내림차순 정렬
        rankings.sort(Comparator.comparing(MemberRankingResponse::getRealizedPnl).reversed());

        return rankings;
    }

    /**
     * 개인 통계: 현재 사용자의 CLOSED 포지션 기준
     */
    public MyStatsResponse getMyStats(Long teamId, ClubMembership membership) {
        List<Position> closedPositions = positionRepository
                .findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, PositionStatus.CLOSED);

        List<Position> myPositions = closedPositions.stream()
                .filter(p -> p.getOpener() != null && p.getOpener().getId().equals(membership.getId()))
                .toList();

        int totalTrades = myPositions.size();

        if (totalTrades == 0) {
            return MyStatsResponse.builder()
                    .totalTrades(0)
                    .winRate(BigDecimal.ZERO)
                    .avgProfitRate(BigDecimal.ZERO)
                    .avgHoldingHours(BigDecimal.ZERO)
                    .successCount(0)
                    .failCount(0)
                    .build();
        }

        int successCount = (int) myPositions.stream()
                .filter(p -> p.getRealizedProfitLoss() != null
                        && p.getRealizedProfitLoss().compareTo(BigDecimal.ZERO) > 0)
                .count();
        int failCount = totalTrades - successCount;

        BigDecimal winRate = BigDecimal.valueOf(successCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTrades), 2, RoundingMode.HALF_UP);

        BigDecimal avgProfitRate = myPositions.stream()
                .map(p -> p.getProfitRate() != null ? p.getProfitRate() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP);

        // 최고/최저 거래
        Position bestTrade = myPositions.stream()
                .filter(p -> p.getProfitRate() != null)
                .max(Comparator.comparing(Position::getProfitRate))
                .orElse(null);

        Position worstTrade = myPositions.stream()
                .filter(p -> p.getProfitRate() != null)
                .min(Comparator.comparing(Position::getProfitRate))
                .orElse(null);

        // 평균 보유 시간
        BigDecimal avgHoldingHours = myPositions.stream()
                .filter(p -> p.getHoldingPeriodHours() != null)
                .map(p -> BigDecimal.valueOf(p.getHoldingPeriodHours()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long holdingCount = myPositions.stream()
                .filter(p -> p.getHoldingPeriodHours() != null)
                .count();

        if (holdingCount > 0) {
            avgHoldingHours = avgHoldingHours.divide(
                    BigDecimal.valueOf(holdingCount), 1, RoundingMode.HALF_UP);
        }

        return MyStatsResponse.builder()
                .totalTrades(totalTrades)
                .winRate(winRate)
                .avgProfitRate(avgProfitRate)
                .bestTradeTicker(bestTrade != null ? bestTrade.getTicker() : null)
                .bestTradeProfitRate(bestTrade != null ? bestTrade.getProfitRate() : null)
                .worstTradeTicker(worstTrade != null ? worstTrade.getTicker() : null)
                .worstTradeProfitRate(worstTrade != null ? worstTrade.getProfitRate() : null)
                .avgHoldingHours(avgHoldingHours)
                .successCount(successCount)
                .failCount(failCount)
                .build();
    }
}
