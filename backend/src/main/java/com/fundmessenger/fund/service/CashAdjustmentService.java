package com.fundmessenger.fund.service;

import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.fund.dto.CreateCashAdjustmentRequest;
import com.fundmessenger.fund.entity.AdjustmentType;
import com.fundmessenger.fund.entity.CashAdjustment;
import com.fundmessenger.fund.repository.CashAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashAdjustmentService {

    private final CashAdjustmentRepository adjustmentRepository;
    private final PositionService positionService;

    @Transactional
    public CashAdjustment recordAdjustment(Long teamId, CreateCashAdjustmentRequest request, ClubMembership recordedBy) {
        Team team = positionService.validateFundTeam(teamId);

        CashAdjustment adjustment = CashAdjustment.builder()
                .team(team)
                .adjustmentType(AdjustmentType.valueOf(request.getAdjustmentType().toUpperCase()))
                .currency(request.getCurrency())
                .amount(request.getAmount())
                .memo(request.getMemo())
                .recordedBy(recordedBy)
                .build();

        CashAdjustment saved = adjustmentRepository.save(adjustment);
        log.info("CashAdjustment recorded: id={}, teamId={}, type={}, amount={} {}",
                saved.getId(), teamId, request.getAdjustmentType(), request.getAmount(), request.getCurrency());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CashAdjustment> getAdjustments(Long teamId) {
        positionService.validateFundTeam(teamId);
        return adjustmentRepository.findByTeamIdOrderByCreatedAtDesc(teamId);
    }
}
