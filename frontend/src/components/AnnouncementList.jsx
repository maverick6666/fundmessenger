import { useEffect, useState } from 'react'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import { PostSkeleton } from './SkeletonRow'
import handleApiError from '../lib/handleApiError'

export default function AnnouncementList({ clubId, isExecutive, onAnnouncementClick, onWrite }) {
  const [announcements, setAnnouncements] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!clubId) return
    setLoading(true)
    api.get(`/clubs/${clubId}/announcements`)
      .then(data => setAnnouncements(data || []))
      .catch(e => handleApiError(e, '공지사항 목록 조회'))
      .finally(() => setLoading(false))
  }, [clubId])

  const pinned = announcements.filter(a => a.pinned)
  const regular = announcements.filter(a => !a.pinned)

  return (
    <div className="flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* 상단 바 */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
        <h3 className="text-base font-semibold text-slate-900">공지사항</h3>
        {isExecutive && onWrite && (
          <button
            onClick={onWrite}
            className="px-4 py-1.5 bg-primary text-white text-sm rounded-lg hover:bg-primary-hover transition-colors cursor-pointer"
          >
            공지 작성
          </button>
        )}
      </div>

      {/* 목록 */}
      <div>
        {loading ? (
          <PostSkeleton count={3} />
        ) : announcements.length === 0 ? (
          <div className="p-6 text-sm text-slate-400">공지사항이 없습니다</div>
        ) : (
          <>
            {pinned.map(a => (
              <AnnouncementRow key={a.id} announcement={a} onClick={() => onAnnouncementClick(a.id)} />
            ))}
            {pinned.length > 0 && regular.length > 0 && (
              <div className="border-t border-slate-100" />
            )}
            {regular.map(a => (
              <AnnouncementRow key={a.id} announcement={a} onClick={() => onAnnouncementClick(a.id)} />
            ))}
          </>
        )}
      </div>
    </div>
  )
}

function AnnouncementRow({ announcement, onClick }) {
  const a = announcement
  return (
    <button
      onClick={onClick}
      className="w-full text-left px-6 py-3 border-b border-slate-50 hover:bg-slate-50 transition-colors cursor-pointer"
    >
      <div className="flex items-center gap-2">
        {a.pinned && <span className="text-xs shrink-0">📌</span>}
        <span className="font-medium text-sm text-slate-900 truncate flex-1">{a.title}</span>
        {a.eventDate && (
          <span className="shrink-0 px-2 py-0.5 text-xs rounded bg-blue-50 text-blue-600 flex items-center gap-1">
            📅 {a.eventDate}
          </span>
        )}
      </div>
      <div className="mt-1 text-xs text-slate-400 flex items-center gap-2">
        <span>{a.createdByName}</span>
        <span>·</span>
        <span>{formatTime(a.createdAt)}</span>
        {a.banner && <span className="text-orange-500">배너</span>}
      </div>
    </button>
  )
}
