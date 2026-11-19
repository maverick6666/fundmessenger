import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import { PostSkeleton } from './SkeletonRow'
import handleApiError from '../lib/handleApiError'
import TAG_COLORS from '../lib/tagColors'

function isNew(createdAt) {
  return Date.now() - new Date(createdAt).getTime() < 24 * 60 * 60 * 1000
}

/**
 * 홈 게시판 패널 — 내 팀 기준 + 태그 필터 + 최신 글 5건
 */
export default function HomeBoardPanel({ clubId, myTeams = [] }) {
  const navigate = useNavigate()
  const [posts, setPosts] = useState([])
  const [tags, setTags] = useState([])
  const [teamId, setTeamId] = useState('')
  const [tagId, setTagId] = useState('')
  const [loading, setLoading] = useState(true)

  // 내 팀이 로드되면 첫 번째 팀을 기본 선택
  useEffect(() => {
    if (myTeams.length > 0 && !teamId) {
      setTeamId(String(myTeams[0].teamId))
    }
  }, [myTeams])

  // 태그(카테고리) 로드
  useEffect(() => {
    if (!clubId) return
    api.get(`/clubs/${clubId}/semi-categories`).then(setTags).catch(e => handleApiError(e, '카테고리 조회'))
  }, [clubId])

  // 팀 변경 시 태그 초기화
  useEffect(() => { setTagId('') }, [teamId])

  useEffect(() => {
    if (!clubId || !teamId) return
    setLoading(true)
    const params = new URLSearchParams()
    params.set('teamId', teamId)
    if (tagId) params.set('tagId', tagId)
    params.set('size', '5')
    api.get(`/clubs/${clubId}/home/posts?${params}`)
      .then(data => setPosts(data || []))
      .catch(e => handleApiError(e, '홈 게시글 조회'))
      .finally(() => setLoading(false))
  }, [clubId, teamId, tagId])

  const activeTeam = myTeams.find(t => String(t.teamId) === teamId)

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col overflow-hidden">
      {/* 헤더 */}
      <div className="px-5 pt-4 pb-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-base font-semibold text-slate-900 scandi-heading">게시판</h3>
            {posts.some(p => isNew(p.createdAt)) && (
              <span className="text-xs px-2 py-0.5 bg-red-50 text-red-500 rounded-full font-semibold">
                {posts.filter(p => isNew(p.createdAt)).length} new
              </span>
            )}
          </div>
          <button
            onClick={() => {
              const params = new URLSearchParams()
              if (teamId) params.set('team', teamId)
              if (tagId) params.set('tag', tagId)
              navigate(`/club/boards?${params}`)
            }}
            className="text-xs text-slate-400 hover:text-blue-500 transition-colors cursor-pointer"
          >
            전체보기
          </button>
        </div>

        {/* 내 팀 탭 */}
        {myTeams.length > 0 && (
          <div className="flex mt-3 border-b border-slate-200 overflow-x-auto no-scrollbar">
            {myTeams.map(t => (
              <button
                key={t.teamId}
                onClick={() => setTeamId(String(t.teamId))}
                className={`shrink-0 text-xs px-2.5 py-1.5 transition-colors cursor-pointer ${String(t.teamId) === teamId ? 'text-blue-600 border-b-2 border-blue-500 font-semibold' : 'text-slate-400 hover:text-slate-600'}`}
              >
                {t.teamName}
              </button>
            ))}
          </div>
        )}

        {/* 태그 필터 */}
        {tags.length > 0 && (
          <div className="flex gap-1 mt-2 flex-wrap">
            <button
              onClick={() => setTagId('')}
              className={`shrink-0 text-xs px-2 py-0.5 rounded-full transition-colors cursor-pointer ${!tagId ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'}`}
            >
              전체
            </button>
            {tags.map(t => (
              <button
                key={t.id}
                onClick={() => setTagId(String(t.id))}
                className={`shrink-0 text-xs px-2 py-0.5 rounded-full transition-colors cursor-pointer ${String(t.id) === tagId ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'}`}
              >
                {t.name}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="h-px bg-slate-100" />

      {/* 글 목록 */}
      <div className="flex-1">
        {myTeams.length === 0 ? (
          <p className="text-xs text-slate-400 py-6 text-center">소속 팀이 없습니다</p>
        ) : loading ? (
          <PostSkeleton count={3} />
        ) : posts.length === 0 ? (
          <p className="text-xs text-slate-400 py-6 text-center">
            {activeTeam?.teamName || '팀'}에 글이 없습니다
          </p>
        ) : (
          <div>
            {posts.map(post => (
              <button
                key={post.id}
                onClick={() => {
                  const params = new URLSearchParams()
                  if (teamId) params.set('team', teamId)
                  if (tagId) params.set('tag', tagId)
                  params.set('post', post.id)
                  navigate(`/club/boards?${params}`)
                }}
                className="w-full text-left px-5 py-2.5 hover:bg-slate-50 transition-colors cursor-pointer group"
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
      </div>
    </div>
  )
}
