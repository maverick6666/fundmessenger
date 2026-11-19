import { useEffect, useMemo, useState } from 'react'
import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import { Markdown } from 'tiptap-markdown'
import { CalendarDays } from 'lucide-react'
import { createPortal } from 'react-dom'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import ConfirmModal from './ConfirmModal'
import DateTimePicker, { createDefaultParts, formatDateTimeSummary } from './DateTimePicker'
import { useUnsavedChanges, useUnsavedChangesGuard } from './UnsavedChangesProvider'

export default function AnnouncementEditor({ clubId, announcementId, onBack, onSaved }) {
  const isNew = !announcementId
  const { confirmIfDirty } = useUnsavedChanges()

  const [title, setTitle] = useState('')
  const [isPinned, setIsPinned] = useState(false)
  const [hasEventDate, setHasEventDate] = useState(false)
  const [eventDateParts, setEventDateParts] = useState(() => createDefaultParts())
  const [showDatePicker, setShowDatePicker] = useState(false)
  const [reminderDays, setReminderDays] = useState({ 7: false, 3: false, 1: false })
  const [content, setContent] = useState('')
  const [initialSnapshot, setInitialSnapshot] = useState({
    title: '',
    isPinned: false,
    hasEventDate: false,
    eventDateKey: '',
    reminderDaysKey: '',
    content: '',
  })
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(!isNew)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  const editor = useEditor({
    extensions: [
      StarterKit,
      Placeholder.configure({ placeholder: '공지 내용을 입력하세요...' }),
      Markdown.configure({ html: true, transformPastedText: true, transformCopiedText: true }),
    ],
    editorProps: {
      attributes: { class: 'prose prose-sm max-w-none focus:outline-none' },
    },
    onUpdate: ({ editor: nextEditor }) => {
      setContent(nextEditor.storage.markdown.getMarkdown())
    },
  })

  // 수정 모드: 기존 데이터 로드
  const reminderDaysKey = useMemo(() => (
    Object.entries(reminderDays)
      .filter(([, checked]) => checked)
      .map(([day]) => day)
      .sort()
      .join(',')
  ), [reminderDays])

  const eventDateKey = hasEventDate ? `${eventDateParts.date}T${eventDateParts.hour}:${eventDateParts.minute}` : ''
  const eventDateValue = hasEventDate ? eventDateKey : null

  const isDirty = !saving && (
    title !== initialSnapshot.title ||
    isPinned !== initialSnapshot.isPinned ||
    hasEventDate !== initialSnapshot.hasEventDate ||
    eventDateKey !== initialSnapshot.eventDateKey ||
    reminderDaysKey !== initialSnapshot.reminderDaysKey ||
    content !== initialSnapshot.content
  )

  useUnsavedChangesGuard(isDirty)

  useEffect(() => {
    if (isNew || !clubId || !announcementId) return
    setLoading(true)
    api.get(`/clubs/${clubId}/announcements/${announcementId}`)
      .then(data => {
        setTitle(data.title || '')
        setIsPinned(data.pinned || false)
        setHasEventDate(!!data.eventDate)
        if (data.eventDate) {
          const dt = data.eventDate.substring(0, 16) // "2026-03-19T15:00"
          const [d, t] = dt.split('T')
          const [h, m] = (t || '00:00').split(':')
          setEventDateParts({ date: d, hour: h || '00', minute: m || '00' })
        }
        const days = { 7: false, 3: false, 1: false }
        ;(data.reminderDaysBefore || []).forEach(d => { if (d in days) days[d] = true })
        setReminderDays(days)
        const initialContent = data.content || ''
        if (editor) {
          editor.commands.setContent(initialContent)
        }
        setContent(initialContent)
        const edKey = data.eventDate ? data.eventDate.substring(0, 16) : ''
        setInitialSnapshot({
          title: data.title || '',
          isPinned: data.pinned || false,
          hasEventDate: !!data.eventDate,
          eventDateKey: edKey,
          reminderDaysKey: (data.reminderDaysBefore || []).slice().sort().join(','),
          content: initialContent,
        })
      })
      .catch(e => handleApiError(e, '공지 조회'))
      .finally(() => setLoading(false))
  }, [clubId, announcementId, isNew, editor])

  useEffect(() => {
    if (!isNew || !editor) return

    editor.commands.clearContent()
    setContent('')
    setInitialSnapshot({
      title: '',
      isPinned: false,
      hasEventDate: false,
      eventDateKey: '',
      reminderDaysKey: '',
      content: '',
    })
  }, [editor, isNew])

  const handleSave = async () => {
    if (!title.trim()) {
      setAlertModal({ open: true, message: '제목을 입력하세요' })
      return
    }

    setSaving(true)
    try {
      const markdownContent = editor?.storage?.markdown?.getMarkdown() || ''
      const selectedReminders = Object.entries(reminderDays)
        .filter(([, v]) => v)
        .map(([k]) => Number(k))

      const body = {
        title: title.trim(),
        content: markdownContent,
        pinned: isPinned,
        banner: false,
        eventDate: eventDateValue,
        reminderDaysBefore: hasEventDate ? selectedReminders : [],
      }

      if (isNew) {
        await api.post(`/clubs/${clubId}/announcements`, body)
      } else {
        await api.put(`/clubs/${clubId}/announcements/${announcementId}`, body)
      }
      onSaved()
    } catch (e) {
      setAlertModal({ open: true, message: e.message || '저장 실패' })
    } finally {
      setSaving(false)
    }
  }

  const toggleReminder = (day) => {
    setReminderDays(prev => ({ ...prev, [day]: !prev[day] }))
  }

  if (loading) {
    return <div className="p-6 text-sm text-slate-400 bg-white rounded-2xl border border-slate-200 shadow-sm">로딩 중...</div>
  }

  return (
    <div className="flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* 상단 액션 */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-slate-200">
        <button onClick={() => confirmIfDirty(onBack)} className="text-sm text-slate-500 hover:text-slate-700 cursor-pointer">
          취소
        </button>
        <button
          onClick={handleSave}
          disabled={saving}
          className="px-4 py-1.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50 transition-colors cursor-pointer"
        >
          {saving ? '저장 중...' : isNew ? '공지 등록' : '수정'}
        </button>
      </div>

      {/* 제목 */}
      <div className="px-6 pt-4 pb-2">
        <input
          type="text"
          value={title}
          onChange={e => setTitle(e.target.value)}
          placeholder="공지 제목을 입력하세요"
          className="w-full text-xl font-bold text-slate-900 border-b border-slate-200 pb-3 focus:outline-none focus:border-blue-400"
        />
      </div>

      {/* 옵션 영역 */}
      <div className="px-6 py-3 space-y-3 border-b border-slate-100">
        {/* 고정 토글 */}
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={isPinned}
              onChange={e => setIsPinned(e.target.checked)}
              className="rounded border-slate-300"
            />
            📌 고정 공지
          </label>
        </div>

        {/* 일정 설정 버튼 */}
        <button
          type="button"
          onClick={() => setShowDatePicker(true)}
          className="flex w-full items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-left hover:border-primary/20 hover:bg-white cursor-pointer"
        >
          <div className="flex items-center gap-3">
            <CalendarDays className="h-4 w-4 text-slate-400" />
            <div>
              <p className="text-sm font-medium text-slate-700">📅 일정</p>
              <p className="text-xs text-slate-400">{hasEventDate ? formatDateTimeSummary(eventDateValue) : '설정 없음'}</p>
            </div>
          </div>
          <span className="text-xs text-primary font-medium">{hasEventDate ? '수정' : '추가'}</span>
        </button>

      </div>

      {/* 에디터 */}
      <div className="px-6 py-4 min-h-[200px]">
        <EditorContent editor={editor} />
      </div>

      {/* 날짜/시간 선택 모달 */}
      {showDatePicker && createPortal(
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40" onClick={() => setShowDatePicker(false)}>
          <div className="w-full max-w-sm h-[520px] bg-white rounded-2xl shadow-xl overflow-hidden" onClick={e => e.stopPropagation()}>
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
                setEventDateParts(createDefaultParts())
                setReminderDays({ 7: false, 3: false, 1: false })
                setShowDatePicker(false)
              }}
              onBack={() => setShowDatePicker(false)}
              allowPast
            />
          </div>
        </div>,
        document.body
      )}

      {/* 알림 모달 */}
      {alertModal.open && (
        <ConfirmModal
          title="알림"
          message={alertModal.message}
          onConfirm={() => setAlertModal({ open: false, message: '' })}
          onClose={() => setAlertModal({ open: false, message: '' })}
          confirmLabel="확인"
          cancelLabel=""
        />
      )}
    </div>
  )
}
