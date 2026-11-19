package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.TagDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagDefinitionRepository extends JpaRepository<TagDefinition, Long> {

    List<TagDefinition> findByClubIdOrderByDisplayOrderAsc(Long clubId);

    Optional<TagDefinition> findByClubIdAndName(Long clubId, String name);

    boolean existsByClubIdAndName(Long clubId, String name);

    void deleteAllByTeamId(Long teamId);

    List<TagDefinition> findByAdminFolderId(Long adminFolderId);
}
