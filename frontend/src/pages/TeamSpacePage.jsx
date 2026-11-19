import { useState, useEffect, useRef } from 'react'
import { useParams, useOutletContext, Link } from 'react-router-dom'
import { MessageSquare, Pencil } from 'lucide-react'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import Pagination from '../components/Pagination'
import HomeCalendarPanel from '../components/HomeCalendarPanel'
import GenerationBadge from '../components/GenerationBadge'
import ConfirmModal from '../components/ConfirmModal'
import DriveUploadModal from '../components/DriveUploadModal'
import useMessagingStore from '../store/messagingStore'
import useClubStore from '../store/clubStore'
import handleApiError from '../lib/handleApiError'
import TAG_COLORS from '../lib/tagColors'
import EmptyState from '../components/EmptyState'
import { useGuardedNavigate } from '../components/UnsavedChangesProvider'

// 파일 확장자별 아이콘 스타일
const EXT_STYLES = {
  pdf: { bg: 'bg-red-100', text: 'text-red-500', label: 'PDF' },
  doc: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'DOC' },
  docx: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'DOC' },
  xls: { bg: 'bg-green-100', text: 'text-green-600', label: 'XLS' },
  xlsx: { bg: 'bg-green-100', text: 'text-green-600', label: 'XLS' },
  ppt: { bg: 'bg-orange-100', text: 'text-orange-600', label: 'PPT' },
  pptx: { bg: 'bg-orange-100', text: 'text-orange-600', label: 'PPT' },
  png: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'IMG' },
  jpg: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'IMG' },
  jpeg: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'IMG' },
}
function getExtStyle(name) {
  const ext = name?.split('.').pop()?.toLowerCase() || ''
  return EXT_STYLES[ext] || { bg: 'bg-slate-100', text: 'text-slate-600', label: ext.toUpperCase().substring(0, 3) || '?' }
}


// 아바타 색상
const AVATAR_COLORS = ['bg-blue-500', 'bg-violet-500', 'bg-emerald-500', 'bg-amber-500']

function isNew(createdAt) {
  return Date.now() - new Date(createdAt).getTime() < 24 * 60 * 60 * 1000
}

