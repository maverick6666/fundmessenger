package com.fundmessenger.fund.service;

import com.fundmessenger.club.entity.Team;
import com.fundmessenger.fund.dto.CreateAssetSnapshotRequest;
import com.fundmessenger.fund.entity.AssetSnapshot;
import com.fundmessenger.fund.repository.AssetSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetSnapshotService {

    private final AssetSnapshotRepository snapshotRepository;
    private final PositionService positionService;

    @Transactional
    public AssetSnapshot createSnapshot(Long teamId, CreateAssetSnapshotRequest request) {
        Team team = positionService.validateFundTeam(teamId);

        AssetSnapshot snapshot = AssetSnapshot.builder()
                .team(team)
                .snapshotDate(request.getSnapshotDate())
                .krwCash(safe(request.getKrwCash()))
                .krwEvaluation(safe(request.getKrwEvaluation()))
                .usdCash(safe(request.getUsdCash()))
                .usdEvaluation(safe(request.getUsdEvaluation()))
                .totalKrw(safe(request.getTotalKrw()))
                .exchangeRate(safe(request.getExchangeRate()))
                .realizedPnl(safe(request.getRealizedPnl()))
                .unrealizedPnl(safe(request.getUnrealizedPnl()))
                .positionDetails(request.getPositionDetails())
                .build();

        AssetSnapshot saved = snapshotRepository.save(snapshot);
        log.info("AssetSnapshot created: id={}, teamId={}, date={}", saved.getId(), teamId, request.getSnapshotDate());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AssetSnapshot> getSnapshots(Long teamId, LocalDate from, LocalDate to) {
        positionService.validateFundTeam(teamId);
        if (from != null && to != null) {
            return snapshotRepository.findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(teamId, from, to);
        }
        return snapshotRepository.findByTeamIdOrderBySnapshotDateDesc(teamId);
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
