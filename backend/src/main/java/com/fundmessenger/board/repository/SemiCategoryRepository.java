package com.fundmessenger.board.repository;

import com.fundmessenger.board.entity.SemiCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SemiCategoryRepository extends JpaRepository<SemiCategory, Long> {

    List<SemiCategory> findByClubIdOrderByDisplayOrderAscCreatedAtAsc(Long clubId);

    /** 일반 태그만 (기수 제외) */
    List<SemiCategory> findByClubIdAndGenerationFalseOrderByDisplayOrderAscCreatedAtAsc(Long clubId);

    /** 기수 태그만 */
    List<SemiCategory> findByClubIdAndGenerationTrueOrderByDisplayOrderAsc(Long clubId);

    /** 최신(마지막) 기수 — displayOrder 가장 높은 것 */
    Optional<SemiCategory> findTopByClubIdAndGenerationTrueOrderByDisplayOrderDesc(Long clubId);

    Optional<SemiCategory> findByClubIdAndName(Long clubId, String name);

    long countByClubId(Long clubId);

    List<SemiCategory> findByAdminFolderId(Long adminFolderId);
}
