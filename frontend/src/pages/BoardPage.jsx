import { useLocation, useOutletContext } from 'react-router-dom'
import BoardSidebar from '../components/BoardSidebar'
import MobileSidebarOverlay from '../components/MobileSidebarOverlay'
import BoardPostList from '../components/BoardPostList'
import BoardPostDetail from '../components/BoardPostDetail'
import AnnouncementList from '../components/AnnouncementList'
import AnnouncementDetail from '../components/AnnouncementDetail'
import AnnouncementEditor from '../components/AnnouncementEditor'
import { useGuardedSearchParams } from '../components/UnsavedChangesProvider'
import FundPublicPositionList from '../components/fund/FundPublicPositionList'

const extractAiTitle = (markdown) => {
  if (!markdown || !markdown.trim()) return ''

  const lines = markdown.split('\n')
  for (const line of lines) {
    const trimmed = line.trim()
    if (trimmed.startsWith('## ')) return trimmed.slice(3).trim()
    if (trimmed.startsWith('# ')) return trimmed.slice(2).trim()
  }

  const first = lines[0].trim()
  return first.length > 50 ? first.slice(0, 50) : first
}

export default function BoardPage() {
  const { clubInfo } = useOutletContext()
  const location = useLocation()
  const [searchParams, setSearchParams] = useGuardedSearchParams()

  // URL에서 상태 복원
  const postId = searchParams.get('post')
  const boardId = searchParams.get('board')
  const teamId = searchParams.get('team')
  const tagId = searchParams.get('tag')
  const fundTeamId = searchParams.get('fund')
  const postRevision = searchParams.get('rev') || '0'
  const isWriting = searchParams.get('writing') === '1'
  const viewAnnouncement = searchParams.get('view') === 'announcement'
  const announcementId = searchParams.get('announcement')
  const announcementWriting = searchParams.get('announcementWriting') === '1'
  const announcementEditing = searchParams.get('announcementEditing')
  const initialAiTransfer = location.state?.aiEditorTransfer ?? (() => {
    const markdown = searchParams.get('aiContent')
    if (!markdown) return null

    return {
      title: extractAiTitle(markdown),
      markdown,
      jobId: searchParams.get('aiJobId')
        ? Number(searchParams.get('aiJobId'))
        : null,
      driveFileId: searchParams.get('aiDriveFileId')
        ? Number(searchParams.get('aiDriveFileId'))
        : null,
      semiCategoryIds: [],
      boardId: boardId ? Number(boardId) : null,
    }
  })()

  // 기본 팀 결정: 회장단(시스템팀) 제외 후 첫 번째 팀
  const defaultTeam = (() => {
    const teams = clubInfo?.myTeams || []
    const nonSystem = teams.filter(t => !t.systemTeam)
    return nonSystem.length > 0 ? nonSystem[0] : null
  })()

  // 사이드바 선택 상태 — URL 파라미터 없으면 기본 팀 선택
  const selection = (() => {
    if (viewAnnouncement || announcementId || announcementWriting || announcementEditing) {
      return { type: 'announcement', id: null, name: '공지사항' }
    }
    if (fundTeamId) return { type: 'fund', id: Number(fundTeamId), name: '' }
    if (boardId) return { type: 'board', id: Number(boardId), name: '' }
    if (teamId) return { type: 'team', id: Number(teamId), name: '' }
    if (tagId) return { type: 'tag', id: Number(tagId), name: '' }
    // URL 파라미터 없으면 기본 팀 또는 전체
    if (defaultTeam) return { type: 'team', id: defaultTeam.teamId, name: defaultTeam.teamName }
    return { type: 'all', id: null, name: '전체' }
  })()

  const handleSelect = (sel) => {
    const params = {}
    if (sel.type === 'board') params.board = sel.id
    if (sel.type === 'team') params.team = sel.id
    if (sel.type === 'tag') params.tag = sel.id
    if (sel.type === 'fund') params.fund = sel.id
    if (sel.type === 'announcement') params.view = 'announcement'
    setSearchParams(params)
  }

  const handlePostClick = (post) => {
    const params = Object.fromEntries(searchParams)
    params.post = post.id
    delete params.writing
    delete params.rev
    setSearchParams(params)
  }

  const handleBack = () => {
    const params = Object.fromEntries(searchParams)
    delete params.post
    delete params.writing
    delete params.rev
    setSearchParams(params)
  }

  // 글쓰기 → 인라인 편집 모드 (새 글)
  const handleWrite = () => {
    const params = Object.fromEntries(searchParams)
    delete params.post
    delete params.rev
    params.writing = '1'
    setSearchParams(params)
  }

  // 공지 상세 클릭
  const handleAnnouncementClick = (id) => {
    setSearchParams({ view: 'announcement', announcement: id })
  }

  // 공지 목록으로 돌아가기
  const handleAnnouncementBack = () => {
    setSearchParams({ view: 'announcement' })
  }

  // 공지 글쓰기
  const handleAnnouncementWrite = () => {
    setSearchParams({ view: 'announcement', announcementWriting: '1' })
  }

  // 공지 수정
  const handleAnnouncementEdit = (id) => {
    setSearchParams({ view: 'announcement', announcementEditing: id })
  }

  // 공지 저장 후
  const handleAnnouncementSaved = () => {
    setSearchParams({ view: 'announcement' })
  }

  // 메인 컨텐츠 영역 렌더링
  const renderContent = () => {
    // 공지사항 영역
    if (selection.type === 'announcement') {
      if (announcementWriting) {
        return (
          <AnnouncementEditor
            clubId={clubInfo.clubId}
            onBack={handleAnnouncementBack}
            onSaved={handleAnnouncementSaved}
          />
        )
      }
      if (announcementEditing) {
        return (
          <AnnouncementEditor
            clubId={clubInfo.clubId}
            announcementId={Number(announcementEditing)}
            onBack={handleAnnouncementBack}
            onSaved={handleAnnouncementSaved}
          />
        )
      }
      if (announcementId) {
        return (
          <AnnouncementDetail
            clubId={clubInfo.clubId}
            announcementId={Number(announcementId)}
            isExecutive={clubInfo.isExecutive}
            onBack={handleAnnouncementBack}
            onEdit={handleAnnouncementEdit}
          />
        )
      }
      return (
        <AnnouncementList
          clubId={clubInfo.clubId}
          isExecutive={clubInfo.isExecutive}
          onAnnouncementClick={handleAnnouncementClick}
          onWrite={handleAnnouncementWrite}
        />
      )
    }

    // 펀드팀 공개 종목 뷰
    if (selection.type === 'fund') {
      return <FundPublicPositionList fundTeamId={selection.id} clubId={clubInfo?.clubId} />
    }

    // 기존 게시판 영역
    if (isWriting) {
      return (
        <BoardPostDetail
          key={`new-post:${selection.type}:${selection.id ?? 'all'}:${initialAiTransfer?.driveFileId ?? 'manual'}`}
          onBack={handleBack}
          isNewPost
          initialBoardId={selection.type === 'board' ? selection.id : null}
          initialAiTransfer={initialAiTransfer}
        />
      )
    }
    if (postId) {
      return <BoardPostDetail key={`post:${postId}:${postRevision}`} postId={postId} onBack={handleBack} selection={selection} />
    }
    return (
      <BoardPostList
        selection={selection}
        onPostClick={handlePostClick}
        onWrite={handleWrite}
      />
    )
  }

  return (
    <div className="flex gap-4 -mx-3 md:-mx-6 -mt-4 md:-mt-6 -mb-24 md:-mb-40 px-3 md:px-6 pt-4 md:pt-6 min-h-[calc(100vh-var(--header-h))] animate-fade-in-up">
      <MobileSidebarOverlay>
        <BoardSidebar
          selection={selection}
          onSelect={handleSelect}
        />
      </MobileSidebarOverlay>

      <div className="flex-1 min-w-0 pb-48 md:pb-64">
        {renderContent()}
      </div>
    </div>
  )
}
