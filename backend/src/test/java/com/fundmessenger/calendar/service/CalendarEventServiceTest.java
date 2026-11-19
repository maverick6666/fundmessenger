package com.fundmessenger.calendar.service;

import com.fundmessenger.calendar.dto.CalendarEventResponse;
import com.fundmessenger.calendar.dto.CreateCalendarEventRequest;
import com.fundmessenger.calendar.dto.UpdateCalendarEventRequest;
import com.fundmessenger.calendar.entity.CalendarEvent;
import com.fundmessenger.calendar.repository.CalendarEventRepository;
import com.fundmessenger.club.entity.Club;
import com.fundmessenger.club.entity.ClubMembership;
import com.fundmessenger.club.repository.ClubRepository;
import com.fundmessenger.club.repository.TeamRepository;
import com.fundmessenger.club.service.ClubAuthorizationService;
import com.fundmessenger.support.TestFixtures;
import com.fundmessenger.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {

    @Mock CalendarEventRepository calendarEventRepository;
    @Mock ClubRepository clubRepository;
    @Mock TeamRepository teamRepository;
    @Mock ClubAuthorizationService authService;

    @InjectMocks CalendarEventService sut;

    User user;
    Club club;
    ClubMembership membership;

    @BeforeEach
    void setUp() {
        user = TestFixtures.user(1L, "test@test.dev");
        club = TestFixtures.club(1L, "테스트동아리");
        membership = TestFixtures.approvedMembership(1L, user, club);
    }

    @Test
    @DisplayName("일정 생성 — 동아리 전체 일정이 정상 생성된다")
    void createEvent_success() {
        // given
        CreateCalendarEventRequest request = new CreateCalendarEventRequest();
        setField(request, "title", "정기 모임");
        setField(request, "eventDate", LocalDate.of(2026, 4, 1));
        setField(request, "eventTime", LocalTime.of(18, 0));
        setField(request, "description", "4월 정기 모임");
        setField(request, "teamId", null);

        given(authService.validateClubAdmin(1L, 1L)).willReturn(membership);
        given(clubRepository.findById(1L)).willReturn(Optional.of(club));
        given(calendarEventRepository.save(any(CalendarEvent.class)))
                .willAnswer(inv -> {
                    CalendarEvent e = inv.getArgument(0);
                    e.setId(100L);
                    e.setCreatedAt(java.time.LocalDateTime.now());
                    return e;
                });

        // when
        CalendarEventResponse result = sut.createEvent(1L, request, 1L);

        // then
        assertThat(result.getTitle()).isEqualTo("정기 모임");
        assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        then(calendarEventRepository).should().save(any(CalendarEvent.class));
    }

    @Test
    @DisplayName("월간 일정 조회 — 해당 월의 일정 목록을 반환한다")
    void getEventsByMonth_returnsFilteredEvents() {
        // given
        CalendarEvent event = CalendarEvent.builder()
                .club(club).title("모임").eventDate(LocalDate.of(2026, 3, 15))
                .createdBy(membership).build();
        event.setId(1L);
        event.setCreatedAt(java.time.LocalDateTime.now());

        given(authService.validateClubMember(1L, 1L)).willReturn(membership);
        given(calendarEventRepository.findByClubIdAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(event));

        // when
        List<CalendarEventResponse> result = sut.getEventsByMonth(1L, 2026, 3, 1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("모임");
    }

    @Test
    @DisplayName("일정 수정 — 작성자(임원급)가 수정하면 성공한다")
    void updateEvent_byCreator_succeeds() {
        // given
        CalendarEvent event = CalendarEvent.builder()
                .club(club).title("구제목").eventDate(LocalDate.of(2026, 3, 10))
                .createdBy(membership).build();
        event.setId(10L);
        event.setCreatedAt(java.time.LocalDateTime.now());

        UpdateCalendarEventRequest request = new UpdateCalendarEventRequest();
        setField(request, "title", "신제목");
        setField(request, "eventDate", LocalDate.of(2026, 3, 20));
        setField(request, "description", "수정된 설명");

        given(calendarEventRepository.findById(10L)).willReturn(Optional.of(event));
        given(authService.validateClubAdmin(1L, 1L)).willReturn(membership);

        // when
        CalendarEventResponse result = sut.updateEvent(1L, 10L, request, 1L);

        // then
        assertThat(result.getTitle()).isEqualTo("신제목");
        assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2026, 3, 20));
    }

    @Test
    @DisplayName("일정 삭제 — 정상 삭제된다")
    void deleteEvent_succeeds() {
        // given
        CalendarEvent event = CalendarEvent.builder()
                .club(club).title("삭제할 일정").eventDate(LocalDate.of(2026, 3, 10))
                .createdBy(membership).build();
        event.setId(10L);

        given(calendarEventRepository.findById(10L)).willReturn(Optional.of(event));
        given(authService.validateClubAdmin(1L, 1L)).willReturn(membership);

        // when
        sut.deleteEvent(1L, 10L, 1L);

        // then
        then(calendarEventRepository).should().delete(event);
    }

    // --- Reflection helper for Lombok @Getter-only DTOs ---
    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
