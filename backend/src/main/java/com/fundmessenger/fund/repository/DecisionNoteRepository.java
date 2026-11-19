package com.fundmessenger.fund.repository;

import com.fundmessenger.fund.entity.DecisionNote;
import com.fundmessenger.fund.entity.NoteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionNoteRepository extends JpaRepository<DecisionNote, Long> {

    List<DecisionNote> findByTeamIdAndNoteTypeOrderByCreatedAtDesc(Long teamId, NoteType noteType);

    List<DecisionNote> findByPositionIdOrderByCreatedAtDesc(Long positionId);

    List<DecisionNote> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    /** 공개된 문서만 (게시판 펀드팀 뷰용) */
    List<DecisionNote> findByPositionIdAndExportEnabledTrueOrderByCreatedAtDesc(Long positionId);
}
