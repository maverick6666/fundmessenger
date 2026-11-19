import { useState, useEffect } from 'react'
import api from '../lib/api'
import { API_URL } from '../lib/api'
import { formatTime } from '../lib/format'
import handleApiError from '../lib/handleApiError'
import NewsMarkdownRenderer from './NewsMarkdownRenderer'

export default function ArchiveView({ clubId, generation, onBack }) {
  const [tab, setTab] = useState('teams')
  const [summary, setSummary] = useState(null)
  const [teams, setTeams] = useState([])
  const [posts, setPosts] = useState([])
  const [files, setFiles] = useState([])
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selectedPost, setSelectedPost] = useState(null)
  const [selectedFile, setSelectedFile] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)

  useEffect(() => {
    loadData()
  }, [generation.id])

  const loadData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [summaryData, teamsData, membersData] = await Promise.all([
        api.get(`/clubs/${clubId}/archive?generationId=${generation.id}`),
        api.get(`/clubs/${clubId}/archive/teams?generationId=${generation.id}`),
        api.get(`/clubs/${clubId}/archive/members?generationId=${generation.id}`),
      ])
      setSummary(summaryData)
      setTeams(teamsData)
      setMembers(membersData)
    } catch (err) {
      handleApiError(err, '아카이브 로드')
      setError('아카이브 데이터를 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }

  const loadPostDetail = async (postId) => {
    setDetailLoading(true)
    try {
      const data = await api.get(`/clubs/${clubId}/boards/posts/${postId}`)
      setSelectedPost(data)
    } catch (err) {
      handleApiError(err, '글 상세 조회')
    } finally {
      setDetailLoading(false)
    }
  }

  const loadFileDetail = async (file) => {
    setSelectedFile(file)
  }

  const loadPosts = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/archive/posts?generationId=${generation.id}&size=50`)
      setPosts(data.content || [])
    } catch (err) {
      handleApiError(err, '아카이브 게시글 조회')
      setError('게시글을 불러오지 못했습니다')
    }
  }

  const loadFiles = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/archive/files?generationId=${generation.id}&size=50`)
      setFiles(data.content || [])
    } catch (err) {
      handleApiError(err, '아카이브 파일 조회')
      setError('파일을 불러오지 못했습니다')
    }
  }

  useEffect(() => {
    if (tab === 'posts' && posts.length === 0) loadPosts()
    if (tab === 'files' && files.length === 0) loadFiles()
  }, [tab])

  const tabs = [
    { key: 'teams', label: '팀' },
    { key: 'posts', label: '게시글' },
    { key: 'files', label: '자료실' },
    { key: 'members', label: '멤버' },
  ]

  if (loading) {
    return <div className="text-center py-12 text-slate-400">아카이브 불러오는 중...</div>
  }

  if (error && !summary) {
    return (
      <div className="text-center py-12">
        <p className="text-sm text-red-500 mb-3">{error}</p>
        <button onClick={loadData} className="text-sm text-blue-500 hover:text-blue-700 cursor-pointer">재시도</button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {error && <p className="text-xs text-red-500 px-1">{error}</p>}
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
          >
            ← 현재로 돌아가기
          </button>
          <span className="inline-flex items-center px-3 py-1 bg-indigo-50 text-indigo-700 rounded-full text-sm font-semibold">
            {generation.name} 아카이브
          </span>
        </div>
      </div>

      {/* 요약 카드 */}
      {summary && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[
            { label: '팀', value: summary.teamCount },
            { label: '멤버', value: summary.memberCount },
            { label: '게시글', value: summary.postCount },
            { label: '파일', value: summary.fileCount },
          ].map(s => (
            <div key={s.label} className="bg-white border rounded-xl p-4 text-center">
              <div className="text-2xl font-bold text-slate-900">{s.value}</div>
              <div className="text-xs text-slate-500 mt-1">{s.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* 탭 */}
      <div className="flex gap-1 border-b">
        {tabs.map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors cursor-pointer ${
              tab === t.key
                ? 'border-primary text-primary'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* 탭 콘텐츠 */}
      <div>
        {tab === 'teams' && <ArchiveTeams teams={teams} />}
        {tab === 'posts' && <ArchivePosts posts={posts} onPostClick={loadPostDetail} />}
        {tab === 'files' && <ArchiveFiles files={files} clubId={clubId} onFileClick={loadFileDetail} />}
        {tab === 'members' && <ArchiveMembers members={members} />}
      </div>

      {/* 글 상세 읽기전용 모달 */}
      {selectedPost && (
        <PostDetailModal post={selectedPost} loading={detailLoading} onClose={() => setSelectedPost(null)} />
      )}

      {/* 파일 상세 읽기전용 모달 */}
      {selectedFile && (
        <FileDetailModal file={selectedFile} clubId={clubId} onClose={() => setSelectedFile(null)} />
      )}
    </div>
  )
}

function ArchiveTeams({ teams }) {
  if (teams.length === 0) return <EmptyState text="이 기수에 등록된 팀이 없습니다" />
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {teams.map(t => (
        <div key={t.id} className="bg-white border rounded-xl p-4">
          <div className="font-medium text-slate-900">{t.name}</div>
          {t.description && <p className="text-xs text-slate-500 mt-1">{t.description}</p>}
          <div className="text-xs text-slate-400 mt-2">{t.memberCount}명</div>
        </div>
      ))}
    </div>
  )
}

function ArchivePosts({ posts, onPostClick }) {
  if (posts.length === 0) return <EmptyState text="이 기수에 태그된 게시글이 없습니다" />
  return (
    <div className="space-y-2">
      {posts.map(p => (
        <div
          key={p.id}
          onClick={() => onPostClick(p.id)}
          className="bg-white border rounded-xl p-4 flex items-center justify-between cursor-pointer hover:border-primary/30 hover:bg-slate-50 transition-colors"
        >
          <div>
            <div className="font-medium text-slate-900 text-sm">{p.title}</div>
            <div className="text-xs text-slate-500 mt-1">{p.authorName} · {p.boardName}</div>
          </div>
          <div className="text-xs text-slate-400">{new Date(p.createdAt).toLocaleDateString('ko')}</div>
        </div>
      ))}
    </div>
  )
}

function ArchiveFiles({ files, clubId, onFileClick }) {
  if (files.length === 0) return <EmptyState text="이 기수에 태그된 파일이 없습니다" />
  return (
    <div className="space-y-2">
      {files.map(f => (
        <div
          key={f.id}
          onClick={() => onFileClick(f)}
          className="bg-white border rounded-xl p-4 flex items-center justify-between cursor-pointer hover:border-primary/30 hover:bg-slate-50 transition-colors"
        >
          <div>
            <div className="font-medium text-slate-900 text-sm">{f.originalName}</div>
            <div className="text-xs text-slate-500 mt-1">{f.uploaderName} · {f.teamName || '자유'}</div>
          </div>
          <div className="text-xs text-slate-400">{new Date(f.createdAt).toLocaleDateString('ko')}</div>
        </div>
      ))}
    </div>
  )
}

function ArchiveMembers({ members }) {
  if (members.length === 0) return <EmptyState text="이 기수에 등록된 멤버가 없습니다" />
  return (
    <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
      {members.map(m => (
        <div key={m.id} className="bg-white border rounded-xl p-4 flex items-center gap-3">
          <div className="w-9 h-9 bg-slate-100 rounded-full flex items-center justify-center text-sm font-medium text-slate-500">
            {m.name.charAt(0)}
          </div>
          <div>
            <div className="font-medium text-slate-900 text-sm">{m.name}</div>
            <div className="text-xs text-slate-500">{m.studentId} · {m.firstGeneration}</div>
          </div>
        </div>
      ))}
    </div>
  )
}

function EmptyState({ text }) {
  return (
    <div className="text-center py-12 text-slate-400 text-sm">{text}</div>
  )
}

/** 읽기전용 글 상세 모달 */
function PostDetailModal({ post, loading, onClose }) {
  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/40" onClick={onClose}>
      <div
        className="bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[80vh] overflow-y-auto mx-4"
        onClick={e => e.stopPropagation()}
      >
        <div className="sticky top-0 bg-white border-b px-6 py-4 flex items-center justify-between rounded-t-2xl">
          <h2 className="text-lg font-bold text-slate-900 truncate">{post.title}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 text-xl cursor-pointer">✕</button>
        </div>
        <div className="px-6 py-4">
          <div className="flex items-center gap-2 text-xs text-slate-500 mb-4">
            <span>{post.authorName}</span>
            <span>·</span>
            <span>{formatTime(post.createdAt)}</span>
            {post.aiGenerated && (
              <span className="px-1.5 py-0.5 bg-violet-100 text-violet-600 rounded text-xs">AI</span>
            )}
          </div>
          {loading ? (
            <div className="text-center py-8 text-slate-400">불러오는 중...</div>
          ) : (
            <div className="prose prose-sm max-w-none">
              <NewsMarkdownRenderer content={post.content || ''} />
            </div>
          )}
        </div>
        <div className="border-t px-6 py-3 text-center">
          <span className="text-xs text-slate-400">읽기 전용 — 아카이브 모드</span>
        </div>
      </div>
    </div>
  )
}

/** 읽기전용 파일 상세 모달 */
function FileDetailModal({ file, clubId, onClose }) {
  const downloadUrl = `${API_URL}/files/download/${file.storedName}?name=${encodeURIComponent(file.originalName)}`

  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/40" onClick={onClose}>
      <div
        className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4"
        onClick={e => e.stopPropagation()}
      >
        <div className="sticky top-0 bg-white border-b px-6 py-4 flex items-center justify-between rounded-t-2xl">
          <h2 className="text-base font-bold text-slate-900 truncate">{file.originalName}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 text-xl cursor-pointer">✕</button>
        </div>
        <div className="px-6 py-5 space-y-3">
          <div className="grid grid-cols-2 gap-2 text-sm">
            <div className="text-slate-500">업로더</div>
            <div className="text-slate-900">{file.uploaderName}</div>
            <div className="text-slate-500">팀</div>
            <div className="text-slate-900">{file.teamName || '자유'}</div>
            <div className="text-slate-500">업로드</div>
            <div className="text-slate-900">{new Date(file.createdAt).toLocaleDateString('ko')}</div>
          </div>
          {file.storedName && (
            <a
              href={downloadUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="block w-full text-center py-2 bg-primary text-white rounded-lg text-sm hover:bg-primary/90 transition-colors"
            >
              다운로드
            </a>
          )}
        </div>
        <div className="border-t px-6 py-3 text-center">
          <span className="text-xs text-slate-400">읽기 전용 — 아카이브 모드</span>
        </div>
      </div>
    </div>
  )
}
