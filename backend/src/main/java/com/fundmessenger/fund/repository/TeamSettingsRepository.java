package com.fundmessenger.fund.repository;

import com.fundmessenger.fund.entity.TeamSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamSettingsRepository extends JpaRepository<TeamSettings, Long> {

    Optional<TeamSettings> findByTeamId(Long teamId);
}
