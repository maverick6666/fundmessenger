import { useState, useEffect } from 'react'
import { createPortal } from 'react-dom'
import api from '../lib/api'
import useToastStore from '../store/toastStore'
import ConfirmModal from './ConfirmModal'

/**
 * 일정 생성/수정/삭제 모달
 * event가 있으면 수정 모드, 없으면 생성 모드
 */
export default function EventFormModal({ clubId, event, onClose, onSaved, defaultTeamId, teams = [] }) {
  const isEdit = !!event

  const [title, setTitle] = useState('')
  const [eventDate, setEventDate] = useState('')
  const [eventTime, setEventTime] = useState('')
  const [description, setDescription] = useState('')
  const [teamId, setTeamId] = useState(defaultTeamId || '')
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteConfirm, setDeleteConfirm] = useState(false)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  useEffect(() => {
    if (event) {
      setTitle(event.title || '')
      setEventDate(event.eventDate || '')
      setEventTime(event.eventTime?.substring(0, 5) || '') // HH:mm
      setDescription(event.description || '')
      setTeamId(event.teamId || '')
    }
  }, [event])

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!title.trim() || !eventDate) return

    setSaving(true)
    try {
      const body = {
        title: title.trim(),
        eventDate,
        eventTime: eventTime || null,
        description: description.trim() || null,
        teamId: isEdit ? undefined : (teamId ? Number(teamId) : null),
      }

      if (isEdit) {
        await api.put(`/clubs/${clubId}/calendar/events/${event.id}`, body)
        useToastStore.getState().addToast({ type: 'SUCCESS', title: '일정이 수정되었습니다' })
      } else {
        await api.post(`/clubs/${clubId}/calendar/events`, body)
        useToastStore.getState().addToast({ type: 'SUCCESS', title: '일정이 등록되었습니다' })
      }
      onSaved?.()
      onClose()
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    setDeleteConfirm(false)
    setDeleting(true)
    try {
      await api.delete(`/clubs/${clubId}/calendar/events/${event.id}`)
      onSaved?.()
      onClose()
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setDeleting(false)
    }
  }

  const titleOverflow = title.length > 100
  const descOverflow = description.length > 500

  return createPortal(
    <div className="fixed inset-0 z-[60] modal-overlay flex items-center justify-center bg-black/40" onClick={onClose}>
      <div
        className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 p-6"
        onClick={e => e.stopPropagation()}
      >
        <h2 className="text-lg font-bold text-slate-900 mb-4">
          {isEdit ? '일정 수정' : '일정 추가'}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* 제목 */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">제목 *</label>
            <input
              type="text"
              value={title}
              onChange={e => setTitle(e.target.value)}
              className={`w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 ${
                titleOverflow ? 'border-red-300 focus:ring-red-400' : 'border-slate-200 focus:ring-primary/30 focus:border-primary'
              }`}
              placeholder="일정 제목"
              required
            />
            {titleOverflow && (
              <p className="text-xs text-red-500 mt-1">{title.length}/100자 — 최대 길이를 초과했습니다</p>
            )}
          </div>

          {/* 팀 선택 (생성 모드에서만, 팀 목록이 있을 때만) */}
          {!isEdit && teams.length > 0 && (
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">일정 종류</label>
              <select
                value={teamId}
                onChange={e => setTeamId(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary"
              >
                <option value="">동아리 일정</option>
                {teams.map(t => (
                  <option key={t.teamId || t.id} value={t.teamId || t.id}>{t.teamName || t.name} 일정</option>
                ))}
              </select>
            </div>
          )}

          {/* 날짜 + 시간 */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">날짜 *</label>
              <input
                type="date"
                value={eventDate}
                onChange={e => setEventDate(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">시간 (선택)</label>
              <input
                type="time"
                value={eventTime}
                onChange={e => setEventTime(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary"
              />
              {!eventTime && (
                <p className="text-xs text-slate-400 mt-1">비워두면 종일 일정</p>
              )}
            </div>
          </div>

          {/* 설명 */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">설명 (선택)</label>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value)}
              rows={3}
              className={`w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 resize-none ${
                descOverflow ? 'border-red-300 focus:ring-red-400' : 'border-slate-200 focus:ring-primary/30 focus:border-primary'
              }`}
              placeholder="일정 설명"
            />
            {descOverflow && (
              <p className="text-xs text-red-500 mt-1">{description.length}/500자 — 최대 길이를 초과했습니다</p>
            )}
          </div>

          {/* 버튼 */}
          <div className="flex items-center justify-between pt-2">
            {isEdit ? (
              <button
                type="button"
                onClick={() => setDeleteConfirm(true)}
                disabled={deleting}
                className="text-sm text-red-500 hover:text-red-700 transition-colors cursor-pointer disabled:opacity-50"
              >
                {deleting ? '삭제 중...' : '삭제'}
              </button>
            ) : (
              <div />
            )}
            <div className="flex gap-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 text-sm text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors cursor-pointer"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={saving || !title.trim() || !eventDate || titleOverflow || descOverflow}
                className="px-4 py-2 text-sm text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50"
              >
                {saving ? '저장 중...' : isEdit ? '수정' : '추가'}
              </button>
            </div>
          </div>
        </form>

        {deleteConfirm && (
          <ConfirmModal
            title="일정 삭제"
            message={`"${event.title}" 일정을 삭제하시겠습니까?`}
            confirmLabel="삭제"
            onConfirm={handleDelete}
            onClose={() => setDeleteConfirm(false)}
            danger
          />
        )}

        {alertModal.open && (
          <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
        )}
      </div>
    </div>,
    document.body
  )
}
