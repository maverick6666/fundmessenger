import { useState, useMemo } from 'react'
import { ArrowLeft, Plus, Pin, Calendar, CalendarDays, Trash2 } from 'lucide-react'
import useMessagingStore from '../store/messagingStore'
import useClubStore from '../store/clubStore'
import handleApiError from '../lib/handleApiError'
import DateTimePicker, { createDefaultParts, formatDateTimeSummary } from './DateTimePicker'

export default function AnnouncementOverlay() {
  const closeOverlay = useMessagingStore(s => s.closeOverlay)
  const announcements = useMessagingStore(s => s.announcements)
  const createAnnouncement = useMessagingStore(s => s.createAnnouncement)
  const deleteAnnouncement = useMessagingStore(s => s.deleteAnnouncement)
  const togglePin = useMessagingStore(s => s.togglePin)
  const clubInfo = useClubStore(s => s.clubInfo)
  const clubId = clubInfo?.clubId
  const isExec = clubInfo?.isExecutive

  const [showForm, setShowForm] = useState(false)
  const [expanded, setExpanded] = useState({})

  const pinned = announcements.filter(a => a.pinned)
  const regular = announcements.filter(a => !a.pinned)

  const toggleExpand = (id) => setExpanded(s => ({ ...s, [id]: !s[id] }))

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* 헤더 */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-slate-100">
        <div className="flex items-center gap-2">
          <button onClick={closeOverlay} className="p-1 hover:bg-slate-100 rounded cursor-pointer">
            <ArrowLeft className="w-4 h-4 text-slate-500" />
          </button>
          <span className="text-sm font-semibold text-slate-900">공지사항</span>
        </div>
        {isExec && (
          <button
            onClick={() => setShowForm(true)}
            className="flex items-center gap-1 text-xs text-primary hover:text-primary/80 cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />
            공지
          </button>
        )}
      </div>

      {/* 폼이 열리면 폼만 표시 */}
      {showForm ? (
        <AnnouncementForm
          clubId={clubId}
          onSave={async (req) => {
            await createAnnouncement(clubId, req)
            setShowForm(false)
          }}
          onCancel={() => setShowForm(false)}
        />
      ) : (
      /* 목록 */
      <div className="flex-1 overflow-y-auto">
        {/* 고정 공지 */}
        {pinned.length > 0 && (
          <div className="border-b border-slate-100">
            {pinned.map(a => (
              <AnnouncementCard
                key={a.id}
                a={a}
                expanded={expanded[a.id]}
                onToggleExpand={() => toggleExpand(a.id)}
                isExec={isExec}
                onPin={() => togglePin(clubId, a.id)}
                onDelete={() => deleteAnnouncement(clubId, a.id)}
              />
            ))}
          </div>
        )}

        {/* 일반 공지 */}
        {regular.map(a => (
          <AnnouncementCard
            key={a.id}
            a={a}
            expanded={expanded[a.id]}
            onToggleExpand={() => toggleExpand(a.id)}
            isExec={isExec}
            onPin={() => togglePin(clubId, a.id)}
            onDelete={() => deleteAnnouncement(clubId, a.id)}
          />
        ))}

        {announcements.length === 0 && (
          <div className="text-center text-sm text-slate-400 py-8">공지가 없습니다</div>
        )}
      </div>
      )}
    </div>
  )
}

