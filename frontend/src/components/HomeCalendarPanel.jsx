import { useEffect, useState, useCallback } from 'react'
import api from '../lib/api'
import EventFormModal from './EventFormModal'
import { GenericSkeleton } from './SkeletonRow'
import handleApiError from '../lib/handleApiError'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/**
 * 캘린더 패널 — 좌측 월간 그리드 + 우측 다가오는 일정 + 탭 필터
 *
 * Props:
 *  - clubId, isExecutive: 기본
 *  - teams: [{ teamId, teamName }] — 탭 필터에 사용 (홈에서 전달)
 *  - fixedTeamId: 팀 페이지에서 사용 시 해당 팀으로 고정
 *  - showFilter: 탭 필터 표시 여부 (기본 true)
 */
export default function HomeCalendarPanel({ clubId, isExecutive, teams = [], fixedTeamId, showFilter = true, myTeams = [] }) {
  const today = new Date()
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [monthEvents, setMonthEvents] = useState([])
  const [upcoming, setUpcoming] = useState([])
  const [selectedDate, setSelectedDate] = useState(null)
  const [loadingMonth, setLoadingMonth] = useState(true)
  const [loadingUpcoming, setLoadingUpcoming] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editingEvent, setEditingEvent] = useState(null)

  // 필터: 'all' | 'club' | teamId(number)
  const [filter, setFilter] = useState(fixedTeamId ? fixedTeamId : 'club')

  // fixedTeamId 변경 시 필터 동기화
  useEffect(() => {
    if (fixedTeamId) setFilter(fixedTeamId)
  }, [fixedTeamId])

  // 필터 → API 쿼리 파라미터 빌드
  const buildFilterParams = useCallback(() => {
    if (filter === 'club') return '&clubOnly=true'
    if (typeof filter === 'number') return `&teamId=${filter}`
    return '' // 'all' → 전체
  }, [filter])

  const fetchMonth = useCallback(() => {
    if (!clubId) return
    setLoadingMonth(true)
    api.get(`/clubs/${clubId}/calendar/events?year=${year}&month=${month}${buildFilterParams()}`)
      .then(data => setMonthEvents(data || []))
      .catch(e => handleApiError(e, '월간 일정 조회'))
      .finally(() => setLoadingMonth(false))
  }, [clubId, year, month, buildFilterParams])

  const fetchUpcoming = useCallback(() => {
    if (!clubId) return
    setLoadingUpcoming(true)
    api.get(`/clubs/${clubId}/calendar/events/upcoming?limit=5${buildFilterParams()}`)
      .then(data => setUpcoming(data || []))
      .catch(e => handleApiError(e, '다가오는 일정 조회'))
      .finally(() => setLoadingUpcoming(false))
  }, [clubId, buildFilterParams])

  useEffect(() => { fetchMonth() }, [fetchMonth])
  useEffect(() => { fetchUpcoming() }, [fetchUpcoming])

  // 달력 그리드
  const firstDay = new Date(year, month - 1, 1).getDay()
  const totalDays = new Date(year, month, 0).getDate()
  const cells = []
  for (let i = 0; i < 35; i++) {
    const dayNum = i - firstDay + 1
    cells.push(dayNum >= 1 && dayNum <= totalDays ? dayNum : null)
  }
  if (cells[34] !== null && firstDay + totalDays > 35) {
    for (let i = 35; i < 42; i++) {
      const dayNum = i - firstDay + 1
      cells.push(dayNum <= totalDays ? dayNum : null)
    }
  }

  const eventsByDate = {}
  monthEvents.forEach(ev => {
    const day = parseInt(ev.eventDate.split('-')[2], 10)
    if (!eventsByDate[day]) eventsByDate[day] = []
    eventsByDate[day].push(ev)
  })

  const prevMonth = () => {
    setSelectedDate(null)
    if (month === 1) { setYear(y => y - 1); setMonth(12) }
    else setMonth(m => m - 1)
  }
  const nextMonth = () => {
    setSelectedDate(null)
    if (month === 12) { setYear(y => y + 1); setMonth(1) }
    else setMonth(m => m + 1)
  }

  const todayDay = today.getFullYear() === year && today.getMonth() + 1 === month ? today.getDate() : null

  const rightEvents = selectedDate ? (eventsByDate[selectedDate] || []) : upcoming
  const rightTitle = selectedDate ? `${month}/${selectedDate} 일정` : '다가오는 일정'
  const rightLoading = selectedDate ? loadingMonth : loadingUpcoming

  // [+ 추가] 버튼 권한: 동아리/전체 → 임원급, 팀 필터 → 임원급 또는 해당 팀 팀장/매니저
  const canAddEvent = isExecutive || (
    typeof filter === 'number' && myTeams.some(t => t.teamId === filter && (t.teamRole === 'LEADER' || t.teamRole === 'MANAGER'))
  )

  const formatTime = (t) => t ? t.substring(0, 5) : '종일'
  const formatDate = (dateStr) => {
    const d = new Date(dateStr)
    return `${d.getMonth() + 1}/${d.getDate()}`
  }

  const handleEventSaved = () => {
    fetchMonth()
    fetchUpcoming()
    setEditingEvent(null)
    setShowForm(false)
  }

  // EventFormModal에 전달할 teamId 결정
  const formTeamId = typeof filter === 'number' ? filter : (fixedTeamId || null)

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* 탭 필터 — 동아리 + 내 팀만 */}
      {showFilter && (
        <div className="px-4 pt-3 pb-1 flex gap-1.5 flex-wrap border-b border-slate-100">
          <FilterTab label="동아리" active={filter === 'club'} onClick={() => setFilter('club')} />
          {myTeams.map(t => (
            <FilterTab
              key={t.teamId}
              label={t.teamName}
              active={filter === t.teamId}
              onClick={() => setFilter(t.teamId)}
            />
          ))}
        </div>
      )}

      {/* 메인: 좌측 캘린더 + 우측 일정 */}
      <div className="flex flex-col md:flex-row">
        {/* 좌측: 캘린더 그리드 */}
        <div className="flex-1 p-4 border-b md:border-b-0 md:border-r border-slate-100">
          <div className="flex items-center justify-between mb-3">
            <button onClick={prevMonth} className="p-1 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer text-slate-500 text-xs">
              ◀
            </button>
            <h3 className="text-sm font-bold text-slate-900">{year}년 {month}월</h3>
            <button onClick={nextMonth} className="p-1 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer text-slate-500 text-xs">
              ▶
            </button>
          </div>

          <div className="grid grid-cols-7 mb-1">
            {WEEKDAYS.map((day, i) => (
              <div key={day} className={`text-center text-xs font-medium py-0.5 ${i === 0 ? 'text-red-400' : i === 6 ? 'text-blue-400' : 'text-slate-400'}`}>
                {day}
              </div>
            ))}
          </div>

          {loadingMonth ? (
            <div className="py-8">
              <GenericSkeleton count={3} />
            </div>
          ) : (
            <div className="grid grid-cols-7">
              {cells.map((dayNum, i) => {
                if (dayNum === null) return <div key={i} className="h-8" />
                const isToday = dayNum === todayDay
                const isSelected = dayNum === selectedDate
                const hasEvents = !!eventsByDate[dayNum]
                const dow = i % 7

                return (
                  <button
                    key={i}
                    onClick={() => setSelectedDate(dayNum === selectedDate ? null : dayNum)}
                    className={`
                      h-8 flex flex-col items-center justify-center rounded text-xs transition-all cursor-pointer relative
                      ${isSelected ? 'bg-primary text-white' : isToday ? 'bg-blue-50 text-blue-600 font-bold' : 'hover:bg-slate-50'}
                      ${!isSelected && dow === 0 ? 'text-red-400' : ''}
                      ${!isSelected && dow === 6 ? 'text-blue-400' : ''}
                    `}
                  >
                    {dayNum}
                    {hasEvents && !isSelected && (
                      <span className="absolute bottom-0.5 w-1 h-1 bg-blue-500 rounded-full" />
                    )}
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* 우측: 일정 목록 */}
        <div className="w-full md:flex-1 p-4 flex flex-col">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-bold text-slate-900">{rightTitle}</h3>
            {canAddEvent && (
              <button
                onClick={() => setShowForm(true)}
                className="text-xs px-2 py-1 bg-blue-50 text-blue-600 rounded-lg hover:bg-blue-100 transition-colors cursor-pointer"
              >
                + 추가
              </button>
            )}
          </div>

          <div>
            {rightLoading ? (
              <GenericSkeleton count={3} />
            ) : rightEvents.length === 0 ? (
              <p className="text-xs text-slate-400 py-6 text-center">
                {selectedDate ? '이 날짜에 일정이 없습니다' : '등록된 일정이 없습니다'}
              </p>
            ) : (
              <div className="space-y-2">
                {rightEvents.map(ev => (
                  <div
                    key={ev.id}
                    onClick={() => canAddEvent && setEditingEvent(ev)}
                    className={`px-3 py-2 bg-slate-50 rounded-lg ${canAddEvent ? 'cursor-pointer hover:bg-slate-100' : ''}`}
                  >
                    <div className="flex items-center gap-2">
                      {!selectedDate && (
                        <span className="text-xs text-blue-500 font-medium shrink-0">
                          {formatDate(ev.eventDate)}
                        </span>
                      )}
                      <span className="text-xs text-slate-400 shrink-0">
                        {formatTime(ev.eventTime)}
                      </span>
                    </div>
                    <p className="text-sm text-slate-800 truncate mt-0.5">{ev.title}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 일정 폼 모달 */}
      {(showForm || editingEvent) && (
        <EventFormModal
          clubId={clubId}
          event={editingEvent}
          defaultTeamId={formTeamId}
          teams={teams}
          onClose={() => { setShowForm(false); setEditingEvent(null) }}
          onSaved={handleEventSaved}
        />
      )}
    </div>
  )
}

function FilterTab({ label, active, onClick }) {
  return (
    <button
      onClick={onClick}
      className={`shrink-0 text-xs px-3 py-1 rounded-full transition-colors cursor-pointer ${
        active
          ? 'bg-slate-900 text-white font-semibold'
          : 'bg-slate-100 text-slate-500 font-medium hover:bg-slate-200'
      }`}
    >
      {label}
    </button>
  )
}
