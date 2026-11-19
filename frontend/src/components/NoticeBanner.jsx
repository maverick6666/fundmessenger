import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import handleApiError from '../lib/handleApiError'

/**
 * 공지사항 배너 — 최신 3개를 캐러셀로 표시 (ExecutiveCarousel과 동일 레이아웃)
 */
export default function NoticeBanner({ clubId }) {
  const navigate = useNavigate()
  const [notices, setNotices] = useState([])
  const [loading, setLoading] = useState(true)
  const [idx, setIdx] = useState(0)

  useEffect(() => {
    if (!clubId) return
    api.get(`/clubs/${clubId}/home/notices?size=3`)
      .then(data => setNotices(data || []))
      .catch(e => handleApiError(e, '공지사항 조회'))
      .finally(() => setLoading(false))
  }, [clubId])

  // 자동 슬라이드 (5초)
  useEffect(() => {
    if (notices.length <= 1) return
    const timer = setInterval(() => setIdx(prev => (prev + 1) % notices.length), 5000)
    return () => clearInterval(timer)
  }, [notices.length])

  const goPrev = useCallback(() => {
    setIdx(prev => (prev - 1 + notices.length) % notices.length)
  }, [notices.length])

  const goNext = useCallback(() => {
    setIdx(prev => (prev + 1) % notices.length)
  }, [notices.length])

  if (loading) return <div className="flex-1 min-w-0 h-[44px] lg:h-auto rounded-xl bg-slate-100 animate-pulse" />

  if (notices.length === 0) {
    return (
      <div className="flex-1 min-w-0 bg-white border border-slate-200 rounded-2xl shadow-sm px-5 py-3 lg:py-0 flex items-center gap-3">
        <span className="shrink-0 w-7 h-7 rounded-lg bg-primary/10 text-primary flex items-center justify-center text-xs">📢</span>
        <p className="text-sm text-slate-400">공지가 없습니다</p>
      </div>
    )
  }

  const notice = notices[idx]
  const hasPrev = notices.length > 1
  const hasNext = notices.length > 1

  return (
    <div
      onClick={() => navigate(`/club/boards?announcement=${notice.id}`)}
      className="flex-1 min-w-0 bg-white border border-slate-200 rounded-2xl shadow-sm px-5 py-3 lg:py-0 flex items-center gap-3 hover:bg-slate-50 cursor-pointer transition-colors relative"
    >
      {/* 좌 화살표 */}
      <button
        onClick={(e) => { e.stopPropagation(); goPrev() }}
        disabled={!hasPrev}
        className={`shrink-0 p-1 rounded-lg transition-colors cursor-pointer ${hasPrev ? 'hover:bg-slate-100 text-slate-500' : 'text-slate-200 cursor-default'}`}
      >
        <ChevronLeft className="w-4 h-4" />
      </button>

      {/* 아이콘 */}
      <span className="shrink-0 w-10 h-10 rounded-full bg-amber-50 flex items-center justify-center text-lg">
        {notice.pinned ? '📌' : '📢'}
      </span>

      {/* 내용 */}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-slate-800 truncate">{notice.title}</p>
        <p className="text-xs text-slate-400 mt-0.5">{formatTime(notice.createdAt)}</p>
      </div>

      {/* 우 화살표 */}
      <button
        onClick={(e) => { e.stopPropagation(); goNext() }}
        disabled={!hasNext}
        className={`shrink-0 p-1 rounded-lg transition-colors cursor-pointer ${hasNext ? 'hover:bg-slate-100 text-slate-500' : 'text-slate-200 cursor-default'}`}
      >
        <ChevronRight className="w-4 h-4" />
      </button>

      {/* 페이지 인디케이터 */}
      {notices.length > 1 && (
        <div className="shrink-0 flex flex-col gap-1">
          {notices.map((_, i) => (
            <span
              key={i}
              className={`w-1.5 h-1.5 rounded-full ${i === idx ? 'bg-amber-500' : 'bg-slate-200'}`}
            />
          ))}
        </div>
      )}
    </div>
  )
}