function AnnouncementCard({ a, expanded, onToggleExpand, isExec, onPin, onDelete }) {
  return (
    <div className="px-3 py-3 border-b border-slate-50 hover:bg-slate-50/50">
      <div className="flex items-start justify-between">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            {a.pinned && <Pin className="w-3 h-3 text-orange-500 flex-shrink-0" />}
            <h4 className="text-sm font-semibold text-slate-900 truncate">{a.title}</h4>
          </div>
          <div className="flex items-center gap-2 mt-0.5">
            <span className="text-xs text-slate-400">{a.createdByName}</span>
            <span className="text-xs text-slate-300">·</span>
            <span className="text-xs text-slate-400">
              {new Date(a.createdAt).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })}
            </span>
            {a.eventDate && (
              <>
                <span className="text-xs text-slate-300">·</span>
                <span className="text-xs text-blue-500 flex items-center gap-0.5">
                  <Calendar className="w-2.5 h-2.5" />
                  {new Date(a.eventDate).toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                </span>
              </>
            )}
          </div>
        </div>

        {isExec && (
          <div className="flex items-center gap-0.5 flex-shrink-0 ml-2">
            <button onClick={onPin} className="p-1 hover:bg-slate-100 rounded cursor-pointer" title="고정 토글">
              <Pin className={`w-3 h-3 ${a.pinned ? 'text-orange-500' : 'text-slate-400'}`} />
            </button>
            <button onClick={onDelete} className="p-1 hover:bg-red-50 rounded cursor-pointer" title="삭제">
              <Trash2 className="w-3 h-3 text-slate-400 hover:text-red-500" />
            </button>
          </div>
        )}
      </div>

      {/* 본문 */}
      {a.content && (
        <div className="mt-2 relative">
          <div className={`text-sm text-slate-700 whitespace-pre-wrap ${!expanded ? 'max-h-[80px] overflow-hidden' : ''}`}>
            {a.content}
          </div>
          {!expanded && a.content.length > 100 && (
            <div className="absolute bottom-0 left-0 right-0 h-8 bg-gradient-to-t from-white pointer-events-none" />
          )}
          {a.content.length > 100 && (
            <button
              onClick={onToggleExpand}
              className="text-xs text-primary mt-1 cursor-pointer hover:text-primary/80"
            >
              {expanded ? '접기' : '펼치기'}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function AnnouncementForm({ clubId, onSave, onCancel }) {
  const defaults = useMemo(() => createDefaultParts(), [])
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [hasEventDate, setHasEventDate] = useState(false)
  const [showDatePicker, setShowDatePicker] = useState(false)
  const [eventDateParts, setEventDateParts] = useState(defaults)
  const [reminderDays, setReminderDays] = useState({ 7: false, 3: false, 1: false })
  const [pinned, setPinned] = useState(false)
  const [saving, setSaving] = useState(false)

  const toggleReminder = (d) => setReminderDays(prev => ({ ...prev, [d]: !prev[d] }))

  const eventDateValue = hasEventDate
    ? `${eventDateParts.date}T${eventDateParts.hour}:${eventDateParts.minute}`
    : null

  const handleSubmit = async () => {
    if (!title.trim()) return
    setSaving(true)
    try {
      const selectedReminders = Object.entries(reminderDays).filter(([, v]) => v).map(([k]) => Number(k))
      await onSave({
        title: title.trim(),
        content: content.trim(),
        eventDate: eventDateValue,
        reminderDaysBefore: hasEventDate ? selectedReminders : [],
        pinned,
        banner: false,
      })
    } catch (e) {
      handleApiError(e, '공지사항 저장')
      setSaving(false)
    }
  }

  // 날짜 설정 뷰
  if (showDatePicker) {
    return (
      <DateTimePicker
        initialDate={eventDateParts.date}
        initialHour={eventDateParts.hour}
        initialMinute={eventDateParts.minute}
        title="일정 날짜/시간 설정"
        clearLabel="일정 없음"
        reminderDays={reminderDays}
        onApply={({ date, hour, minute, reminders }) => {
          setEventDateParts({ date, hour, minute })
          if (reminders) setReminderDays(reminders)
          setHasEventDate(true)
          setShowDatePicker(false)
        }}
        onClear={() => {
          setHasEventDate(false)
          setEventDateParts(defaults)
          setReminderDays({ 7: false, 3: false, 1: false })
          setShowDatePicker(false)
        }}
        onBack={() => setShowDatePicker(false)}
        allowPast
      />
    )
  }

  return (
    <div className="flex-1 overflow-y-auto px-3 py-3 border-b border-slate-200 bg-blue-50/30 space-y-2">
      <input
        value={title}
        onChange={e => setTitle(e.target.value)}
        placeholder="공지 제목"
        className="w-full text-sm font-medium bg-white rounded-lg px-3 py-2 outline-none focus:ring-1 focus:ring-primary/30"
      />
      <textarea
        value={content}
        onChange={e => setContent(e.target.value)}
        placeholder="내용 (선택)"
        rows={3}
        className="w-full text-sm bg-white rounded-lg px-3 py-2 outline-none focus:ring-1 focus:ring-primary/30 resize-none"
      />

      {/* 옵션 */}
      <div className="space-y-2">
        <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
          <input type="checkbox" checked={pinned} onChange={e => setPinned(e.target.checked)} className="rounded" />
          📌 고정 공지
        </label>

        {/* 일정 설정 버튼 */}
        <button
          type="button"
          onClick={() => setShowDatePicker(true)}
          className="flex w-full items-center justify-between rounded-lg border border-slate-200 bg-white px-3 py-2 text-left hover:border-primary/20 cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <CalendarDays className="h-3.5 w-3.5 text-slate-400" />
            <div>
              <p className="text-xs font-medium text-slate-700">일정</p>
              <p className="text-xs text-slate-400">{hasEventDate ? formatDateTimeSummary(eventDateValue) : '설정 없음'}</p>
            </div>
          </div>
          <span className="text-xs text-primary font-medium">{hasEventDate ? '수정' : '추가'}</span>
        </button>

      </div>

      <div className="flex justify-end gap-2 pt-1">
        <button onClick={onCancel} className="text-xs text-slate-500 px-3 py-1.5 cursor-pointer">취소</button>
        <button
          onClick={handleSubmit}
          disabled={!title.trim() || saving}
          className="text-xs text-white bg-primary px-4 py-1.5 rounded-lg cursor-pointer disabled:opacity-50"
        >
          {saving ? '저장중...' : '공지 등록'}
        </button>
      </div>
    </div>
  )
}