export default function TeamSpacePage() {
  const { teamId } = useParams()
  const { clubInfo } = useOutletContext()
  const guardedNavigate = useGuardedNavigate()
  const [team, setTeam] = useState(null)
  const [loading, setLoading] = useState(true)
  const [forbidden, setForbidden] = useState(false)

  const isAdmin = clubInfo?.isExecutive
  const myTeamEntry = clubInfo?.myTeams?.find(t => t.teamId === Number(teamId))
  const isTeamLeader = myTeamEntry?.teamRole === 'LEADER'
  const isTeamManager = myTeamEntry?.teamRole === 'MANAGER'
  const canEditName = isTeamLeader || isAdmin

  // 팀명 인라인 수정
  const [editingName, setEditingName] = useState(false)
  const [nameValue, setNameValue] = useState('')
  const handleSaveName = async () => {
    const trimmed = nameValue.trim()
    if (!trimmed || trimmed === team?.name) { setEditingName(false); return }
    try {
      await api.put(`/clubs/${clubInfo.clubId}/admin/teams/${teamId}/name`, { name: trimmed })
      setTeam(prev => ({ ...prev, name: trimmed }))
      setEditingName(false)
    } catch (err) {
      handleApiError(err, '팀명 수정')
      setEditingName(false)
    }
  }

  useEffect(() => {
    if (!clubInfo?.clubId) return
    setLoading(true)
    setForbidden(false)
    api.get(`/clubs/${clubInfo.clubId}/teams/${teamId}`)
      .then(setTeam)
      .catch(e => {
        if (e.message?.includes('멤버만') || e.message?.includes('권한')) setForbidden(true)
        else handleApiError(e, '팀 정보 조회')
      })
      .finally(() => setLoading(false))
  }, [clubInfo?.clubId, teamId])

  if (loading) {
    return (
      <div className="py-12 flex justify-center">
        <div className="animate-spin w-6 h-6 border-2 border-slate-300 border-t-slate-600 rounded-full" />
      </div>
    )
  }

  if (forbidden) {
    return <p className="text-center py-12 text-sm text-slate-400">해당 팀의 멤버만 접근할 수 있습니다</p>
  }

  if (!team) {
    return <p className="text-center py-12 text-sm text-slate-400">팀을 찾을 수 없습니다</p>
  }

  const leader = team.members?.find(m => m.teamRole === 'LEADER')

  const TEAM_TYPE_LABELS = { FUND: '펀드팀', ANALYSIS: '분석팀', REAL_ESTATE: '부동산팀', CUSTOM: '커스텀' }

  return (
    <div className="space-y-5 animate-fade-in-up">
      {/* ① 브레드크럼 */}
      <div className="flex items-center gap-1 text-sm text-slate-400">
        <Link to="/club" className="hover:text-slate-600 transition-colors">홈</Link>
        <span>/</span>
        <span className="text-slate-600 font-medium">{team.name}</span>
      </div>

      {/* ② 팀 헤더 카드 */}
      <div className="bg-white rounded-2xl border border-slate-200 px-5 md:px-7 py-5 md:py-6 flex items-center gap-4">
        <div className={`shrink-0 w-11 h-11 md:w-[52px] md:h-[52px] rounded-[14px] ${team.teamType === 'FUND' ? 'bg-emerald-500' : 'bg-blue-500'} flex items-center justify-center text-white text-lg md:text-xl font-bold`}>
          {team.name?.charAt(0) || 'T'}
        </div>
        <div className="flex-1 min-w-0">
          <h1 className="text-lg md:text-lg font-bold text-slate-900 flex items-center gap-1.5">
            {editingName ? (
              <input
                autoFocus
                value={nameValue}
                onChange={e => setNameValue(e.target.value)}
                onBlur={handleSaveName}
                onKeyDown={e => { if (e.key === 'Enter') handleSaveName(); if (e.key === 'Escape') setEditingName(false) }}
                className="text-lg font-bold border-b-2 border-primary outline-none bg-transparent max-w-[200px]"
              />
            ) : (
              <>
                <span>{team.name}</span>
                {canEditName && (
                  <button
                    onClick={() => { setNameValue(team.name); setEditingName(true) }}
                    className="p-1 rounded hover:bg-slate-100 transition-colors cursor-pointer"
                    title="팀명 수정"
                  >
                    <Pencil className="w-3.5 h-3.5 text-slate-400" />
                  </button>
                )}
              </>
            )}
            {team.teamType && team.teamType !== 'CUSTOM' && (
              <span className="ml-1 text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-500 font-normal align-middle">
                {TEAM_TYPE_LABELS[team.teamType] || team.teamType}
              </span>
            )}
            {team.generations?.length > 0 && (
              <GenerationBadge generations={team.generations} mode="latest" className="ml-1 align-middle" />
            )}
          </h1>
          <p className="text-sm text-slate-400">팀장: {leader?.name || '미지정'}</p>
        </div>
        {isTeamLeader && (
          <button
            onClick={() => guardedNavigate('/club/admin')}
            className="shrink-0 w-9 h-9 rounded-lg bg-slate-50 border border-slate-200 flex items-center justify-center text-slate-500 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            ⚙
          </button>
        )}
      </div>

      {/* ③ 팀소개 카드 */}
      <TeamIntroCard
        team={team}
        isTeamLeader={isTeamLeader}
        clubId={clubInfo?.clubId}
        teamId={Number(teamId)}
        onRefresh={() => {
          api.get(`/clubs/${clubInfo.clubId}/teams/${teamId}`)
            .then(setTeam)
            .catch(e => handleApiError(e, '팀 새로고침'))
        }}
      />

      {/* ④ 팀 캘린더 (인라인) */}
      <HomeCalendarPanel
        clubId={clubInfo?.clubId}
        isExecutive={isAdmin || isTeamLeader || isTeamManager}
        fixedTeamId={Number(teamId)}
        showFilter={false}
      />

      {/* ⑤ 콘텐츠 패널 */}
      <TeamContentPanel
        clubId={clubInfo?.clubId}
        teamId={Number(teamId)}
        navigate={guardedNavigate}
      />
    </div>
  )
}

