import { useEffect, useState, useRef } from 'react'
import { useOutletContext } from 'react-router-dom'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'

export default function DriveSidebar({ selection, onSelect }) {
  const { clubInfo } = useOutletContext()
  const [sidebar, setSidebar] = useState(null)
  const [expanded, setExpanded] = useState(false)
  const [showUnassigned, setShowUnassigned] = useState(false)
  const listRef = useRef(null)
  const [isOverflowing, setIsOverflowing] = useState(false)

  useEffect(() => {
    if (!clubInfo?.clubId) return
    api.get(`/clubs/${clubInfo.clubId}/drive/sidebar`).then(setSidebar).catch(e => handleApiError(e, '드라이브 사이드바 조회'))
  }, [clubInfo?.clubId])

  useEffect(() => {
    if (!listRef.current) return
    const el = listRef.current
    setIsOverflowing(el.scrollHeight > el.clientHeight)
  }, [sidebar, expanded])

  if (!sidebar) return <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4">로딩...</div>

  const isActive = (type, id) => selection.type === type && selection.id === id
  const myTeamIds = new Set((clubInfo?.myTeams || []).map(t => t.teamId))

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col overflow-hidden sticky top-[calc(var(--header-h)+1rem)] md:top-[calc(var(--header-h)+1.5rem)] h-[calc(100vh-var(--header-h)-2rem)] md:h-[calc(100vh-var(--header-h)-3rem)]">
      {/* 헤더 — 항상 고정 */}
      <div className="p-4 pb-2 shrink-0">
        <h2 className="text-lg font-bold text-slate-900 scandi-heading">드라이브</h2>
      </div>

      {/* 목록 영역 */}
      <div
        ref={listRef}
        className={`flex-1 min-h-0 ${expanded ? 'overflow-y-auto' : 'overflow-hidden'}`}
      >
        {/* 전체 섹션 */}
        <div className="px-3 pt-2">
          <div className="text-xs font-medium text-slate-400 px-2 pb-1">전체</div>
          <SidebarItem
            label="전체 파일"
            icon="📁"
            active={isActive('all', null)}
            onClick={() => onSelect({ type: 'all', id: null, name: '전체 파일' })}
          />
        </div>

        <div className="mx-3 my-2 border-t border-slate-100" />

        {/* 팀별 섹션 */}
        <div className="px-3">
          <div className="text-xs font-medium text-slate-400 px-2 pb-1">팀별</div>
          {sidebar.teams.filter(t => t.id != null).map(t => (
            <SidebarItem
              key={`team-${t.id}`}
              label={t.name}
              icon="👥"
              active={isActive('team', t.id)}
              highlight={myTeamIds.has(t.id)}
              count={t.fileCount}
              onClick={() => onSelect({ type: 'team', id: t.id, name: t.name, description: t.description, memberCount: t.memberCount })}
            />
          ))}
          {/* 자유 — 꺽쇠로 펼치면 아래에 SidebarItem 노출 */}
          {sidebar.teams.some(t => t.id == null) && (() => {
            const unassigned = sidebar.teams.find(t => t.id == null)
            return (
              <>
                <button
                  onClick={() => setShowUnassigned(prev => !prev)}
                  className="w-full flex items-center gap-2 px-3 py-2 rounded-md text-sm text-slate-400 hover:bg-slate-50 transition-colors cursor-pointer"
                >
                  <span className="text-xs transition-transform" style={{ transform: showUnassigned ? 'rotate(90deg)' : 'rotate(0deg)' }}>▶</span>
                  <span className="flex-1 text-left text-xs font-medium">기타</span>
                </button>
                {showUnassigned && (
                  <div className="pl-3">
                    <SidebarItem
                      label={unassigned.name}
                      icon="📂"
                      active={isActive('unassigned', null)}
                      count={unassigned.fileCount}
                      onClick={() => onSelect({ type: 'unassigned', id: null, name: '자유' })}
                    />
                  </div>
                )}
              </>
            )
          })()}
        </div>

        {/* 폴더별 섹션 */}
        {sidebar.folders && sidebar.folders.length > 0 && (
          <>
            <div className="mx-3 my-2 border-t border-slate-100" />
            <div className="px-3">
              <div className="text-xs font-medium text-slate-400 px-2 pb-1">폴더별</div>
              {sidebar.folders.map(f => (
                <SidebarItem
                  key={`folder-${f.id}`}
                  label={f.name}
                  icon="📂"
                  active={isActive('folder', f.id)}
                  count={f.fileCount}
                  onClick={() => onSelect({ type: 'folder', id: f.id, name: f.name, description: f.description })}
                />
              ))}
            </div>
          </>
        )}

        <div className="mx-3 my-2 border-t border-slate-100" />

        {/* 태그별 섹션 */}
        <div className="px-3 pb-4">
          <div className="text-xs font-medium text-slate-400 px-2 pb-1">태그별</div>
          {sidebar.tags.map(tag => (
            <SidebarItem
              key={`tag-${tag.id}`}
              label={tag.name}
              icon="🏷️"
              active={isActive('tag', tag.id)}
              onClick={() => onSelect({ type: 'tag', id: tag.id, name: tag.name })}
            />
          ))}
          {sidebar.tags.length === 0 && (
            <div className="px-2 py-1 text-xs text-slate-400">태그가 없습니다</div>
          )}
        </div>
      </div>

      {/* 더보기/접기 버튼 */}
      {(isOverflowing || expanded) && (
        <button
          onClick={() => setExpanded(prev => !prev)}
          className="shrink-0 py-2 text-xs text-blue-500 hover:text-blue-700 font-medium text-center border-t border-slate-100 cursor-pointer"
        >
          {expanded ? '접기 ▲' : '더보기 ▼'}
        </button>
      )}
    </div>
  )
}

function SidebarItem({ label, icon, active, highlight, count, onClick }) {
  return (
    <button
      onClick={onClick}
      className={`w-full flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors cursor-pointer ${
        active
          ? 'bg-blue-50 text-blue-600 font-semibold'
          : highlight
            ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
            : 'text-slate-500 hover:bg-slate-50'
      }`}
    >
      <span className="text-xs">{icon}</span>
      <span className="flex-1 text-left truncate">{label}</span>
      {count != null && count > 0 && (
        <span className="text-xs text-slate-400">{count}</span>
      )}
    </button>
  )
}
