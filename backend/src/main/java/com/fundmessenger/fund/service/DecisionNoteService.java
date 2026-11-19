package com.fundmessenger.fund.service;

import com.fundmessenger.board.entity.Board;
import com.fundmessenger.board.entity.Post;
import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostCommentRepository;
import com.fundmessenger.board.repository.PostLikeRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.fund.dto.CreateDecisionNoteRequest;
import com.fundmessenger.fund.dto.UpdateDecisionNoteRequest;
import com.fundmessenger.fund.entity.DecisionNote;
import com.fundmessenger.fund.entity.NoteType;
import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.repository.DecisionNoteRepository;
import com.fundmessenger.fund.repository.PositionRepository;
import com.fundmessenger.config.exception.BadRequestException;
import com.fundmessenger.config.exception.ForbiddenException;
import com.fundmessenger.config.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionNoteService {

    private final DecisionNoteRepository noteRepository;
    private final PositionRepository positionRepository;
    private final PositionService positionService;
    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostLikeRepository postLikeRepository;

    @Transactional
    public DecisionNote createNote(Long teamId, CreateDecisionNoteRequest request, ClubMembership author) {
        Team team = positionService.validateFundTeam(teamId);

        Position position = null;
        if (request.getPositionId() != null) {
            position = positionRepository.findById(request.getPositionId())
                    .orElseThrow(() -> new NotFoundException("포지션", request.getPositionId()));
            if (!position.getTeam().getId().equals(teamId)) {
                throw new ForbiddenException("해당 팀의 포지션이 아닙니다");
            }
        }

        DecisionNote note = DecisionNote.builder()
                .team(team)
                .position(position)
                .title(request.getTitle())
                .content(request.getContent())
                .noteType(NoteType.valueOf(request.getNoteType().toUpperCase()))
                .author(author)
                .build();

        DecisionNote saved = noteRepository.save(note);
        log.info("DecisionNote created: id={}, teamId={}, type={}", saved.getId(), teamId, request.getNoteType());
        return saved;
    }

    @Transactional
    public DecisionNote updateNote(Long noteId, UpdateDecisionNoteRequest request, ClubMembership membership) {
        DecisionNote note = getNoteById(noteId);
        // 권한 검증은 컨트롤러에서 수행

        if (request.getTitle() != null) {
            note.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            note.setContent(request.getContent());
        }

        DecisionNote saved = noteRepository.save(note);
        log.info("DecisionNote updated: id={}", noteId);
        return saved;
    }

    @Transactional
    public void deleteNote(Long noteId, ClubMembership membership) {
        DecisionNote note = getNoteById(noteId);

        noteRepository.delete(note);
        log.info("DecisionNote deleted: id={}", noteId);
    }

    @Transactional
    public DecisionNote toggleExport(Long noteId, ClubMembership membership) {
        DecisionNote note = getNoteById(noteId);

        boolean newValue = !note.isExportEnabled();
        note.setExportEnabled(newValue);

        if (newValue) {
            // Position.lastExportedAt 갱신 (종목 리스트 정렬 기준)
            if (note.getPosition() != null) {
                Position position = note.getPosition();
                position.setLastExportedAt(java.time.LocalDateTime.now());
                positionRepository.save(position);
            }

            // ON → Post 생성, 클럽 첫 CUSTOM 게시판에 등록 + 팀 태깅
            Long clubId = note.getTeam().getClub().getId();
            Board targetBoard = boardRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(clubId)
                    .stream()
                    .filter(b -> b.getBoardType() == com.fundmessenger.board.entity.BoardType.CUSTOM)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("게시판이 없습니다"));

            Post post = Post.builder()
                    .title(note.getTitle())
                    .content(note.getContent())
                    .author(note.getAuthor())
                    .build();
            post.getBoards().add(targetBoard);
            post.getTeams().add(note.getTeam());

            Post savedPost = postRepository.save(post);
            note.setLinkedPost(savedPost);

            log.info("DecisionNote {} export enabled — Post {} created in board {}",
                    noteId, savedPost.getId(), targetBoard.getId());
        } else {
            // OFF → linkedPost 삭제
            if (note.getLinkedPost() != null) {
                Long postId = note.getLinkedPost().getId();

                // 연관 데이터 삭제 (cascade 미설정이므로 수동 삭제)
                postCommentRepository.deleteByPostId(postId);
                postLikeRepository.deleteByPostId(postId);

                note.setLinkedPost(null);
                noteRepository.save(note); // FK 해제 먼저

                postRepository.deleteById(postId);
                log.info("DecisionNote {} export disabled — Post {} deleted", noteId, postId);
            } else {
                note.setLinkedPost(null);
            }
        }

        return noteRepository.save(note);
    }

    @Transactional(readOnly = true)
    public DecisionNote getNoteById(Long noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new NotFoundException("의사결정서", noteId));
    }

    @Transactional(readOnly = true)
    public List<DecisionNote> getNotesByTeam(Long teamId, NoteType noteType) {
        positionService.validateFundTeam(teamId);
        if (noteType != null) {
            return noteRepository.findByTeamIdAndNoteTypeOrderByCreatedAtDesc(teamId, noteType);
        }
        return noteRepository.findByTeamIdOrderByCreatedAtDesc(teamId);
    }

    @Transactional(readOnly = true)
    public List<DecisionNote> getNotesByPosition(Long positionId) {
        return noteRepository.findByPositionIdOrderByCreatedAtDesc(positionId);
    }

    // ── Private helpers ──

    // 권한 검증은 컨트롤러의 validateAuthorOrManager()에서 처리
}
