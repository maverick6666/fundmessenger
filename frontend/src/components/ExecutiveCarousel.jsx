import { useEffect, useState } from 'react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'

/**
 * 임원 캐러셀 — GET /home/executives
 * 아바타(이니셜) + 이름 + 직책 + 한줄소개
 * 여러 명이면 좌우 전환 버튼
 * 편집 권한자: 연필 아이콘 클릭 → roleName / bio 인라인 편집
 */
export default function ExecutiveCarousel({ clubId, clubInfo }) {
  const [executives, setExecutives] = useState([])
  const [index, setIndex] = useState(0)
  const [loading, setLoading] = useState(true)

  // 인라인 편집 (캐러셀 전용 소개글)
  const [editing, setEditing] = useState(false)
  const [editBio, setEditBio] = useState('')
  const [saving, setSaving] = useState(false)

  const fetchExecutives = () => {
    if (!clubId) return
    api.get(`/clubs/${clubId}/home/executives`)
      .then(data => setExecutives(data || []))
      .catch(e => handleApiError(e, '임원 정보 조회'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchExecutives()
  }, [clubId])

  if (loading) return <div className="w-full lg:w-[455px] lg:shrink-0 h-[44px] lg:h-auto rounded-xl bg-slate-100 animate-pulse" />
  if (executives.length === 0) return null

  const current = executives[index]
  const hasPrev = index > 0
  const hasNext = index < executives.length - 1

  // 편집 권한: 본인 카드만 (캐러셀 전용 소개글 수정)
  const canEdit = current?.id === clubInfo?.membershipId

  const startEdit = () => {
    setEditBio(current.bio || '')
    setEditing(true)
  }

  const cancelEdit = () => {
    setEditing(false)
  }

  const saveEdit = async () => {
    setSaving(true)
    try {
      await api.put(`/clubs/${clubId}/home/carousel/${current.carouselId}/bio`, {
        bio: editBio,
      })
      setEditing(false)
      fetchExecutives()
    } catch (err) {
      handleApiError(err, '캐러셀 소개글 수정')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="w-full lg:w-[455px] lg:shrink-0 bg-white rounded-2xl border border-slate-200 shadow-sm px-5 py-3 lg:py-0 flex items-center gap-3 relative">
      {/* 좌 화살표 */}
      <button
        onClick={() => { setIndex(i => i - 1); setEditing(false) }}
        disabled={!hasPrev}
        className={`shrink-0 p-1 rounded-lg transition-colors cursor-pointer ${hasPrev ? 'hover:bg-slate-100 text-slate-500' : 'text-slate-200 cursor-default'}`}
      >
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
      </button>

      {/* 아바타 */}
      <div className="shrink-0 w-10 h-10 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-600 text-base font-bold">
        {current.avatarUrl ? (
          <img src={current.avatarUrl} alt="" className="w-full h-full rounded-full object-cover" />
        ) : (
          current.name?.charAt(0) || '?'
        )}
      </div>

      {/* 정보 영역 */}
      {editing ? (
        <div className="flex-1 min-w-0 flex flex-col gap-1.5 py-1">
          <div className="flex items-center gap-2">
            <span className="text-sm font-bold text-slate-900">{current.name}</span>
            {current.roleName && (
              <span className="text-xs px-2 py-0.5 bg-blue-50 text-blue-600 rounded-full font-medium">
                {current.firstGeneration ? `${current.firstGeneration} ` : ''}{current.roleName}
              </span>
            )}
          </div>
          <input
            type="text"
            value={editBio}
            onChange={e => setEditBio(e.target.value)}
            placeholder="캐러셀 소개글을 입력하세요"
            className="text-xs px-2 py-1 border border-slate-200 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-300 w-full"
            maxLength={200}
          />
          <div className="flex gap-1.5">
            <button
              onClick={saveEdit}
              disabled={saving}
              className="text-xs px-2 py-0.5 bg-primary text-white rounded hover:bg-primary-hover transition-colors cursor-pointer disabled:opacity-50"
            >
              {saving ? '저장중...' : '저장'}
            </button>
            <button
              onClick={cancelEdit}
              className="text-xs px-2 py-0.5 bg-slate-100 text-slate-600 rounded hover:bg-slate-200 transition-colors cursor-pointer"
            >
              취소
            </button>
          </div>
        </div>
      ) : (
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-bold text-slate-900">{current.name}</span>
            {current.roleName && (
              <span className="text-xs px-2 py-0.5 bg-blue-50 text-blue-600 rounded-full font-medium">
                {current.firstGeneration ? `${current.firstGeneration} ` : ''}{current.roleName}
              </span>
            )}
          </div>
          {current.bio ? (
            <p className="text-xs text-slate-500 mt-0.5 truncate">{current.bio}</p>
          ) : (
            <p className="text-xs text-slate-400 mt-0.5">한줄 소개가 없습니다</p>
          )}
          {current.phone && (
            <p className="text-xs text-slate-400 mt-0.5">{current.phone}</p>
          )}
        </div>
      )}

      {/* 편집 아이콘 — canEdit이고 편집 모드가 아닐 때 */}
      {canEdit && !editing && (
        <button
          onClick={startEdit}
          className="shrink-0 p-1 rounded-lg text-slate-300 hover:text-slate-500 hover:bg-slate-50 transition-colors cursor-pointer"
          title="프로필 수정"
        >
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
          </svg>
        </button>
      )}

      {/* 우 화살표 */}
      <button
        onClick={() => { setIndex(i => i + 1); setEditing(false) }}
        disabled={!hasNext}
        className={`shrink-0 p-1 rounded-lg transition-colors cursor-pointer ${hasNext ? 'hover:bg-slate-100 text-slate-500' : 'text-slate-200 cursor-default'}`}
      >
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
      </button>

      {/* 페이지 인디케이터 */}
      {executives.length > 1 && (
        <div className="shrink-0 flex flex-col gap-1">
          {executives.map((_, i) => (
            <span
              key={i}
              className={`w-1.5 h-1.5 rounded-full ${i === index ? 'bg-blue-500' : 'bg-slate-200'}`}
            />
          ))}
        </div>
      )}
    </div>
  )
}
