import { useEffect, useState } from 'react'
import { useOutletContext, useNavigate } from 'react-router-dom'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import Pagination from './Pagination'
import EmptyState from './EmptyState'
import FundPositionView from './FundPositionView'
import { PostSkeleton } from './SkeletonRow'
import handleApiError from '../lib/handleApiError'

export default function BoardPostList({ selection, onPostClick, onWrite }) {
  const { clubInfo } = useOutletContext()
  const navigate = useNavigate()
  const [posts, setPosts] = useState([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(false)
  const [aiPermission, setAiPermission] = useState(false)
  const [showPdfModal, setShowPdfModal] = useState(false)
  const [pdfFiles, setPdfFiles] = useState([])
  const [pdfLoading, setPdfLoading] = useState(false)
  const [fundView, setFundView] = useState(false) // 펀드팀 뷰 토글

  // 교차 필터: 팀 선택 시 태그 필터, 태그 선택 시 팀 필터
  const [crossFilterId, setCrossFilterId] = useState(null)
  const [crossOptions, setCrossOptions] = useState([]) // 선택 가능한 태그 또는 팀 목록

  // 전체뷰 다중 필터
  const [selectedTeamIds, setSelectedTeamIds] = useState([])
  const [selectedTagIds, setSelectedTagIds] = useState([])
  const [allTeams, setAllTeams] = useState([])
  const [allTags, setAllTags] = useState([])

  // AI 권한 체크
  useEffect(() => {
    if (!clubInfo?.clubId) return
    api.get(`/clubs/${clubInfo.clubId}/ai/permission`)
      .then(data => setAiPermission(data.hasPermission))
      .catch(() => setAiPermission(false))
  }, [clubInfo?.clubId])

  // 사이드바 선택 변경 시 page + crossFilter + fundView + 다중필터 리셋
  useEffect(() => { setPage(0); setCrossFilterId(null); setFundView(false); setSelectedTeamIds([]); setSelectedTagIds([]) }, [selection.type, selection.id])

  // 교차 필터 옵션 로드 + 전체뷰 팀/태그 목록
  useEffect(() => {
    if (!clubInfo?.clubId) return
    if (selection.type === 'all') {
      api.get(`/clubs/${clubInfo.clubId}/boards/sidebar`)
        .then(data => {
          setAllTeams((data?.teams || []).filter(t => !t.systemTeam))
          setAllTags((data?.tags || []).filter(t => !t.systemTag && !t.generation))
        })
        .catch(() => { setAllTeams([]); setAllTags([]) })
      setCrossOptions([])
    } else if (selection.type === 'team') {
      api.get(`/clubs/${clubInfo.clubId}/semi-categories`)
        .then(data => setCrossOptions(data || []))
        .catch(() => setCrossOptions([]))
      setAllTeams([]); setAllTags([])
    } else if (selection.type === 'tag') {
      api.get(`/clubs/${clubInfo.clubId}/boards/sidebar`)
        .then(data => setCrossOptions(data?.teams || []))
        .catch(() => setCrossOptions([]))
      setAllTeams([]); setAllTags([])
    } else {
      setCrossOptions([]); setAllTeams([]); setAllTags([])
    }
  }, [clubInfo?.clubId, selection.type, selection.id])

  useEffect(() => {
    if (!clubInfo?.clubId || !selection.type) return
    setLoading(true)

    let url
    if (selection.type === 'all') {
      url = `/clubs/${clubInfo.clubId}/posts?page=${page}&size=20`
      if (selectedTeamIds.length > 0) url += selectedTeamIds.map(id => `&teamIds=${id}`).join('')
      if (selectedTagIds.length > 0) url += selectedTagIds.map(id => `&tagIds=${id}`).join('')
    } else if (selection.type === 'board') {
      url = `/clubs/${clubInfo.clubId}/boards/${selection.id}/posts?page=${page}&size=20`
    } else if (selection.type === 'team') {
      url = `/clubs/${clubInfo.clubId}/posts?teamIds=${selection.id}&page=${page}&size=20`
      if (crossFilterId) url += `&tagIds=${crossFilterId}`
    } else if (selection.type === 'tag') {
      url = `/clubs/${clubInfo.clubId}/posts?tagIds=${selection.id}&page=${page}&size=20`
      if (crossFilterId) url += `&teamIds=${crossFilterId}`
    }

    api.get(url)
      .then(data => {
        setPosts(data.content || [])
        setTotalPages(data.totalPages || 0)
      })
      .catch(e => handleApiError(e, '게시글 목록 조회'))
      .finally(() => setLoading(false))
  }, [clubInfo?.clubId, selection.type, selection.id, page, crossFilterId, selectedTeamIds, selectedTagIds])

  const openPdfPicker = async () => {
    setPdfLoading(true)
    setShowPdfModal(true)
    try {
      const data = await api.get(`/clubs/${clubInfo.clubId}/drive/files?size=100`)
      const pdfs = (data.content || []).filter(f =>
        (f.mimeType?.includes('pdf') || f.originalName?.toLowerCase().endsWith('.pdf')) && !f.linkedPostId
      )
      setPdfFiles(pdfs)
    } catch (e) { handleApiError(e, 'PDF 파일 조회'); setPdfFiles([]) }
    finally { setPdfLoading(false) }
  }

  const handlePdfSelect = (file) => {
    setShowPdfModal(false)
    navigate(`/club/drive?file=${file.id}`)
  }

  const isFundTeam = selection.type === 'team' && selection.teamType === 'FUND'

  // 펀드팀 뷰가 활성화되면 FundPositionView 렌더
  if (isFundTeam && fundView) {
    return (
      <div className="flex flex-col gap-3">
        {/* 팀 헤더 축약 카드 */}
        {selection.description && (
          <a
            href={`/club/teams/${selection.id}`}
            className="flex items-center gap-3 px-6 py-3 bg-white rounded-2xl border border-slate-200 shadow-sm hover:bg-slate-50 transition-colors"
          >
            <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center text-sm">👥</div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-slate-800 truncate">{selection.name}</p>
              <p className="text-xs text-slate-500 truncate">{selection.description}</p>
            </div>
            <span className="text-xs text-slate-400 shrink-0">{selection.memberCount}명</span>
          </a>
        )}
        {/* 뷰 토글 */}
        <div className="flex items-center gap-1 px-1">
          <button
            onClick={() => setFundView(false)}
            className="px-3 py-1.5 text-xs rounded-lg transition-colors cursor-pointer bg-white text-slate-500 border border-slate-200 hover:border-slate-400"
          >
            일반 뷰
          </button>
          <button
            className="px-3 py-1.5 text-xs rounded-lg transition-colors cursor-pointer bg-primary text-white border border-blue-500"
          >
            펀드팀 뷰
          </button>
        </div>
        <FundPositionView teamId={selection.id} teamName={selection.name} />
      </div>
    )
  }

  return (
    <div className="flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* 팀 헤더 축약 카드 */}
      {selection.type === 'team' && selection.description && (
        <a
          href={`/club/teams/${selection.id}`}
          className="flex items-center gap-3 px-6 py-3 bg-slate-50 border-b border-slate-100 hover:bg-slate-100 transition-colors"
        >
          <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center text-sm">👥</div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-slate-800 truncate">{selection.name}</p>
            <p className="text-xs text-slate-500 truncate">{selection.description}</p>
          </div>
          <span className="text-xs text-slate-400 shrink-0">{selection.memberCount}명</span>
        </a>
      )}

      {/* 뷰 토글 — FUND 팀일 때만 표시 */}
      {isFundTeam && (
        <div className="flex items-center gap-1 px-6 pt-3">
          <button
            className="px-3 py-1.5 text-xs rounded-lg transition-colors cursor-pointer bg-primary text-white border border-blue-500"
          >
            일반 뷰
          </button>
          <button
            onClick={() => setFundView(true)}
            className="px-3 py-1.5 text-xs rounded-lg transition-colors cursor-pointer bg-white text-slate-500 border border-slate-200 hover:border-slate-400"
          >
            펀드팀 뷰
          </button>
        </div>
      )}

      {/* 상단 바 */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
        <h3 className="text-base font-semibold text-slate-900">{selection.name || (selection.type === 'all' ? '전체' : '게시판')}</h3>
        <div className="flex items-center gap-2">
          {onWrite && selection.canWrite !== false && (
            <button
              onClick={onWrite}
              className="px-4 py-1.5 bg-primary text-white text-sm rounded-lg hover:bg-primary-hover transition-colors cursor-pointer"
            >
              글쓰기
            </button>
          )}
        </div>
      </div>

      {/* 전체뷰 다중 필터 탭 */}
      {selection.type === 'all' && (allTeams.length > 0 || allTags.length > 0) && (
        <div className="px-6 py-2 border-b border-slate-100 space-y-2">
          {allTeams.length > 0 && (
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className="text-xs text-slate-400 shrink-0">팀</span>
              {allTeams.map(t => (
                <button
                  key={t.id}
                  onClick={() => { setSelectedTeamIds(prev => prev.includes(t.id) ? prev.filter(id => id !== t.id) : [...prev, t.id]); setPage(0) }}
                  className={`px-2.5 py-1 text-xs rounded-full border transition-colors cursor-pointer ${
                    selectedTeamIds.includes(t.id)
                      ? 'bg-slate-700 text-white border-slate-700'
                      : 'bg-white text-slate-500 border-slate-200 hover:border-slate-400'
                  }`}
                >
                  {t.name}
                </button>
              ))}
            </div>
          )}
          {allTags.length > 0 && (
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className="text-xs text-slate-400 shrink-0">태그</span>
              {allTags.map(tag => (
                <button
                  key={tag.id}
                  onClick={() => { setSelectedTagIds(prev => prev.includes(tag.id) ? prev.filter(id => id !== tag.id) : [...prev, tag.id]); setPage(0) }}
                  className={`px-2.5 py-1 text-xs rounded-full border transition-colors cursor-pointer ${
                    selectedTagIds.includes(tag.id)
                      ? 'bg-primary text-white border-primary'
                      : 'bg-white text-slate-500 border-slate-200 hover:border-slate-400'
                  }`}
                >
                  {tag.name}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 교차 필터 pills */}
      {crossOptions.length > 0 && (selection.type === 'team' || selection.type === 'tag') && (
        <div className="px-6 py-2 border-b border-slate-100 flex items-center gap-2 flex-wrap">
          <span className="text-xs text-slate-400 shrink-0">
            {selection.type === 'team' ? '태그:' : '팀:'}
          </span>
          <button
            onClick={() => { setCrossFilterId(null); setPage(0) }}
            className={`px-2.5 py-1 text-xs rounded-full border transition-colors cursor-pointer ${
              !crossFilterId
                ? 'bg-slate-700 text-white border-slate-700'
                : 'bg-white text-slate-500 border-slate-200 hover:border-slate-400'
            }`}
          >
            전체
          </button>
          {crossOptions.map(opt => {
            const optId = opt.id
            const optName = opt.name
            return (
              <button
                key={optId}
                onClick={() => { setCrossFilterId(crossFilterId === optId ? null : optId); setPage(0) }}
                className={`px-2.5 py-1 text-xs rounded-full border transition-colors cursor-pointer ${
                  crossFilterId === optId
                    ? selection.type === 'team' ? 'bg-primary text-white border-blue-500' : 'bg-emerald-500 text-white border-emerald-500'
                    : 'bg-white text-slate-500 border-slate-200 hover:border-slate-400'
                }`}
              >
                {optName}
              </button>
            )
          })}
        </div>
      )}

      {/* 글 목록 */}
      <div>
        {loading ? (
          <PostSkeleton count={5} />
        ) : posts.length === 0 ? (
          <EmptyState icon="post" message="아직 게시글이 없습니다" ctaLabel="첫 글 작성하기 →" onCta={() => navigate('/club/boards?write=true')} />
        ) : (
          posts.map(post => (
            <PostRow key={post.id} post={post} onClick={() => onPostClick(post)} filterTagId={selection.type === 'tag' ? selection.id : null} />
          ))
        )}
      </div>

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className="border-t border-slate-100 py-3">
          <Pagination page={page} totalPages={totalPages} setPage={setPage} />
        </div>
      )}

      {/* PDF 선택 모달 */}
      {showPdfModal && (
        <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/40" onClick={() => setShowPdfModal(false)}>
          <div className="bg-white rounded-2xl w-full max-w-lg mx-4 max-h-[70vh] flex flex-col" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
              <h3 className="font-semibold text-slate-900">PDF 선택 → AI 글 생성</h3>
              <button onClick={() => setShowPdfModal(false)} className="text-slate-400 hover:text-slate-600 cursor-pointer">✕</button>
            </div>
            <div className="flex-1 overflow-y-auto p-4">
              {pdfLoading ? (
                <p className="text-sm text-slate-400 text-center py-8">로딩 중...</p>
              ) : pdfFiles.length === 0 ? (
                <p className="text-sm text-slate-400 text-center py-8">AI 글을 생성할 수 있는 PDF가 없습니다</p>
              ) : (
                <div className="space-y-1">
                  {pdfFiles.map(f => (
                    <button
                      key={f.id}
                      onClick={() => handlePdfSelect(f)}
                      className="w-full text-left px-4 py-3 rounded-lg hover:bg-violet-50 transition-colors cursor-pointer flex items-center gap-3"
                    >
                      <span className="shrink-0 w-9 h-9 rounded-lg bg-red-50 text-red-500 flex items-center justify-center text-xs font-bold">PDF</span>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-slate-900 truncate">{f.originalName}</p>
                        <p className="text-xs text-slate-400">{f.teams?.length > 0 ? f.teams.map(t => t.teamName).join(', ') : '공용'} · {formatTime(f.createdAt)}</p>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function PostRow({ post, onClick, filterTagId }) {
  // 현재 필터 태그와 동일한 태그는 숨기고, 추가 태그만 표시
  const visibleTags = filterTagId
    ? post.semiCategories?.filter(cat => cat.id !== filterTagId)
    : post.semiCategories

  return (
    <button
      onClick={onClick}
      className="w-full text-left px-6 py-3 border-b border-slate-50 hover:bg-slate-50 transition-colors cursor-pointer"
    >
      <div className="flex items-center gap-2">
        <span className="font-medium text-sm text-slate-900 truncate flex-1">{post.title}</span>
        {visibleTags?.map(cat => (
          <span key={cat.id} className="shrink-0 px-2 py-0.5 text-xs rounded bg-blue-50 text-blue-600">
            {cat.name}
          </span>
        ))}
      </div>
      <div className="mt-1 text-xs text-slate-400 flex items-center gap-2">
        <span>{post.boards?.map(b => b.name).join(', ')}</span>
        <span>·</span>
        <span>{post.authorName}</span>
        <span>·</span>
        <span>{formatTime(post.createdAt)}</span>
        {post.updatedAt && <span className="text-slate-300">(수정됨)</span>}
        {post.likeCount > 0 && (
          <span className="text-xs text-rose-400 flex items-center gap-0.5">
            <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><path d="M3.172 5.172a4 4 0 015.656 0L10 6.343l1.172-1.171a4 4 0 115.656 5.656L10 17.657l-6.828-6.829a4 4 0 010-5.656z" /></svg>
            {post.likeCount}
          </span>
        )}
      </div>
    </button>
  )
}