/* ─── ③ 팀소개 카드 ─── */
function TeamIntroCard({ team, isTeamLeader, clubId, teamId, onRefresh }) {
  const [showMembers, setShowMembers] = useState(false)
  const members = team.members || []

  return (
    <>
      <div className="bg-white rounded-2xl border border-slate-200 px-5 md:px-6 py-4 md:py-5 space-y-3.5">
        {/* KV 필드 */}
        {team.generations?.length > 0 && (
          <div className="flex items-center">
            <span className="text-sm font-medium text-slate-500 w-16 shrink-0">기수</span>
            <span className="text-sm font-semibold text-slate-900">{team.generations.join(', ')}</span>
          </div>
        )}
        {team.creditAllocation > 0 && (
          <div className="flex items-center">
            <span className="text-sm font-medium text-slate-500 w-16 shrink-0">크레딧</span>
            <span className="text-sm font-semibold text-slate-900">{team.creditAllocation}</span>
          </div>
        )}

        {/* 설명 */}
        {team.description && (
          <p className="text-sm italic text-slate-500">{team.description}</p>
        )}

        {/* 구분선 + 아바타 행 */}
        <div className="h-px bg-slate-200" />
        <div className="flex items-center gap-1.5">
          {members.slice(0, 4).map((m, i) => (
            <div
              key={m.clubMembershipId}
              className={`shrink-0 w-8 h-8 rounded-full ${AVATAR_COLORS[i % AVATAR_COLORS.length]} flex items-center justify-center text-white text-xs font-semibold`}
            >
              {m.name?.charAt(0)}
            </div>
          ))}
          {members.length > 0 && (
            <button
              onClick={() => setShowMembers(true)}
              className="text-sm font-medium text-blue-500 hover:text-blue-600 transition-colors cursor-pointer ml-1"
            >
              {members.length <= 4 ? `${members.length}명` : `외 ${members.length - 4}명 >`}
            </button>
          )}
        </div>
      </div>

      {/* 멤버 모달 */}
      {showMembers && (
        <MemberModal
          members={members}
          onClose={() => setShowMembers(false)}
          isTeamLeader={isTeamLeader}
          clubId={clubId}
          teamId={teamId}
          onRefresh={onRefresh}
        />
      )}
    </>
  )
}

