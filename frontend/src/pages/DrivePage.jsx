import { useState, useEffect } from 'react'
import { useSearchParams, useOutletContext } from 'react-router-dom'
import DriveSidebar from '../components/DriveSidebar'
import MobileSidebarOverlay from '../components/MobileSidebarOverlay'
import DriveFileList from '../components/DriveFileList'
import DriveFileDetail from '../components/DriveFileDetail'

export default function DrivePage() {
  const { clubInfo } = useOutletContext()
  const [searchParams, setSearchParams] = useSearchParams()
  const [sidebarOpen, setSidebarOpen] = useState(true)

  // URL에서 상태 복원
  const fileId = searchParams.get('file')
  const teamId = searchParams.get('team')
  const tagId = searchParams.get('tag')
  const folderId = searchParams.get('folder')
  const unassigned = searchParams.get('unassigned') === 'true'

  // 기본 팀 결정: 회장단(시스템팀) 제외 후 첫 번째 팀
  const defaultTeam = (() => {
    const teams = clubInfo?.myTeams || []
    const nonSystem = teams.filter(t => !t.systemTeam)
    return nonSystem.length > 0 ? nonSystem[0] : null
  })()

  // 사이드바 선택 상태 — URL 파라미터 없으면 기본 팀 선택
  const selection = (() => {
    if (folderId) return { type: 'folder', id: Number(folderId), name: '' }
    if (teamId) return { type: 'team', id: Number(teamId), name: '' }
    if (tagId) return { type: 'tag', id: Number(tagId), name: '' }
    if (unassigned) return { type: 'unassigned', id: null, name: '자유' }
    if (defaultTeam) return { type: 'team', id: defaultTeam.teamId, name: defaultTeam.teamName }
    return { type: 'all', id: null, name: '전체 파일' }
  })()

  const handleSelect = (sel) => {
    const params = {}
    if (sel.type === 'team') params.team = sel.id
    if (sel.type === 'tag') params.tag = sel.id
    if (sel.type === 'folder') params.folder = sel.id
    if (sel.type === 'unassigned') params.unassigned = 'true'
    setSearchParams(params)
  }

  const handleFileClick = (file) => {
    const params = Object.fromEntries(searchParams)
    params.file = file.id
    setSearchParams(params)
  }

  const handleBack = () => {
    const params = Object.fromEntries(searchParams)
    delete params.file
    setSearchParams(params)
  }

  return (
    <div className="flex gap-4 -mx-3 md:-mx-6 -mt-4 md:-mt-6 -mb-24 md:-mb-40 px-3 md:px-6 pt-4 md:pt-6 min-h-[calc(100vh-var(--header-h))] animate-fade-in-up">
      {/* 사이드바: 파일 상세에서 접기/펼치기 토글 */}
      {sidebarOpen && (
        <MobileSidebarOverlay>
          <DriveSidebar
            selection={selection}
            onSelect={handleSelect}
          />
        </MobileSidebarOverlay>
      )}

      <div className="flex-1 min-w-0 pb-48 md:pb-64">
        {fileId ? (
          <DriveFileDetail
            fileId={fileId}
            selection={selection}
            onBack={handleBack}
            onFileClick={handleFileClick}
            sidebarOpen={sidebarOpen}
            onToggleSidebar={() => setSidebarOpen(prev => !prev)}
          />
        ) : (
          <DriveFileList
            selection={selection}
            onFileClick={handleFileClick}
          />
        )}
      </div>
    </div>
  )
}
