import { useEffect, useState, useRef } from 'react'
import { useOutletContext } from 'react-router-dom'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import Pagination from './Pagination'
import DriveUploadModal from './DriveUploadModal'
import { FileSkeleton } from './SkeletonRow'
import handleApiError from '../lib/handleApiError'

export default function DriveFileList({ selection, onFileClick }) {
  const { clubInfo } = useOutletContext()
  const [files, setFiles] = useState([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const [uploadFile, setUploadFile] = useState(null)
  const dragCounter = useRef(0)

  // 교차 필터
  const [crossFilterId, setCrossFilterId] = useState(null)
  const [crossOptions, setCrossOptions] = useState([])

  // 전체뷰 다중 필터
  const [selectedTeamIds, setSelectedTeamIds] = useState([])
  const [selectedTagIds, setSelectedTagIds] = useState([])
  const [allTeams, setAllTeams] = useState([])
  const [allTags, setAllTags] = useState([])

  useEffect(() => { setPage(0); setCrossFilterId(null); setSelectedTeamIds([]); setSelectedTagIds([]) }, [selection.type, selection.id])

  // 교차 필터 옵션 로드 + 전체뷰 팀/태그 목록
  useEffect(() => {
    if (!clubInfo?.clubId) return
    if (selection.type === 'all') {
      api.get(`/clubs/${clubInfo.clubId}/drive/sidebar`)
        .then(data => {
          setAllTeams((data?.teams || []).filter(t => t.id != null && !t.systemTeam))
          setAllTags((data?.tags || []).filter(t => !t.systemTag && !t.generation))
        })
        .catch(() => { setAllTeams([]); setAllTags([]) })
      setCrossOptions([])
    } else if (selection.type === 'team' || selection.type === 'unassigned') {
      api.get(`/clubs/${clubInfo.clubId}/semi-categories`)
        .then(data => setCrossOptions(data || []))
        .catch(() => setCrossOptions([]))
      setAllTeams([]); setAllTags([])
    } else if (selection.type === 'tag') {
      api.get(`/clubs/${clubInfo.clubId}/drive/sidebar`)
        .then(data => setCrossOptions((data?.teams || []).filter(t => t.id != null)))
        .catch(() => setCrossOptions([]))
      setAllTeams([]); setAllTags([])
    } else {
      setCrossOptions([]); setAllTeams([]); setAllTags([])
    }
  }, [clubInfo?.clubId, selection.type, selection.id])

  useEffect(() => {
    if (!clubInfo?.clubId) return
    fetchFiles()
  }, [clubInfo?.clubId, selection.type, selection.id, page, crossFilterId, selectedTeamIds, selectedTagIds])

  const fetchFiles = () => {
    setLoading(true)
    let url = `/clubs/${clubInfo.clubId}/drive/files?page=${page}&size=20`
    if (selection.type === 'all') {
      if (selectedTeamIds.length > 0) url += selectedTeamIds.map(id => `&teamIds=${id}`).join('')
      if (selectedTagIds.length > 0) url += selectedTagIds.map(id => `&tagIds=${id}`).join('')
    } else if (selection.type === 'folder') {
      url += `&folderId=${selection.id}`
    } else if (selection.type === 'team') {
      url += `&teamIds=${selection.id}`
      if (crossFilterId) url += `&tagIds=${crossFilterId}`
    } else if (selection.type === 'tag') {
      url += `&tagIds=${selection.id}`
      if (crossFilterId) url += `&teamIds=${crossFilterId}`
    } else if (selection.type === 'unassigned') {
      url += `&unassigned=true`
      if (crossFilterId) url += `&tagIds=${crossFilterId}`
    }

    api.get(url)
      .then(data => {
        setFiles(data.content || [])
        setTotalPages(data.totalPages || 0)
      })
      .catch(e => handleApiError(e, '드라이브 파일 조회'))
      .finally(() => setLoading(false))
  }

  const handleDragEnter = (e) => {
    e.preventDefault()
    dragCounter.current++
    setIsDragging(true)
  }
  const handleDragLeave = (e) => {
    e.preventDefault()
    dragCounter.current--
    if (dragCounter.current === 0) setIsDragging(false)
  }
  const handleDragOver = (e) => e.preventDefault()
  const handleDrop = (e) => {
    e.preventDefault()
    dragCounter.current = 0
    setIsDragging(false)
    const file = e.dataTransfer?.files?.[0]
    if (file) setUploadFile(file)
  }

  const handleUploadButton = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.pdf'
    input.onchange = (e) => {
      const file = e.target.files?.[0]
      if (file) setUploadFile(file)
    }
    input.click()
  }

  const formatSize = (bytes) => {
    if (!bytes) return ''
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  const isNew = (createdAt) => {
    const diff = Date.now() - new Date(createdAt).getTime()
    return diff < 24 * 60 * 60 * 1000
  }

  return (
    <div
      className="flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden relative"
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
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
      {/* 상단 바 */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
        <h3 className="text-base font-semibold text-slate-900">{selection.name || '드라이브'}</h3>
        <button
          onClick={handleUploadButton}
          className="px-4 py-1.5 bg-primary text-white text-sm rounded-lg hover:bg-primary-hover transition-colors cursor-pointer"
        >
          + 업로드
        </button>
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
      {crossOptions.length > 0 && (selection.type === 'team' || selection.type === 'tag' || selection.type === 'unassigned') && (
        <div className="px-6 py-2 border-b border-slate-100 flex items-center gap-2 flex-wrap">
          <span className="text-xs text-slate-400 shrink-0">
            {selection.type === 'tag' ? '팀:' : '태그:'}
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
                    ? selection.type === 'tag' ? 'bg-emerald-500 text-white border-emerald-500' : 'bg-primary text-white border-blue-500'
                    : 'bg-white text-slate-500 border-slate-200 hover:border-slate-400'
                }`}
              >
                {optName}
              </button>
            )
          })}
        </div>
      )}

      {/* DnD 오버레이 */}
      {isDragging && (
        <div className="absolute inset-0 z-10 bg-blue-50/80 border-2 border-dashed border-blue-400 rounded-lg flex items-center justify-center pointer-events-none">
          <div className="text-center">
            <svg className="w-12 h-12 text-blue-400 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
            </svg>
            <p className="text-sm font-medium text-blue-600">파일을 여기에 끌어다 놓기</p>
          </div>
        </div>
      )}

      {/* 파일 목록 */}
      <div>
        {loading ? (
          <FileSkeleton count={5} />
        ) : files.length === 0 ? (
          <div className="p-12 text-center">
            <svg className="w-12 h-12 text-slate-300 mx-auto mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
            </svg>
            <p className="text-sm text-slate-400">파일이 없습니다</p>
            <p className="text-xs text-slate-300 mt-1">파일을 드래그하거나 업로드 버튼을 클릭하세요</p>
          </div>
        ) : (
          files.map(file => (
            <button
              key={file.id}
              onClick={() => onFileClick(file)}
              className="w-full text-left px-6 py-3 border-b border-slate-50 hover:bg-slate-50 transition-colors flex items-center gap-4 group cursor-pointer"
            >
              {/* PDF 아이콘 */}
              <div className="w-10 h-10 bg-red-50 rounded-lg flex items-center justify-center shrink-0">
                <svg className="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                </svg>
              </div>

              <div className="flex-1 min-w-0">
                {/* 파일명 + 태그 (현재 필터 태그는 숨김) */}
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-medium text-sm text-slate-900 truncate">{file.originalName}</span>
                  {file.semiCategories?.filter(cat => selection.type !== 'tag' || cat.id !== selection.id).map(cat => (
                    <span key={cat.id} className="shrink-0 px-2 py-0.5 text-xs rounded bg-blue-50 text-blue-600">
                      {cat.name}
                    </span>
                  ))}
                  {isNew(file.createdAt) && (
                    <span className="shrink-0 px-1.5 py-0.5 text-xs rounded bg-green-50 text-green-600 font-medium">NEW</span>
                  )}
                </div>
                {/* 메타 */}
                <div className="mt-1 text-xs text-slate-400 flex items-center gap-1.5 flex-wrap">
                  {file.teams?.length > 0 && <span>[{file.teams.map(t => t.teamName).join(', ')}]</span>}
                  <span>{file.uploaderName}</span>
                  <span>·</span>
                  <span>{formatTime(file.createdAt)}</span>
                  <span>·</span>
                  <span>{formatSize(file.fileSize)}</span>
                  {file.commentCount > 0 && (
                    <>
                      <span>·</span>
                      <span>💬 {file.commentCount}</span>
                    </>
                  )}
                </div>
              </div>
            </button>
          ))
        )}
      </div>

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className="border-t border-slate-100 py-3">
          <Pagination page={page} totalPages={totalPages} setPage={setPage} />
        </div>
      )}

      {/* 업로드 모달 */}
      {uploadFile && (
        <DriveUploadModal
          file={uploadFile}
          defaultTeamId={selection.type === 'team' ? selection.id : undefined}
          defaultFolderId={selection.type === 'folder' ? selection.id : undefined}
          onClose={() => setUploadFile(null)}
          onUploaded={() => fetchFiles()}
        />
      )}
    </div>
  )
}