/* ─── 멤버 모달 ─── */
function MemberModal({ members, onClose, isTeamLeader, clubId, teamId, onRefresh }) {
  const [actionLoading, setActionLoading] = useState(null)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })
  const startDmWith = useMessagingStore(s => s.startDmWith)
  const myMembershipId = useClubStore(s => s.clubInfo?.membershipId)
  const roleLabel = { LEADER: '팀장', MANAGER: '매니저', MEMBER: '팀원' }
  const roleBg = { LEADER: 'bg-blue-50 text-blue-600', MANAGER: 'bg-amber-50 text-amber-600', MEMBER: 'bg-slate-100 text-slate-500' }

  const handleManagerToggle = async (member) => {
    const isManager = member.teamRole === 'MANAGER'
    setActionLoading(member.clubMembershipId)
    try {
      if (isManager) {
        await api.delete(`/clubs/${clubId}/teams/${teamId}/members/${member.clubMembershipId}/manager`)
      } else {
        await api.put(`/clubs/${clubId}/teams/${teamId}/members/${member.clubMembershipId}/manager`)
      }
      onRefresh?.()
    } catch (e) {
      setAlertModal({ open: true, message: e.response?.data?.message || '권한 변경에 실패했습니다' })
    } finally {
      setActionLoading(null)
    }
  }

  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/30" onClick={onClose}>
      <div className="bg-white rounded-2xl w-full max-w-md mx-4 max-h-[70vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
          <h3 className="text-base font-bold text-slate-900">팀원 ({members.length}명)</h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 cursor-pointer">✕</button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-3 space-y-1">
          {members.map((m, i) => (
            <div key={m.clubMembershipId} className="flex items-center gap-3 py-2">
              <div className={`shrink-0 w-8 h-8 rounded-full ${AVATAR_COLORS[i % AVATAR_COLORS.length]} flex items-center justify-center text-white text-xs font-semibold`}>
                {m.name?.charAt(0)}
              </div>
              <div className="flex-1 min-w-0">
                <span className="text-sm font-medium text-slate-900">{m.name}</span>
                <span className="text-xs text-slate-400 ml-2">{m.studentId}</span>
              </div>
              {/* 팀장이 보는 매니저 임명/해제 버튼 */}
              {isTeamLeader && m.teamRole !== 'LEADER' && (
                <button
                  onClick={() => handleManagerToggle(m)}
                  disabled={actionLoading === m.clubMembershipId}
                  className={`text-xs px-2.5 py-1 rounded-full font-medium transition-colors cursor-pointer ${
                    m.teamRole === 'MANAGER'
                      ? 'bg-amber-50 text-amber-600 hover:bg-amber-100'
                      : 'bg-slate-50 text-slate-500 hover:bg-slate-100'
                  } ${actionLoading === m.clubMembershipId ? 'opacity-50' : ''}`}
                >
                  {actionLoading === m.clubMembershipId
                    ? '...'
                    : m.teamRole === 'MANAGER'
                      ? '매니저 해제'
                      : '매니저 임명'}
                </button>
              )}
              {/* 팀장이 아닌 경우 또는 팀장 본인의 역할 뱃지 */}
              {(!isTeamLeader || m.teamRole === 'LEADER') && (
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${roleBg[m.teamRole] || roleBg.MEMBER}`}>
                  {roleLabel[m.teamRole] || '팀원'}
                </span>
              )}
              {/* DM 버튼 */}
              {m.clubMembershipId !== myMembershipId && (
                <button
                  onClick={() => { startDmWith(clubId, m.clubMembershipId); onClose() }}
                  className="p-1.5 hover:bg-slate-100 rounded cursor-pointer"
                  title="DM 보내기"
                >
                  <MessageSquare className="w-4 h-4 text-slate-400" />
                </button>
              )}
            </div>
          ))}
        </div>

        {alertModal.open && (
          <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
        )}
      </div>
    </div>
  )
}

/* ─── ⑤ 콘텐츠 패널 ─── */
function TeamContentPanel({ clubId, teamId, navigate }) {
  const [tab, setTab] = useState('drive')
  const [tags, setTags] = useState([])
  const [tagId, setTagId] = useState('')

  // Drive
  const [files, setFiles] = useState([])
  const [filePage, setFilePage] = useState(0)
  const [fileTotalPages, setFileTotalPages] = useState(0)
  const [filesLoading, setFilesLoading] = useState(true)
  const [uploadFile, setUploadFile] = useState(null)
  const [isDragging, setIsDragging] = useState(false)
  const dragCounter = useRef(0)

  // Board
  const [posts, setPosts] = useState([])
  const [postPage, setPostPage] = useState(0)
  const [postTotalPages, setPostTotalPages] = useState(0)
  const [postsLoading, setPostsLoading] = useState(true)

  useEffect(() => {
    if (!clubId) return
    api.get(`/clubs/${clubId}/semi-categories`).then(setTags).catch(e => handleApiError(e, '카테고리 조회'))
  }, [clubId])

  useEffect(() => { setFilePage(0); setPostPage(0) }, [tagId, tab])

  useEffect(() => {
    if (!clubId || tab !== 'drive') return
    setFilesLoading(true)
    const params = new URLSearchParams()
    params.set('teamId', String(teamId))
    if (tagId) params.set('tagId', tagId)
    params.set('page', String(filePage))
    params.set('size', '15')
    api.get(`/clubs/${clubId}/drive/files?${params}`)
      .then(data => { setFiles(data.content || []); setFileTotalPages(data.totalPages || 0) })
      .catch(e => handleApiError(e, '팀 파일 조회'))
      .finally(() => setFilesLoading(false))
  }, [clubId, teamId, tagId, filePage, tab])

  useEffect(() => {
    if (!clubId || tab !== 'board') return
    setPostsLoading(true)
    const params = new URLSearchParams()
    params.set('teamId', String(teamId))
    if (tagId) params.set('tagId', tagId)
    params.set('page', String(postPage))
    params.set('size', '15')
    api.get(`/clubs/${clubId}/posts?${params}`)
      .then(data => { setPosts(data.content || []); setPostTotalPages(data.totalPages || 0) })
      .catch(e => handleApiError(e, '팀 게시글 조회'))
      .finally(() => setPostsLoading(false))
  }, [clubId, teamId, tagId, postPage, tab])

  const handleUploadClick = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.onchange = (e) => {
      const file = e.target.files?.[0]
      if (file) setUploadFile(file)
    }
    input.click()
  }

  const handleDragEnter = (e) => { e.preventDefault(); dragCounter.current++; setIsDragging(true) }
  const handleDragLeave = (e) => { e.preventDefault(); dragCounter.current--; if (dragCounter.current === 0) setIsDragging(false) }
  const handleDragOver = (e) => e.preventDefault()
  const handleDrop = (e) => {
    e.preventDefault(); dragCounter.current = 0; setIsDragging(false)
    const file = e.dataTransfer?.files?.[0]
    if (file) setUploadFile(file)
  }

  const fetchFiles = () => {
    const params = new URLSearchParams()
    params.set('teamId', String(teamId))
    if (tagId) params.set('tagId', tagId)
    params.set('page', String(filePage))
    params.set('size', '15')
    api.get(`/clubs/${clubId}/drive/files?${params}`)
      .then(data => { setFiles(data.content || []); setFileTotalPages(data.totalPages || 0) })
      .catch(e => handleApiError(e, '파일 새로고침'))
  }

  const handleAction = () => {
    if (tab === 'drive') {
      handleUploadClick()
    } else {
      navigate('/club/boards')
    }
  }

  return (
    <div
      className="bg-white rounded-2xl border border-slate-200 overflow-hidden relative"
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {isDragging && (
        <div className="absolute inset-0 z-10 bg-blue-50/80 border-2 border-dashed border-blue-400 rounded-2xl flex items-center justify-center pointer-events-none">
          <p className="text-blue-600 font-semibold text-sm">파일을 놓아주세요</p>
        </div>
      )}
      {/* 탭 바 */}
      <div className="flex items-center border-b border-slate-200">
        <button
          onClick={() => setTab('drive')}
          className={`px-5 py-3 text-sm transition-colors cursor-pointer ${
            tab === 'drive'
              ? 'font-semibold text-blue-500 border-b-2 border-blue-500'
              : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          드라이브
        </button>
        <button
          onClick={() => setTab('board')}
          className={`px-5 py-3 text-sm transition-colors cursor-pointer ${
            tab === 'board'
              ? 'font-semibold text-blue-500 border-b-2 border-blue-500'
              : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          게시판
        </button>
        <div className="flex-1" />
        <button
          onClick={handleAction}
          className="mr-3 px-3.5 py-1.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors cursor-pointer"
        >
          {tab === 'drive' ? '+ 업로드' : '+ 글쓰기'}
        </button>
      </div>

      {/* 콘텐츠 영역 */}
      <div className="px-5 md:px-6 py-5 space-y-5">
        {/* 태그 필터 */}
        <div className="flex gap-1.5 flex-wrap">
          <button
            onClick={() => setTagId('')}
            className={`shrink-0 text-xs px-3 py-1 rounded-full transition-colors cursor-pointer ${
              !tagId ? 'bg-slate-900 text-white font-semibold' : 'bg-slate-100 text-slate-500 font-medium hover:bg-slate-200'
            }`}
          >
            전체
          </button>
          {tags.map(t => (
            <button
              key={t.id}
              onClick={() => setTagId(String(t.id))}
              className={`shrink-0 text-xs px-3 py-1 rounded-full transition-colors cursor-pointer ${
                String(t.id) === tagId ? 'bg-slate-900 text-white font-semibold' : 'bg-slate-100 text-slate-500 font-medium hover:bg-slate-200'
              }`}
            >
              {t.name}
            </button>
          ))}
        </div>

        {/* 드라이브 탭 */}
        {tab === 'drive' && (
          <>
            {filesLoading ? (
              <div className="py-6 flex justify-center">
                <div className="animate-spin w-4 h-4 border-2 border-slate-300 border-t-slate-600 rounded-full" />
              </div>
            ) : files.length === 0 ? (
              <EmptyState icon="file" message="파일이 없습니다" ctaLabel="파일 업로드 →" onCta={() => handleUploadClick()} />
            ) : (
              <div>
                {files.map(file => {
                  const ext = getExtStyle(file.originalName || file.displayName)
                  return (
                    <button
                      key={file.id}
                      onClick={() => navigate(`/club/drive?file=${file.id}`)}
                      className="w-full text-left flex items-center gap-3 py-2.5 border-b border-slate-100 last:border-b-0 hover:bg-slate-50 transition-colors cursor-pointer group"
                    >
                      <span className={`shrink-0 w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold ${ext.bg} ${ext.text}`}>
                        {ext.label}
                      </span>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-slate-900 truncate group-hover:text-blue-600 transition-colors">
                          {file.displayName || file.originalName}
                        </p>
                        <p className="text-xs text-slate-400 mt-0.5">
                          {file.uploaderName} · {formatTime(file.createdAt)}
                          {file.semiCategories?.[0]?.name && ` · ${file.semiCategories[0].name}`}
                        </p>
                      </div>
                    </button>
                  )
                })}
              </div>
            )}
            {fileTotalPages > 1 && (
              <Pagination page={filePage} totalPages={fileTotalPages} setPage={setFilePage} />
            )}
          </>
        )}

        {/* 게시판 탭 */}
        {tab === 'board' && (
          <>
            {postsLoading ? (
              <div className="py-6 flex justify-center">
                <div className="animate-spin w-4 h-4 border-2 border-slate-300 border-t-slate-600 rounded-full" />
              </div>
            ) : posts.length === 0 ? (
              <EmptyState icon="post" message="글이 없습니다" ctaLabel="게시판으로 이동 →" onCta={() => navigate('/club/boards')} />
            ) : (
              <div>
                {posts.map(post => (
                  <button
                    key={post.id}
                    onClick={() => navigate(`/club/boards?board=${post.boards?.[0]?.id}&post=${post.id}`)}
                    className="w-full text-left py-2.5 border-b border-slate-100 last:border-b-0 hover:bg-slate-50 transition-colors cursor-pointer group"
                  >
                    <div className="flex items-center gap-1.5 flex-wrap">
                      {post.semiCategories?.slice(0, 1).map((cat, i) => (
                        <span key={cat.id} className={`text-xs px-2 py-0.5 rounded ${TAG_COLORS[i % TAG_COLORS.length]} font-medium`}>
                          {cat.name}
                        </span>
                      ))}
                      <span className="text-sm font-medium text-slate-900 truncate group-hover:text-blue-600 transition-colors">
                        {post.title}
                      </span>
                      {isNew(post.createdAt) && (
                        <span className="text-xs px-1.5 py-0.5 bg-red-50 text-red-500 rounded font-bold shrink-0">N</span>
                      )}
                    </div>
                    <p className="text-xs text-slate-400 mt-1">
                      {post.authorName} · {formatTime(post.createdAt)}
                    </p>
                  </button>
                ))}
              </div>
            )}
            {postTotalPages > 1 && (
              <Pagination page={postPage} totalPages={postTotalPages} setPage={setPostPage} />
            )}
          </>
        )}
      </div>

      {/* 업로드 모달 */}
      {uploadFile && (
        <DriveUploadModal
          file={uploadFile}
          defaultTeamId={teamId}
          onClose={() => setUploadFile(null)}
          onUploaded={() => fetchFiles()}
        />
      )}
    </div>
  )
}
