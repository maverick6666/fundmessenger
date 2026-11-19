import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useOutletContext, useNavigate } from 'react-router-dom'
import { Document, Page, pdfjs } from 'react-pdf'
import 'react-pdf/dist/Page/AnnotationLayer.css'
import 'react-pdf/dist/Page/TextLayer.css'
import api from '../lib/api'
import ConfirmModal from './ConfirmModal'
import useAiJobStore from '../store/aiJobStore'
import handleApiError from '../lib/handleApiError'
import { API_URL, BASE_URL } from '../lib/api'

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString()

const PDF_OPTIONS = {
  cMapUrl: `https://unpkg.com/pdfjs-dist@${pdfjs.version}/cmaps/`,
  cMapPacked: true,
  standardFontDataUrl: `https://unpkg.com/pdfjs-dist@${pdfjs.version}/standard_fonts/`,
}

export default function DriveFileDetail({ fileId, selection, onBack, onFileClick, sidebarOpen, onToggleSidebar }) {
  const { clubInfo } = useOutletContext()
  const navigate = useNavigate()

  // 파일 정보
  const [file, setFile] = useState(null)
  const [fileLoading, setFileLoading] = useState(true)

  // 파일 네비게이션 바 (같은 카테고리 파일 목록)
  const [siblingFiles, setSiblingFiles] = useState([])
  const fileStripRef = useRef(null)

  // PDF 뷰어 — 단일 페이지 뷰
  const [numPages, setNumPages] = useState(null)
  const [currentPage, setCurrentPage] = useState(1)
  const [zoomMultiplier, setZoomMultiplier] = useState(1)
  const [pageSizes, setPageSizes] = useState([]) // 전 페이지 크기 사전 계산
  const [viewerSize, setViewerSize] = useState({ width: 0, height: 0 })
  const [pdfError, setPdfError] = useState(null)
  const [thumbnailsOpen, setThumbnailsOpen] = useState(false)
  const viewerRef = useRef(null)
  const thumbListRef = useRef(null)

  // 코멘트
  const [comments, setComments] = useState([])
  const [commentedPages, setCommentedPages] = useState([])
  const [commentFilter, setCommentFilter] = useState('all')
  const [newComment, setNewComment] = useState('')
  const [commentLoading, setCommentLoading] = useState(false)
  const [commentsOpen, setCommentsOpen] = useState(true)
  const [deleteFileConfirm, setDeleteFileConfirm] = useState(false)
  const [deleteCommentId, setDeleteCommentId] = useState(null)

  // 속성 편집
  const [editMode, setEditMode] = useState(false)
  const [editTeamIds, setEditTeamIds] = useState([])
  const [editFolderIds, setEditFolderIds] = useState([])
  const [editTagIds, setEditTagIds] = useState([])
  const [editSaving, setEditSaving] = useState(false)
  const [sidebarData, setSidebarData] = useState(null) // 팀 + 폴더 + 태그 목록

  // 알림 모달
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  // AI 글 생성
  const [aiPermission, setAiPermission] = useState(false)
  const [aiGenerating, setAiGenerating] = useState(false)
  const aiJob = useAiJobStore(s => s.job)
  const aiHasActive = useAiJobStore(s => s.hasActiveJob)
  const [showAiModal, setShowAiModal] = useState(false)
  const [myTeamBoards, setMyTeamBoards] = useState([]) // 내 팀 게시판 목록
  const [myTeams, setMyTeams] = useState([]) // 내 팀 목록
  const [allTags, setAllTags] = useState([]) // 세미카테고리 목록
  const [selectedBoardId, setSelectedBoardId] = useState(null)
  const [selectedTeamId, setSelectedTeamId] = useState(null)
  const [selectedTagIds, setSelectedTagIds] = useState([])

  // 모바일 스와이프로 이전/다음 파일 전환
  const touchRef = useRef(null)
  useEffect(() => {
    const el = viewerRef.current
    if (!el) return
    const onStart = (e) => {
      const t = e.touches[0]
      touchRef.current = { x: t.clientX, y: t.clientY }
    }
    const onEnd = (e) => {
      if (!touchRef.current) return
      const t = e.changedTouches[0]
      const dx = t.clientX - touchRef.current.x
      const dy = t.clientY - touchRef.current.y
      touchRef.current = null
      if (Math.abs(dx) < 60 || Math.abs(dy) > 40) return
      const idx = siblingFiles.findIndex(f => String(f.id) === String(fileId))
      if (idx < 0) return
      if (dx > 0 && idx > 0) onFileClick?.(siblingFiles[idx - 1])
      if (dx < 0 && idx < siblingFiles.length - 1) onFileClick?.(siblingFiles[idx + 1])
    }
    el.addEventListener('touchstart', onStart, { passive: true })
    el.addEventListener('touchend', onEnd, { passive: true })
    return () => {
      el.removeEventListener('touchstart', onStart)
      el.removeEventListener('touchend', onEnd)
    }
  }, [siblingFiles, fileId, onFileClick])

  // 파일 전환 시 스크롤 상단 리셋
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [fileId])

  useEffect(() => {
    setCurrentPage(1)
  }, [fileId])

  // 파일 정보 로드
  useEffect(() => {
    if (!clubInfo?.clubId || !fileId) return
    setFileLoading(true)
    api.get(`/clubs/${clubInfo.clubId}/drive/files/${fileId}`)
      .then(setFile)
      .catch(e => handleApiError(e, '파일 정보 조회'))
      .finally(() => setFileLoading(false))
  }, [clubInfo?.clubId, fileId])

  // 같은 카테고리 파일 목록 로드 (네비게이션 바용)
  useEffect(() => {
    if (!clubInfo?.clubId || !selection) return
    let url = `/clubs/${clubInfo.clubId}/drive/files?size=50`
    if (selection.type === 'folder') url += `&folderId=${selection.id}`
    else if (selection.type === 'team') url += `&teamId=${selection.id}`
    else if (selection.type === 'tag') url += `&tagId=${selection.id}`
    else if (selection.type === 'unassigned') url += `&unassigned=true`
    api.get(url)
      .then(data => setSiblingFiles(data.content || []))
      .catch(e => handleApiError(e, '카테고리 파일 조회'))
  }, [clubInfo?.clubId, selection?.type, selection?.id])

  // 파일 네비게이션 바 자동 스크롤 (현재 파일 위치로 — 컨테이너 내부만)
  useEffect(() => {
    if (!fileStripRef.current) return
    const activeEl = fileStripRef.current.querySelector('[data-active="true"]')
    if (activeEl) {
      const container = activeEl.closest('.overflow-x-auto') || activeEl.parentElement
      if (container) {
        const offset = activeEl.offsetLeft - container.offsetLeft - container.clientWidth / 2 + activeEl.clientWidth / 2
        container.scrollLeft = offset
      }
    }
  }, [fileId, siblingFiles])

  // 코멘트 로드
  useEffect(() => {
    if (!clubInfo?.clubId || !fileId) return
    fetchComments()
    api.get(`/clubs/${clubInfo.clubId}/drive/files/${fileId}/comments/pages`)
      .then(setCommentedPages)
      .catch(e => handleApiError(e, '코멘트 페이지 조회'))
  }, [clubInfo?.clubId, fileId])

  // 썸네일 사이드바: 현재 페이지로 자동 스크롤
  useEffect(() => {
    if (!thumbnailsOpen || !thumbListRef.current) return
    const activeThumb = thumbListRef.current.querySelector(`[data-page="${currentPage}"]`)
    if (activeThumb) activeThumb.scrollIntoView({ block: 'nearest' })
  }, [currentPage, thumbnailsOpen])

  const fetchComments = () => {
    setCommentLoading(true)
    api.get(`/clubs/${clubInfo.clubId}/drive/files/${fileId}/comments?size=200`)
      .then(data => setComments(data.content || []))
      .catch(e => handleApiError(e, '코멘트 조회'))
      .finally(() => setCommentLoading(false))
  }

  // PDF 뷰어 위 휠 이벤트 → 브라우저 스크롤 차단 (passive: false 필요)
  useEffect(() => {
    const el = viewerRef.current
    if (!el) return
    const preventBrowserScroll = (e) => { e.preventDefault() }
    el.addEventListener('wheel', preventBrowserScroll, { passive: false })
    return () => el.removeEventListener('wheel', preventBrowserScroll)
  }, [fileLoading])

  // ResizeObserver — 뷰포트 가로 + 세로 추적 (file 로드 후 viewerRef 사용 가능)
  useEffect(() => {
    if (!viewerRef.current) return
    const observer = new ResizeObserver(entries => {
      const entry = entries[0]
      if (entry) setViewerSize({ width: entry.contentRect.width, height: entry.contentRect.height })
    })
    observer.observe(viewerRef.current)
    return () => observer.disconnect()
  }, [fileLoading])

  const onDocumentLoadSuccess = async (pdf) => {
    setNumPages(pdf.numPages)
    setCurrentPage(1)
    // 모든 페이지 크기를 한번에 사전 계산 → 페이지 전환 시 떨림 방지
    const sizes = []
    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i)
      const vp = page.getViewport({ scale: 1 })
      sizes.push({ width: vp.width, height: vp.height })
    }
    setPageSizes(sizes)
  }

  const prevPage = () => setCurrentPage(p => Math.max(1, p - 1))
  const nextPage = () => setCurrentPage(p => Math.min(numPages || 1, p + 1))

  // 뷰어 내 마우스 휠 → 페이지 전환
  const wheelCooldown = useRef(false)
  const handleViewerWheel = (e) => {
    if (wheelCooldown.current) return
    // 줌 중이면 무시 (스크롤 가능한 콘텐츠)
    if (zoomMultiplier > 1.05) return
    if (e.deltaY > 0) {
      nextPage()
    } else if (e.deltaY < 0) {
      prevPage()
    }
    wheelCooldown.current = true
    setTimeout(() => { wheelCooldown.current = false }, 200)
  }

  const zoomIn = () => setZoomMultiplier(z => Math.min(z + 0.15, 4))
  const zoomOut = () => setZoomMultiplier(z => Math.max(z - 0.15, 0.3))
  const zoomReset = () => setZoomMultiplier(1)

  // 현재 페이지 크기 (사전 계산된 값, 없으면 기본 A4)
  const currentPageSize = pageSizes[currentPage - 1] || { width: 595, height: 842 }
  const pdfBaseWidth = currentPageSize.width
  const pdfBaseHeight = currentPageSize.height

  // 슬라이드의 가로/세로 중 더 긴 쪽을 해당 뷰포트에 맞춤
  const padding = 40
  let fitScale = 1
  if (viewerSize.width > 0 && viewerSize.height > 0) {
    if (pdfBaseWidth >= pdfBaseHeight) {
      fitScale = (viewerSize.width - padding) / pdfBaseWidth
    } else {
      fitScale = (viewerSize.height - padding) / pdfBaseHeight
    }
  }
  const renderWidth = Math.max(pdfBaseWidth * fitScale * zoomMultiplier, 200)

  const handleDownload = async () => {
    try {
      const token = api.getToken()
      const res = await fetch(`${API_URL}/clubs/${clubInfo.clubId}/drive/files/${fileId}/download`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (!res.ok) throw new Error('다운로드 실패')
      const blob = await res.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = file?.originalName || 'download'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      setTimeout(() => window.URL.revokeObjectURL(url), 1000)
    } catch (err) {
      setAlertModal({ open: true, message: '다운로드 실패' })
    }
  }

  const handleDelete = async () => {
    try {
      await api.delete(`/clubs/${clubInfo.clubId}/drive/files/${fileId}`)
      onBack()
    } catch (err) {
      setAlertModal({ open: true, message: '삭제 실패: ' + (err.message || '') })
    }
  }

  const handleCreateComment = async () => {
    if (!newComment.trim()) return
    try {
      await api.post(`/clubs/${clubInfo.clubId}/drive/files/${fileId}/comments`, {
        content: newComment.trim(),
        pageNumber: commentFilter === 'page' ? currentPage : null,
      })
      setNewComment('')
      fetchComments()
      api.get(`/clubs/${clubInfo.clubId}/drive/files/${fileId}/comments/pages`)
        .then(setCommentedPages).catch(console.error)
    } catch (err) {
      setAlertModal({ open: true, message: '코멘트 작성 실패' })
    }
  }

  const handleDeleteComment = async (commentId) => {
    try {
      await api.delete(`/clubs/${clubInfo.clubId}/drive/files/${fileId}/comments/${commentId}`)
      fetchComments()
      api.get(`/clubs/${clubInfo.clubId}/drive/files/${fileId}/comments/pages`)
        .then(setCommentedPages).catch(console.error)
    } catch (err) {
      setAlertModal({ open: true, message: '코멘트 삭제 실패' })
    }
  }

  // AI 권한 체크
  useEffect(() => {
    if (!clubInfo?.clubId) return
    api.get(`/clubs/${clubInfo.clubId}/ai/permission`)
      .then(data => setAiPermission(data.hasPermission))
      .catch(() => setAiPermission(false))
  }, [clubInfo?.clubId])

  const isPdf = file?.mimeType?.includes('pdf') || file?.originalName?.toLowerCase().endsWith('.pdf')
  const canAiGenerate = aiPermission && isPdf && !file?.linkedPostId && !aiHasActive()

  const openAiModal = async () => {
    try {
      // sidebar API 1회 호출로 게시판 + 팀 + 태그 모두 획득
      const sidebar = await api.get(`/clubs/${clubInfo.clubId}/boards/sidebar`).catch(() => null)
      const boards = (sidebar?.boards || []).filter(b => b.canWrite)
      setMyTeamBoards(boards)

      // 내가 속한 팀 목록만 필터
      const myTeamIds = new Set((clubInfo?.myTeams || []).map(t => t.teamId))
      const teams = (sidebar?.teams || []).filter(t => myTeamIds.has(t.id))
      setMyTeams(teams)

      // 세미카테고리 (일반 태그만)
      setAllTags((sidebar?.tags || []).filter(t => !t.generation && !t.systemTag))

      // 기본 선택: 파일의 팀 → 첫 번째 팀, 첫 번째 게시판
      const fileTeamId = file.teams?.[0]?.teamId
      setSelectedTeamId(teams.find(t => t.id === fileTeamId)?.id || teams[0]?.id || null)
      setSelectedTagIds(file.semiCategories?.filter(c => !c.generation).map(c => c.id) || [])
      setSelectedBoardId(boards.length > 0 ? boards[0].id : null)
      setShowAiModal(true)
    } catch (err) {
      console.error('AI 모달 데이터 로드 실패:', err)
      setAlertModal({ open: true, message: '데이터 로드 실패: ' + (err.message || '') })
    }
  }

  const handleAiGenerate = async () => {
    if (!selectedTeamId) { setAlertModal({ open: true, message: '팀을 선택해주세요' }); return }
    setShowAiModal(false)
    setAiGenerating(true)
    try {
      const result = await api.post(`/clubs/${clubInfo.clubId}/ai/generate`, {
        driveFileId: file.id,
        boardId: selectedBoardId,
        teamId: selectedTeamId,
        semiCategoryIds: selectedTagIds.length > 0 ? selectedTagIds : null,
      })
      // 글로벌 스토어에 작업 등록 → ClubLayout의 AiJobTracker가 추적
      useAiJobStore.getState().startTracking(clubInfo.clubId, result.jobId)
    } catch (err) {
      setAlertModal({ open: true, message: err.message || 'AI 글 생성 요청 실패' })
    } finally {
      setAiGenerating(false)
    }
  }

  // AI 발행/에디터 전달은 이제 AiJobTracker(글로벌)에서 처리

  const canEdit = file && (
    file.uploaderMembershipId === clubInfo?.membershipId ||
    clubInfo?.isExecutive
  )

  const startEditMode = async () => {
    if (!sidebarData) {
      try {
        const data = await api.get(`/clubs/${clubInfo.clubId}/drive/sidebar`)
        setSidebarData(data)
      } catch (e) {
        handleApiError(e, '사이드바 데이터 로드')
        return
      }
    }
    setEditTeamIds(file.teams?.map(t => t.teamId) || [])
    setEditFolderIds(file.folders?.map(f => f.folderId) || [])
    setEditTagIds(file.semiCategories?.map(c => c.id) || [])
    setEditMode(true)
  }

  const handleSaveEdit = async () => {
    if (editTagIds.length === 0) {
      setAlertModal({ open: true, message: '태그를 최소 1개 선택해야 합니다' })
      return
    }
    setEditSaving(true)
    try {
      const updated = await api.put(`/clubs/${clubInfo.clubId}/drive/files/${fileId}`, {
        teamIds: editTeamIds,
        folderIds: editFolderIds,
        semiCategoryIds: editTagIds,
      })
      setFile(updated)
      setEditMode(false)
    } catch (err) {
      setAlertModal({ open: true, message: '속성 수정 실패: ' + (err.message || '') })
    } finally {
      setEditSaving(false)
    }
  }

  const toggleTag = (tagId) => {
    setEditTagIds(prev =>
      prev.includes(tagId) ? prev.filter(id => id !== tagId) : [...prev, tagId]
    )
  }

  const canDelete = file && (
    file.uploaderMembershipId === clubInfo?.membershipId ||
    clubInfo?.isExecutive
  )

  const formatSize = (bytes) => {
    if (!bytes) return ''
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  const filteredComments = commentFilter === 'page'
    ? comments.filter(c => c.pageNumber === currentPage)
    : comments

  const pdfUrl = file?.fileUrl || (file ? `${BASE_URL}/uploads/${file.storedName}` : null)

  if (fileLoading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="animate-spin w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full" />
      </div>
    )
  }

  if (!file) {
    return (
      <div className="flex-1 flex items-center justify-center text-sm text-slate-400">
        파일을 찾을 수 없습니다
      </div>
    )
  }

  return (
    <div className="flex flex-col bg-white rounded-2xl border border-slate-200 shadow-md hover:shadow-lg transition-shadow overflow-hidden">
      {/* 상단 액션바 */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-slate-200 shrink-0">
        <div className="flex items-center gap-2">
          {onToggleSidebar && (
            <button
              onClick={onToggleSidebar}
              className={`p-1.5 rounded-lg cursor-pointer transition-colors ${sidebarOpen ? 'bg-blue-50 text-blue-600' : 'text-slate-400 hover:bg-slate-100 hover:text-slate-600'}`}
              title={sidebarOpen ? '사이드바 접기' : '사이드바 펼치기'}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 3h7v7H3zM3 14h7v7H3zM14 3h7v4H14zM14 10h7v4H14zM14 17h7v4H14z" />
              </svg>
            </button>
          )}
          <button onClick={onBack} className="text-sm text-slate-400 hover:text-slate-600 cursor-pointer">
            &larr; 목록
          </button>
          {file.linkedPostId && (
            <button
              onClick={() => navigate(`/club/boards?post=${file.linkedPostId}`)}
              className="px-3 py-1 text-xs bg-slate-100 text-slate-600 rounded-lg hover:bg-slate-200 cursor-pointer"
            >
              글 보기
            </button>
          )}
          {canEdit && !editMode && (
            <button
              onClick={startEditMode}
              className="px-3 py-1 text-xs bg-amber-50 text-amber-600 rounded-lg hover:bg-amber-100 cursor-pointer"
            >
              속성 편집
            </button>
          )}
        </div>
        <div className="flex items-center gap-2 relative">
          {/* AI 글 생성 버튼 */}
          {canAiGenerate && (
            <button
              onClick={openAiModal}
              disabled={aiGenerating}
              className="px-3 py-1 text-xs bg-violet-500 text-white rounded-lg hover:bg-violet-600 cursor-pointer disabled:opacity-50 flex items-center gap-1"
            >
              <span>✦</span>
              <span className="hidden md:inline">{aiGenerating ? '요청 중...' : 'AI 글 생성'}</span>
            </button>
          )}
          {file?.linkedPostId && isPdf && aiPermission && !aiHasActive() && (
            <span className="text-xs text-slate-400">이미 생성됨</span>
          )}
          <button onClick={handleDownload} className="px-3 py-1 text-xs bg-slate-100 text-slate-600 rounded-lg hover:bg-slate-200 cursor-pointer">
            다운로드
          </button>
          {canDelete && (
            <button onClick={() => setDeleteFileConfirm(true)} className="px-3 py-1 text-xs bg-red-50 text-red-500 rounded-lg hover:bg-red-100 cursor-pointer">
              삭제
            </button>
          )}
        </div>
      </div>

      {/* 파일 정보 */}
      <div className="px-6 py-3 border-b border-slate-100 shrink-0">
        <h3 className="text-base font-bold text-slate-900">{file.originalName}</h3>
        {editMode ? (
          <div className="mt-2 space-y-3">
            {/* 팀 선택 (pill 다중선택) */}
            <div className="flex items-start gap-2">
              <label className="text-xs text-slate-500 shrink-0 w-10 pt-1">팀</label>
              <div className="flex flex-wrap gap-1.5">
                {sidebarData?.teams?.filter(t => t.id != null).map(t => (
                  <button
                    key={t.id}
                    onClick={() => setEditTeamIds(prev => prev.includes(t.id) ? prev.filter(id => id !== t.id) : [...prev, t.id])}
                    className={`px-2 py-0.5 text-xs rounded cursor-pointer transition-colors ${
                      editTeamIds.includes(t.id)
                        ? 'bg-emerald-500 text-white'
                        : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                    }`}
                  >
                    {t.name}
                  </button>
                ))}
                {editTeamIds.length === 0 && (
                  <span className="text-xs text-slate-400 pt-0.5">자유</span>
                )}
              </div>
            </div>
            {/* 폴더 선택 */}
            {sidebarData?.folders?.length > 0 && (
              <div className="flex items-start gap-2">
                <label className="text-xs text-slate-500 shrink-0 w-10 pt-1">폴더</label>
                <div className="flex flex-wrap gap-1.5">
                  {sidebarData.folders.map(f => (
                    <button
                      key={f.id}
                      onClick={() => setEditFolderIds(prev => prev.includes(f.id) ? prev.filter(id => id !== f.id) : [...prev, f.id])}
                      className={`px-2 py-0.5 text-xs rounded cursor-pointer transition-colors ${
                        editFolderIds.includes(f.id)
                          ? 'bg-violet-500 text-white'
                          : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                      }`}
                    >
                      {f.name}
                    </button>
                  ))}
                  {editFolderIds.length === 0 && (
                    <span className="text-xs text-slate-400 pt-0.5">없음</span>
                  )}
                </div>
              </div>
            )}
            {/* 태그 선택 */}
            <div className="flex items-start gap-2">
              <label className="text-xs text-slate-500 shrink-0 w-10 pt-1">태그</label>
              <div className="flex flex-wrap gap-1.5">
                {sidebarData?.tags?.map(tag => (
                  <button
                    key={tag.id}
                    onClick={() => toggleTag(tag.id)}
                    className={`px-2 py-0.5 text-xs rounded cursor-pointer transition-colors ${
                      editTagIds.includes(tag.id)
                        ? 'bg-primary text-white'
                        : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                    }`}
                  >
                    {tag.name}
                  </button>
                ))}
              </div>
            </div>
            {/* 저장/취소 */}
            <div className="flex items-center gap-2">
              <button
                onClick={handleSaveEdit}
                disabled={editSaving || editTagIds.length === 0}
                className="px-3 py-1 text-xs bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer"
              >
                {editSaving ? '저장 중...' : '저장'}
              </button>
              <button
                onClick={() => setEditMode(false)}
                className="px-3 py-1 text-xs bg-slate-100 text-slate-600 rounded-lg hover:bg-slate-200 cursor-pointer"
              >
                취소
              </button>
              {editTagIds.length === 0 && (
                <span className="text-xs text-red-500">태그를 최소 1개 선택하세요</span>
              )}
            </div>
          </div>
        ) : (
          <div className="mt-1 flex items-center gap-2 text-xs text-slate-400 flex-wrap">
            {file.teams?.length > 0 && <span>[{file.teams.map(t => t.teamName).join(', ')}]</span>}
            <span>{file.uploaderName}</span>
            <span>·</span>
            <span>{formatSize(file.fileSize)}</span>
            {file.folders?.map(f => (
              <span key={f.folderId} className="px-2 py-0.5 bg-violet-50 text-violet-600 rounded text-xs">
                {f.folderName}
              </span>
            ))}
            {file.semiCategories?.map(cat => (
              <span key={cat.id} className="px-2 py-0.5 bg-blue-50 text-blue-600 rounded text-xs">
                {cat.name}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* PDF 뷰어 + 코멘트 하단 */}
      <div className="flex-1 flex flex-col min-h-0">
        {/* PDF 뷰어 */}
        <div className="flex-1 flex flex-col min-h-0">
          {/* 툴바 */}
          <div className="flex items-center justify-between px-4 py-2 bg-slate-50 border-b border-slate-100 shrink-0">
            <div className="flex items-center gap-2">
              {/* 썸네일 사이드바 토글 */}
              <button
                onClick={() => setThumbnailsOpen(prev => !prev)}
                className={`px-2 py-1 text-xs rounded cursor-pointer ${thumbnailsOpen ? 'bg-blue-100 text-blue-600' : 'text-slate-500 hover:bg-slate-200'}`}
                title="페이지 목록"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 6h16M4 10h16M4 14h16M4 18h16" />
                </svg>
              </button>
              <div className="w-px h-4 bg-slate-200" />
              <button onClick={prevPage} disabled={currentPage <= 1}
                className="px-2 py-1 text-xs text-slate-500 hover:bg-slate-200 rounded disabled:opacity-30 cursor-pointer">&lt;</button>
              <span className="text-xs text-slate-600">{currentPage} / {numPages || '?'}</span>
              <button onClick={nextPage} disabled={currentPage >= (numPages || 1)}
                className="px-2 py-1 text-xs text-slate-500 hover:bg-slate-200 rounded disabled:opacity-30 cursor-pointer">&gt;</button>
            </div>
            <div className="flex items-center gap-1">
              <button onClick={zoomOut} className="px-2 py-1 text-xs text-slate-500 hover:bg-slate-200 rounded cursor-pointer">-</button>
              <button onClick={zoomReset} className="px-3 py-1 text-xs text-slate-600 hover:bg-slate-200 rounded cursor-pointer">
                {Math.round(zoomMultiplier * 100)}%
              </button>
              <button onClick={zoomIn} className="px-2 py-1 text-xs text-slate-500 hover:bg-slate-200 rounded cursor-pointer">+</button>
            </div>
          </div>

          {/* 썸네일 사이드바 + PDF 렌더링 */}
          <div className="flex-1 flex min-h-0">
            {/* 페이지 썸네일 사이드바 */}
            {thumbnailsOpen && pdfUrl && numPages && (
              <div ref={thumbListRef} className="w-36 shrink-0 overflow-y-auto bg-slate-50 border-r border-slate-200 p-2 flex flex-col gap-2"
                onWheel={e => e.stopPropagation()}>
                {Array.from({ length: numPages }, (_, i) => i + 1).map(p => (
                  <button
                    key={p}
                    data-page={p}
                    onClick={() => setCurrentPage(p)}
                    className={`rounded overflow-hidden cursor-pointer ring-2 transition-all ${
                      p === currentPage ? 'ring-blue-500' : 'ring-transparent hover:ring-slate-300'
                    }`}
                  >
                    <Document file={pdfUrl} options={PDF_OPTIONS} loading="">
                      <Page pageNumber={p} width={120} renderTextLayer={false} renderAnnotationLayer={false} />
                    </Document>
                    <div className={`text-center text-xs py-0.5 ${p === currentPage ? 'bg-primary text-white' : 'bg-slate-100 text-slate-500'}`}>
                      {p}
                    </div>
                  </button>
                ))}
              </div>
            )}

            {/* PDF 렌더링 — 단일 페이지, 가운데 정렬, 휠 → 페이지 전환 */}
            <div ref={viewerRef} className="flex-1 overflow-auto bg-slate-100" onWheel={handleViewerWheel}>
            <div className="min-h-full flex items-center justify-center p-4"
              style={{ minHeight: renderWidth > 200 ? Math.round(pdfBaseHeight * fitScale * zoomMultiplier + padding) : undefined }}>
              {pdfUrl && (
                <Document
                  file={pdfUrl}
                  options={PDF_OPTIONS}
                  onLoadSuccess={onDocumentLoadSuccess}
                  onLoadError={(err) => setPdfError(err.message)}
                  loading={<div className="p-8 text-center text-sm text-slate-400">PDF 로딩 중...</div>}
                >
                  <div className="bg-white shadow-sm rounded relative" style={{ width: renderWidth }}>
                    <Page
                      pageNumber={currentPage}
                      width={renderWidth}
                      renderTextLayer={true}
                      renderAnnotationLayer={true}
                    />
                    <div className="absolute bottom-2 right-2 px-2 py-0.5 bg-black/50 text-white text-xs rounded">
                      {currentPage}
                    </div>
                    {commentedPages.includes(currentPage) && (
                      <button
                        onClick={() => { setCommentsOpen(true); setCommentFilter('page') }}
                        className="absolute top-2 right-2 px-2 py-0.5 bg-primary text-white text-xs rounded cursor-pointer"
                      >
                        💬
                      </button>
                    )}
                  </div>
                </Document>
              )}
              {pdfError && (
                <div className="p-8 text-center text-sm text-red-500">PDF 로드 실패: {pdfError}</div>
              )}
            </div>
          </div>
          </div>
        </div>

        {/* 코멘트 섹션 (하단, 접기/펼치기) */}
        <div className="shrink-0 border-t border-slate-200 flex flex-col bg-white">
          {/* 코멘트 헤더 (토글) */}
          <div
            onClick={() => setCommentsOpen(prev => !prev)}
            className="flex items-center gap-2 px-4 py-2.5 hover:bg-slate-50 cursor-pointer shrink-0"
            role="button"
            tabIndex={0}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setCommentsOpen(prev => !prev) }}
          >
            <span className="text-sm font-semibold text-slate-900">코멘트</span>
            <span className="text-xs text-slate-400">({comments.length})</span>
            <div className="flex-1" />
            {commentsOpen && (
              <>
                <button
                  onClick={(e) => { e.stopPropagation(); setCommentFilter('all') }}
                  className={`px-2 py-0.5 text-xs rounded cursor-pointer ${commentFilter === 'all' ? 'bg-blue-50 text-blue-600' : 'text-slate-400 hover:bg-slate-50'}`}
                >전체</button>
                <button
                  onClick={(e) => { e.stopPropagation(); setCommentFilter('page') }}
                  className={`px-2 py-0.5 text-xs rounded cursor-pointer ${commentFilter === 'page' ? 'bg-blue-50 text-blue-600' : 'text-slate-400 hover:bg-slate-50'}`}
                >p.{currentPage}</button>
              </>
            )}
            <span className="text-xs text-slate-400">{commentsOpen ? '▼' : '▲'}</span>
          </div>

          {commentsOpen && (
            <>
              {/* 코멘트 목록 */}
              <div className="border-t border-slate-100">
                {filteredComments.length === 0 ? (
                  <div className="p-4 text-xs text-slate-400 text-center">코멘트가 없습니다</div>
                ) : (
                  filteredComments.map(c => (
                    <div key={c.id} className="px-4 py-3 border-b border-slate-50 group">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-medium text-slate-700">{c.authorName}</span>
                          {c.pageNumber != null && (
                            <button
                              onClick={() => setCurrentPage(c.pageNumber)}
                              className="text-xs px-1.5 py-0.5 bg-slate-100 text-slate-500 rounded cursor-pointer hover:bg-slate-200"
                            >p.{c.pageNumber}</button>
                          )}
                        </div>
                        {(c.authorMembershipId === clubInfo?.membershipId || clubInfo?.isExecutive) && (
                          <button
                            onClick={() => setDeleteCommentId(c.id)}
                            className="text-xs text-slate-300 hover:text-red-500 opacity-0 group-hover:opacity-100 cursor-pointer"
                          >삭제</button>
                        )}
                      </div>
                      <p className="mt-1 text-sm text-slate-600 whitespace-pre-wrap break-words">{c.content}</p>
                    </div>
                  ))
                )}
              </div>

              {/* 코멘트 입력 */}
              <div className="px-4 py-3 border-t border-slate-100 shrink-0">
                <div className="flex gap-2 items-end">
                  <textarea
                    value={newComment}
                    onChange={e => setNewComment(e.target.value)}
                    placeholder={commentFilter === 'page' ? `p.${currentPage}에 코멘트...` : '코멘트 작성...'}
                    rows={2}
                    className={`flex-1 px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 resize-none ${
                      newComment.length > 2000 ? 'border-red-300 focus:ring-red-400' : 'border-slate-200 focus:ring-blue-500/20'
                    }`}
                  />
                  <button
                    onClick={handleCreateComment}
                    disabled={!newComment.trim() || newComment.length > 2000}
                    className="px-3 py-2 bg-primary text-white text-xs rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer shrink-0"
                  >전송</button>
                </div>
                {newComment.length > 2000 && (
                  <p className="text-xs text-red-500 mt-1">{newComment.length}/2000자 — 최대 길이를 초과했습니다</p>
                )}
              </div>
            </>
          )}
        </div>
      </div>

      {/* 파일 네비게이션 (게시판 글 목록 스타일) */}
      {siblingFiles.length > 1 && (() => {
        const currentIdx = siblingFiles.findIndex(f => String(f.id) === String(fileId))
        const prevFile = currentIdx > 0 ? siblingFiles[currentIdx - 1] : null
        const nextFile = currentIdx >= 0 && currentIdx < siblingFiles.length - 1 ? siblingFiles[currentIdx + 1] : null

        return (
          <div ref={fileStripRef} className="border-t border-slate-200">
            {/* 이전/다음 파일 */}
            <div className="px-6 py-3 border-b border-slate-100 flex items-center justify-between">
              {prevFile ? (
                <button
                  onClick={() => onFileClick?.(prevFile)}
                  className="flex items-center gap-2 text-sm text-slate-600 hover:text-blue-600 transition-colors min-w-0 cursor-pointer"
                >
                  <span className="text-xs text-slate-400 shrink-0">▲ 이전</span>
                  <span className="truncate">{prevFile.originalName?.replace(/\.pdf$/i, '')}</span>
                </button>
              ) : <div />}
              {nextFile ? (
                <button
                  onClick={() => onFileClick?.(nextFile)}
                  className="flex items-center gap-2 text-sm text-slate-600 hover:text-blue-600 transition-colors min-w-0 text-right cursor-pointer"
                >
                  <span className="truncate">{nextFile.originalName?.replace(/\.pdf$/i, '')}</span>
                  <span className="text-xs text-slate-400 shrink-0">다음 ▼</span>
                </button>
              ) : <div />}
            </div>

            {/* 파일 목록 */}
            <div className="px-6 py-4">
              <h3 className="text-sm font-semibold text-slate-700 mb-3">
                {selection?.name || '파일 목록'}
              </h3>
              <div className="space-y-0.5">
                {siblingFiles.map(f => {
                  const isCurrent = String(f.id) === String(fileId)
                  return (
                    <button
                      key={f.id}
                      data-active={isCurrent ? 'true' : 'false'}
                      onClick={() => !isCurrent && onFileClick?.(f)}
                      className={`w-full text-left px-3 py-2 rounded flex items-center justify-between ${
                        isCurrent
                          ? 'bg-blue-50 border border-blue-200 cursor-default'
                          : 'hover:bg-slate-50 cursor-pointer group'
                      }`}
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <svg className={`w-3.5 h-3.5 shrink-0 ${isCurrent ? 'text-blue-400' : 'text-red-400'}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                        </svg>
                        <span className={`text-sm truncate ${
                          isCurrent ? 'text-blue-700 font-medium' : 'text-slate-700 group-hover:text-blue-600'
                        }`}>
                          {f.originalName?.replace(/\.pdf$/i, '')}
                        </span>
                      </div>
                      <span className={`text-xs shrink-0 ml-2 ${
                        isCurrent ? 'text-blue-400' : 'text-slate-400'
                      }`}>
                        {f.uploaderName}
                      </span>
                    </button>
                  )
                })}
              </div>
            </div>
          </div>
        )
      })()}

      {/* 파일 삭제 확인 모달 */}
      {deleteFileConfirm && (
        <ConfirmModal
          title="파일 삭제"
          message={`"${file?.originalName}"을(를) 삭제하시겠습니까?`}
          confirmLabel="삭제"
          onConfirm={handleDelete}
          onClose={() => setDeleteFileConfirm(false)}
          danger
        />
      )}

      {/* 코멘트 삭제 확인 모달 */}
      {deleteCommentId && (
        <ConfirmModal
          title="코멘트 삭제"
          message="이 코멘트를 삭제하시겠습니까?"
          confirmLabel="삭제"
          onConfirm={() => handleDeleteComment(deleteCommentId)}
          onClose={() => setDeleteCommentId(null)}
          danger
        />
      )}

      {/* AI 글 생성 모달 */}
      {showAiModal && createPortal(
        <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/40" onClick={() => setShowAiModal(false)}>
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 overflow-hidden" onClick={e => e.stopPropagation()}>
            <div className="px-5 py-4 border-b border-slate-100">
              <h3 className="text-base font-bold text-slate-900">AI 글 생성</h3>
              <p className="text-xs text-slate-400 mt-1">게시판과 태그를 선택하세요</p>
            </div>
            <div className="px-5 py-4 space-y-4">
              {/* 팀 선택 */}
              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">팀</label>
                {myTeams.length === 0 ? (
                  <p className="text-xs text-slate-400">소속 팀이 없습니다</p>
                ) : (
                  <div className="flex flex-wrap gap-1.5">
                    {myTeams.map(t => (
                      <button
                        key={t.id}
                        onClick={() => setSelectedTeamId(t.id)}
                        className={`px-3 py-1.5 text-xs rounded-full cursor-pointer transition-colors ${
                          selectedTeamId === t.id
                            ? 'bg-violet-500 text-white'
                            : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                        }`}
                      >
                        {t.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              {/* 게시판 선택 */}
              <div>
                <label className="text-sm font-medium text-slate-700 mb-2 block">게시판 (선택)</label>
                {myTeamBoards.length === 0 ? (
                  <p className="text-xs text-slate-400">작성 가능한 게시판이 없습니다</p>
                ) : (
                  <div className="flex flex-wrap gap-1.5">
                    {myTeamBoards.map(b => (
                      <button
                        key={b.id}
                        onClick={() => setSelectedBoardId(prev => prev === b.id ? null : b.id)}
                        className={`px-3 py-1.5 text-xs rounded-full cursor-pointer transition-colors ${
                          selectedBoardId === b.id
                            ? 'bg-emerald-500 text-white'
                            : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                        }`}
                      >
                        {b.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              {/* 태그 선택 */}
              {allTags.length > 0 && (
                <div>
                  <label className="text-sm font-medium text-slate-700 mb-2 block">태그 (선택)</label>
                  <div className="flex flex-wrap gap-1.5">
                    {allTags.map(tag => (
                      <button
                        key={tag.id}
                        onClick={() => setSelectedTagIds(prev =>
                          prev.includes(tag.id) ? prev.filter(id => id !== tag.id) : [...prev, tag.id]
                        )}
                        className={`px-2.5 py-1 text-xs rounded-full cursor-pointer transition-colors ${
                          selectedTagIds.includes(tag.id)
                            ? 'bg-primary text-white'
                            : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                        }`}
                      >
                        {tag.name}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
            <div className="px-5 py-3 border-t border-slate-100 flex items-center justify-end gap-2">
              <button
                onClick={() => setShowAiModal(false)}
                className="px-4 py-2 text-sm text-slate-500 hover:bg-slate-50 rounded-lg cursor-pointer"
              >
                취소
              </button>
              <button
                onClick={handleAiGenerate}
                disabled={!selectedTeamId}
                className="px-4 py-2 text-sm bg-violet-500 text-white rounded-lg hover:bg-violet-600 disabled:opacity-50 cursor-pointer"
              >
                생성 시작
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* 알림 모달 */}
      {alertModal.open && (
        <ConfirmModal
          title="알림"
          message={alertModal.message}
          onConfirm={() => setAlertModal({ open: false, message: '' })}
          onClose={() => setAlertModal({ open: false, message: '' })}
          confirmLabel="확인"
          cancelLabel=""
        />
      )}
    </div>
  )
}
