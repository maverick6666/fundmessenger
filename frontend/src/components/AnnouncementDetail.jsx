import { useEffect, useState } from 'react'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import NewsMarkdownRenderer from './NewsMarkdownRenderer'
import ConfirmModal from './ConfirmModal'
import handleApiError from '../lib/handleApiError'

export default function AnnouncementDetail({ clubId, announcementId, isExecutive, onBack, onEdit }) {
  const [announcement, setAnnouncement] = useState(null)
  const [loading, setLoading] = useState(true)
  const [deleteConfirm, setDeleteConfirm] = useState(false)

  useEffect(() => {
    if (!clubId || !announcementId) return
    setLoading(true)
    api.get(`/clubs/${clubId}/announcements/${announcementId}`)
      .then(data => setAnnouncement(data))
      .catch(e => handleApiError(e, '공지사항 상세 조회'))
      .finally(() => setLoading(false))
  }, [clubId, announcementId])

  const handleDelete = async () => {
    try {
      await api.delete(`/clubs/${clubId}/announcements/${announcementId}`)
      onBack()
    } catch (e) {
      handleApiError(e, '공지사항 삭제')
    }
  }

  const handleTogglePin = async () => {
    try {
      await api.put(`/clubs/${clubId}/announcements/${announcementId}/pin`)
      setAnnouncement(prev => ({ ...prev, pinned: !prev.pinned }))
    } catch (e) {
      handleApiError(e, '고정 토글')
    }
  }

  if (loading) {
    return <div className="p-6 text-sm text-slate-400 bg-white rounded-2xl border border-slate-200 shadow-sm">로딩 중...</div>
  }
  if (!announcement) {
    return <div className="p-6 text-sm text-slate-400 bg-white rounded-2xl border border-slate-200 shadow-sm">공지를 찾을 수 없습니다</div>
  }

  const a = announcement

  return (
    <div className="flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* 상단 액션 */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-slate-200">
        <button onClick={onBack} className="text-sm text-slate-500 hover:text-slate-700 cursor-pointer">
          ← 목록
        </button>
        {isExecutive && (
          <div className="flex items-center gap-2">
            <button
              onClick={handleTogglePin}
              className={`px-3 py-1.5 text-xs rounded-lg border cursor-pointer transition-colors ${
                a.pinned
                  ? 'bg-orange-50 text-orange-600 border-orange-200 hover:bg-orange-100'
                  : 'bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100'
              }`}
            >
              {a.pinned ? '📌 고정 해제' : '📌 고정'}
            </button>
            <button
              onClick={() => onEdit(announcementId)}
              className="px-3 py-1.5 text-xs rounded-lg bg-slate-50 text-slate-600 border border-slate-200 hover:bg-slate-100 cursor-pointer"
            >
              수정
            </button>
            <button
              onClick={() => setDeleteConfirm(true)}
              className="px-3 py-1.5 text-xs rounded-lg bg-red-50 text-red-600 border border-red-200 hover:bg-red-100 cursor-pointer"
            >
              삭제
            </button>
          </div>
        )}
      </div>

      {/* 헤더 */}
      <div className="px-6 py-4 border-b border-slate-100">
        <div className="flex items-center gap-2 mb-2">
          {a.pinned && <span className="px-2 py-0.5 text-xs rounded bg-orange-50 text-orange-600">📌 고정</span>}
          {a.eventDate && (
            <span className="px-2 py-0.5 text-xs rounded bg-blue-50 text-blue-600">📅 {a.eventDate}</span>
          )}
          {a.banner && <span className="px-2 py-0.5 text-xs rounded bg-amber-50 text-amber-600">배너</span>}
        </div>
        <h1 className="text-xl font-bold text-slate-900">{a.title}</h1>
        <div className="mt-2 text-xs text-slate-400 flex items-center gap-2">
          <span>{a.createdByName}</span>
          <span>·</span>
          <span>{formatTime(a.createdAt)}</span>
        </div>
      </div>

      {/* 본문 */}
      <div className="px-6 py-4 min-h-[200px]">
        {a.content ? (
          <NewsMarkdownRenderer content={a.content} />
        ) : (
          <p className="text-sm text-slate-400">내용이 없습니다</p>
        )}
      </div>

      {/* 리마인더 정보 */}
      {a.reminderDaysBefore && a.reminderDaysBefore.length > 0 && (
        <div className="px-6 py-3 border-t border-slate-100">
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <span>🔔 리마인더:</span>
            {a.reminderDaysBefore.map(d => (
              <span key={d} className="px-2 py-0.5 rounded bg-slate-100 text-slate-600">D-{d}</span>
            ))}
          </div>
        </div>
      )}

      {/* 삭제 확인 모달 */}
      {deleteConfirm && (
        <ConfirmModal
          title="공지 삭제"
          message={`"${a.title}"을(를) 삭제하시겠습니까?`}
          confirmLabel="삭제"
          onConfirm={handleDelete}
          onClose={() => setDeleteConfirm(false)}
          danger
        />
      )}
    </div>
  )
}
