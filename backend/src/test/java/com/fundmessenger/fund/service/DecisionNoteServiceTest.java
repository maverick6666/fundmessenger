package com.fundmessenger.fund.service;

import com.fundmessenger.board.repository.BoardRepository;
import com.fundmessenger.board.repository.PostCommentRepository;
import com.fundmessenger.board.repository.PostLikeRepository;
import com.fundmessenger.board.repository.PostRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.entity.Team;
import com.fundmessenger.config.exception.NotFoundException;
import com.fundmessenger.fund.dto.CreateDecisionNoteRequest;
import com.fundmessenger.fund.dto.UpdateDecisionNoteRequest;
import com.fundmessenger.fund.entity.DecisionNote;
import com.fundmessenger.fund.entity.NoteType;
import com.fundmessenger.fund.entity.Position;
import com.fundmessenger.fund.entity.PositionStatus;
import com.fundmessenger.fund.repository.DecisionNoteRepository;
import com.fundmessenger.fund.repository.PositionRepository;
import com.fundmessenger.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DecisionNoteService 단위 테스트")
class DecisionNoteServiceTest {

    @Mock
    private DecisionNoteRepository noteRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private PositionService positionService;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostCommentRepository postCommentRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @InjectMocks
    private DecisionNoteService decisionNoteService;

    private Club club;
    private Team fundTeam;
    private ClubMembership author;
    private ClubMembership otherMember;

    @BeforeEach
    void setUp() {
        club = TestFixtures.club(1L, "투자동아리");
        fundTeam = TestFixtures.fundTeam(10L, club, "펀드A팀");

        var user1 = TestFixtures.user(1L, "author@test.dev");
        author = TestFixtures.approvedMembership(1L, user1, club);

        var user2 = TestFixtures.user(2L, "other@test.dev");
        otherMember = TestFixtures.approvedMembership(2L, user2, club);
    }

    // ========== createNote ==========

    @Nested
    @DisplayName("createNote — 의사결정서 생성")
    class CreateNote {

        @Test
        @DisplayName("포지션 없이 의사결정서를 생성한다")
        void createNote_withoutPosition_success() {
            // given
            CreateDecisionNoteRequest request = new CreateDecisionNoteRequest();
            request.setTitle("삼성전자 매수 분석");
            request.setContent("실적 기반 분석 내용");
            request.setNoteType("DECISION");

            given(positionService.validateFundTeam(10L)).willReturn(fundTeam);
            given(noteRepository.save(any(DecisionNote.class)))
                    .willAnswer(inv -> {
                        DecisionNote n = inv.getArgument(0);
                        n.setId(1L);
                        return n;
                    });

            // when
            DecisionNote result = decisionNoteService.createNote(10L, request, author);

            // then
            assertThat(result.getTitle()).isEqualTo("삼성전자 매수 분석");
            assertThat(result.getNoteType()).isEqualTo(NoteType.DECISION);
            assertThat(result.getAuthor()).isEqualTo(author);
            assertThat(result.getPosition()).isNull();
        }

        @Test
        @DisplayName("포지션과 연결된 의사결정서를 생성한다")
        void createNote_withPosition_success() {
            // given
            Position position = Position.builder()
                    .id(100L).team(fundTeam).ticker("005930").status(PositionStatus.OPEN).build();

            CreateDecisionNoteRequest request = new CreateDecisionNoteRequest();
            request.setPositionId(100L);
            request.setTitle("삼성전자 추가매수 근거");
            request.setContent("상세 근거");
            request.setNoteType("DECISION");

            given(positionService.validateFundTeam(10L)).willReturn(fundTeam);
            given(positionRepository.findById(100L)).willReturn(Optional.of(position));
            given(noteRepository.save(any(DecisionNote.class)))
                    .willAnswer(inv -> {
                        DecisionNote n = inv.getArgument(0);
                        n.setId(2L);
                        return n;
                    });

            // when
            DecisionNote result = decisionNoteService.createNote(10L, request, author);

            // then
            assertThat(result.getPosition()).isEqualTo(position);
            assertThat(result.getTitle()).isEqualTo("삼성전자 추가매수 근거");
        }

