package com.fundmessenger.club.service;

import com.fundmessenger.club.dto.AllocateCreditRequest;
import com.fundmessenger.club.dto.CreditHistoryResponse;
import com.fundmessenger.club.dto.CreditSummaryResponse;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.CreditHistory;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.CreditHistoryRepository;
import com.fundmessenger.club.repository.MemberTagRepository;
import com.fundmessenger.club.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CreditService {

    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final MemberTagRepository memberTagRepository;
    private final CreditHistoryRepository creditHistoryRepository;
    private final ClubAuthorizationService authService;

    /**
     * 크레딧 요약 조회 (동아리 잔액 + 팀별 잔액)
     * 모든 멤버가 조회 가능
     */
    public CreditSummaryResponse getCreditSummary(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));

        List<Team> teams = teamRepository.findByClubIdAndDeletedFalseOrderByCreatedAtAsc(clubId);

        int totalAllocated = teams.stream()
                .mapToInt(Team::getCreditAllocation)
                .sum();

        List<CreditSummaryResponse.TeamCreditInfo> teamInfos = teams.stream()
                .map(team -> CreditSummaryResponse.TeamCreditInfo.builder()
                        .teamId(team.getId())
                        .teamName(team.getName())
                        .creditAllocation(team.getCreditAllocation())
                        .memberCount(memberTagRepository.countDistinctMembersByTeamId(team.getId()))
                        .build())
                .toList();

        return CreditSummaryResponse.builder()
                .clubBalance(club.getCreditBalance())
                .totalAllocated(totalAllocated)
                .totalBalance(club.getCreditBalance() + totalAllocated)
                .teams(teamInfos)
                .build();
    }

    /**
     * 크레딧 내역 조회
     * 모든 멤버 조회 가능
     */
    public List<CreditHistoryResponse> getCreditHistory(Long clubId, Long userId) {
        authService.validateClubMember(userId, clubId);
        return creditHistoryRepository.findByClubIdOrderByCreatedAtDesc(clubId).stream()
                .map(CreditHistoryResponse::from)
                .toList();
    }

    /**
     * 동아리 잔액 → 팀으로 분배
     * 회장/VP만 가능
     */
    @Transactional
    public CreditSummaryResponse allocateToTeam(Long clubId, AllocateCreditRequest request, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        // SEC-6: 양수 검증 (음수 시 잔액 조작 가능)
        if (request.getAmount() <= 0) {
            throw new IllegalArgumentException("금액은 양수여야 합니다");
        }

        Club club = clubRepository.findByIdForUpdate(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));

        Team team = teamRepository.findByIdForUpdate(request.getTeamId())
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다"));

        if (!team.getClub().getId().equals(clubId)) {
            throw new IllegalArgumentException("해당 동아리의 팀이 아닙니다");
        }

        if (club.getCreditBalance() < request.getAmount()) {
            throw new IllegalArgumentException(
                    "동아리 잔액이 부족합니다 (잔액: " + club.getCreditBalance() + ", 요청: " + request.getAmount() + ")");
        }

        // 동아리 잔액 차감, 팀 할당 증가
        club.setCreditBalance(club.getCreditBalance() - request.getAmount());
        team.setCreditAllocation(team.getCreditAllocation() + request.getAmount());

        // 내역 기록
        creditHistoryRepository.save(CreditHistory.builder()
                .club(club)
                .team(team)
                .amount(request.getAmount())
                .type(CreditHistory.CreditType.ALLOCATE)
                .description(request.getDescription() != null && !request.getDescription().isBlank()
                        ? request.getDescription().trim()
                        : team.getName() + " 팀에 분배")
                .build());

        log.info("크레딧 분배: club={}, team={}, amount={}", club.getName(), team.getName(), request.getAmount());

        return getCreditSummary(clubId, userId);
    }

    /**
     * 팀 → 동아리 잔액으로 회수
     * 회장/VP만 가능
     */
    @Transactional
    public CreditSummaryResponse reclaimFromTeam(Long clubId, AllocateCreditRequest request, Long userId) {
        authService.validateClubAdmin(userId, clubId);

        // SEC-6: 양수 검증 (음수 시 잔액 조작 가능)
        if (request.getAmount() <= 0) {
            throw new IllegalArgumentException("금액은 양수여야 합니다");
        }

        Club club = clubRepository.findByIdForUpdate(clubId)
                .orElseThrow(() -> new IllegalArgumentException("동아리를 찾을 수 없습니다"));

        Team team = teamRepository.findByIdForUpdate(request.getTeamId())
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다"));

        if (!team.getClub().getId().equals(clubId)) {
            throw new IllegalArgumentException("해당 동아리의 팀이 아닙니다");
        }

        if (team.getCreditAllocation() < request.getAmount()) {
            throw new IllegalArgumentException(
                    "팀 잔액이 부족합니다 (잔액: " + team.getCreditAllocation() + ", 요청: " + request.getAmount() + ")");
        }

        // 팀 할당 차감, 동아리 잔액 증가
        team.setCreditAllocation(team.getCreditAllocation() - request.getAmount());
        club.setCreditBalance(club.getCreditBalance() + request.getAmount());

        // 내역 기록
        creditHistoryRepository.save(CreditHistory.builder()
                .club(club)
                .team(team)
                .amount(request.getAmount())
                .type(CreditHistory.CreditType.RECLAIM)
                .description(request.getDescription() != null && !request.getDescription().isBlank()
                        ? request.getDescription().trim()
                        : team.getName() + " 팀에서 회수")
                .build());

        log.info("크레딧 회수: club={}, team={}, amount={}", club.getName(), team.getName(), request.getAmount());

        return getCreditSummary(clubId, userId);
    }
}
