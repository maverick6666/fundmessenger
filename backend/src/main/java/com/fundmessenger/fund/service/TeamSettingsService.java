package com.fundmessenger.fund.service;

import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.entity.TeamType;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.fund.dto.ExchangeRequest;
import com.fundmessenger.fund.dto.UpdateTeamSettingsRequest;
import com.fundmessenger.fund.entity.TeamSettings;
import com.fundmessenger.fund.repository.TeamSettingsRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamSettingsService {

    private final TeamSettingsRepository teamSettingsRepository;
    private final TeamRepository teamRepository;

    /**
     * 팀 설정 조회 (없으면 기본값으로 생성)
     */
    @Transactional
    public TeamSettings getOrCreateSettings(Long teamId) {
        return teamSettingsRepository.findByTeamId(teamId)
                .orElseGet(() -> {
                    Team team = teamRepository.findById(teamId)
                            .orElseThrow(() -> new NotFoundException("팀", teamId));
                    if (team.getTeamType() != TeamType.FUND) {
                        throw new BadRequestException("FUND 타입 팀에서만 설정을 관리할 수 있습니다");
                    }

                    TeamSettings settings = TeamSettings.builder()
                            .team(team)
                            .initialCapitalKrw(BigDecimal.ZERO)
                            .initialCapitalUsd(BigDecimal.ZERO)
                            .build();
                    return teamSettingsRepository.save(settings);
                });
    }

    /**
     * 초기 자본금 수정
     */
    @Transactional
    public TeamSettings updateSettings(Long teamId, UpdateTeamSettingsRequest request) {
        TeamSettings settings = getOrCreateSettings(teamId);

        if (request.getInitialCapitalKrw() != null) {
            settings.setInitialCapitalKrw(request.getInitialCapitalKrw());
        }
        if (request.getInitialCapitalUsd() != null) {
            settings.setInitialCapitalUsd(request.getInitialCapitalUsd());
        }
        if (request.getProfitPublic() != null) {
            settings.setProfitPublic(request.getProfitPublic());
        }

        return teamSettingsRepository.save(settings);
    }

    /**
     * 환전 기록 추가
     */
    @Transactional
    public TeamSettings addExchange(Long teamId, ExchangeRequest request, Long userId) {
        TeamSettings settings = getOrCreateSettings(teamId);

        // 자본금 조정
        if ("KRW".equalsIgnoreCase(request.getFromCurrency())) {
            settings.setInitialCapitalKrw(settings.getInitialCapitalKrw().subtract(request.getFromAmount()));
        } else if ("USD".equalsIgnoreCase(request.getFromCurrency())) {
            settings.setInitialCapitalUsd(settings.getInitialCapitalUsd().subtract(request.getFromAmount()));
        }

        if ("KRW".equalsIgnoreCase(request.getToCurrency())) {
            settings.setInitialCapitalKrw(settings.getInitialCapitalKrw().add(request.getToAmount()));
        } else if ("USD".equalsIgnoreCase(request.getToCurrency())) {
            settings.setInitialCapitalUsd(settings.getInitialCapitalUsd().add(request.getToAmount()));
        }

        // 환전 이력 추가
        List<Map<String, Object>> history = settings.getExchangeHistory() != null
                ? new ArrayList<>(settings.getExchangeHistory())
                : new ArrayList<>();

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("from_currency", request.getFromCurrency());
        record.put("to_currency", request.getToCurrency());
        record.put("from_amount", request.getFromAmount().doubleValue());
        record.put("to_amount", request.getToAmount().doubleValue());
        if (request.getExchangeRate() != null) {
            record.put("exchange_rate", request.getExchangeRate().doubleValue());
        }
        if (request.getMemo() != null) {
            record.put("memo", request.getMemo());
        }
        record.put("date", LocalDateTime.now().toString());
        record.put("user_id", userId);
        history.add(record);

        settings.setExchangeHistory(history);

        TeamSettings saved = teamSettingsRepository.save(settings);
        log.info("Exchange record added for team {}: {} {} -> {} {}",
                teamId, request.getFromCurrency(), request.getFromAmount(),
                request.getToCurrency(), request.getToAmount());
        return saved;
    }
}