        @Test
        @DisplayName("존재하지 않는 포지션 ID로 생성하면 NotFoundException을 던진다")
        void createNote_invalidPositionId_throws() {
            // given
            CreateDecisionNoteRequest request = new CreateDecisionNoteRequest();
            request.setPositionId(999L);
            request.setTitle("테스트");
            request.setContent("내용");
            request.setNoteType("DECISION");

            given(positionService.validateFundTeam(10L)).willReturn(fundTeam);
            given(positionRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> decisionNoteService.createNote(10L, request, author))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ========== updateNote ==========

    @Nested
    @DisplayName("updateNote — 의사결정서 수정")
    class UpdateNote {

        @Test
        @DisplayName("작성자가 제목과 내용을 수정한다")
        void updateNote_byAuthor_success() {
            // given
            DecisionNote existingNote = DecisionNote.builder()
                    .id(1L)
                    .team(fundTeam)
                    .title("원본 제목")
                    .content("원본 내용")
                    .noteType(NoteType.DECISION)
                    .author(author)
                    .build();

            UpdateDecisionNoteRequest request = new UpdateDecisionNoteRequest();
            request.setTitle("수정된 제목");
            request.setContent("수정된 내용");

            given(noteRepository.findById(1L)).willReturn(Optional.of(existingNote));
            given(noteRepository.save(any(DecisionNote.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            DecisionNote result = decisionNoteService.updateNote(1L, request, author);

            // then
            assertThat(result.getTitle()).isEqualTo("수정된 제목");
            assertThat(result.getContent()).isEqualTo("수정된 내용");
        }

        @Test
        @DisplayName("제목만 수정하면 내용은 그대로 유지된다")
        void updateNote_titleOnly_contentUnchanged() {
            // given
            DecisionNote existingNote = DecisionNote.builder()
                    .id(1L)
                    .team(fundTeam)
                    .title("원본 제목")
                    .content("원본 내용")
                    .noteType(NoteType.DECISION)
                    .author(author)
                    .build();

            UpdateDecisionNoteRequest request = new UpdateDecisionNoteRequest();
            request.setTitle("새 제목");
            // content is null — should not change

            given(noteRepository.findById(1L)).willReturn(Optional.of(existingNote));
            given(noteRepository.save(any(DecisionNote.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            DecisionNote result = decisionNoteService.updateNote(1L, request, author);

            // then
            assertThat(result.getTitle()).isEqualTo("새 제목");
            assertThat(result.getContent()).isEqualTo("원본 내용");
        }
    }

    // ========== deleteNote ==========

    @Nested
    @DisplayName("deleteNote — 의사결정서 삭제")
    class DeleteNote {

        @Test
        @DisplayName("존재하는 의사결정서를 삭제한다")
        void deleteNote_success() {
            // given
            DecisionNote note = DecisionNote.builder()
                    .id(1L)
                    .team(fundTeam)
                    .title("삭제 대상")
                    .content("내용")
                    .noteType(NoteType.DECISION)
                    .author(author)
                    .build();

            given(noteRepository.findById(1L)).willReturn(Optional.of(note));

            // when
            decisionNoteService.deleteNote(1L, author);

            // then
            verify(noteRepository).delete(note);
        }

        @Test
        @DisplayName("존재하지 않는 의사결정서를 삭제하면 NotFoundException을 던진다")
        void deleteNote_notFound_throws() {
            // given
            given(noteRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> decisionNoteService.deleteNote(999L, author))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ========== toggleExport ==========

    @Nested
    @DisplayName("toggleExport — 내보내기 토글")
    class ToggleExport {

        @Test
        @DisplayName("exportEnabled=false인 노트를 토글하면 true가 된다")
        void toggleExport_offToOn() {
            // given
            DecisionNote note = DecisionNote.builder()
                    .id(1L)
                    .team(fundTeam)
                    .title("내보내기 테스트")
                    .content("내용")
                    .noteType(NoteType.REPORT)
                    .author(author)
                    .exportEnabled(false)
                    .build();

            var board = TestFixtures.board(1L, club, "자유게시판", com.fundmessenger.board.entity.WritePermission.EVERYONE);

            given(noteRepository.findById(1L)).willReturn(Optional.of(note));
            given(boardRepository.findByClubIdOrderByDisplayOrderAscCreatedAtAsc(1L))
                    .willReturn(java.util.List.of(board));
            given(postRepository.save(any())).willAnswer(inv -> {
                var post = inv.getArgument(0);
                ((com.fundmessenger.board.entity.Post) post).setId(50L);
                return post;
            });
            given(noteRepository.save(any(DecisionNote.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            DecisionNote result = decisionNoteService.toggleExport(1L, author);

            // then
            assertThat(result.isExportEnabled()).isTrue();
            assertThat(result.getLinkedPost()).isNotNull();
        }

        @Test
        @DisplayName("exportEnabled=true인 노트를 토글하면 false가 되고 linkedPost가 삭제된다")
        void toggleExport_onToOff() {
            // given
            var post = com.fundmessenger.board.entity.Post.builder()
                    .title("기존 포스트")
                    .content("내용")
                    .author(author)
                    .build();
            post.setId(50L);

            DecisionNote note = DecisionNote.builder()
                    .id(1L)
                    .team(fundTeam)
                    .title("내보내기 테스트")
                    .content("내용")
                    .noteType(NoteType.REPORT)
                    .author(author)
                    .exportEnabled(true)
                    .linkedPost(post)
                    .build();

            given(noteRepository.findById(1L)).willReturn(Optional.of(note));
            given(noteRepository.save(any(DecisionNote.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            DecisionNote result = decisionNoteService.toggleExport(1L, author);

            // then
            assertThat(result.isExportEnabled()).isFalse();
            verify(postCommentRepository).deleteByPostId(50L);
            verify(postLikeRepository).deleteByPostId(50L);
            verify(postRepository).deleteById(50L);
        }
    }
}
