import { useState, useEffect, useRef } from 'react'
import api from '../lib/api'
import AiBadge from './AiBadge'
import ConfirmModal from './ConfirmModal'
import handleApiError from '../lib/handleApiError'

export default function StockThreadTab({ clubId, stockId, membershipId, isExecutive, onThreadsChanged }) {
  const [threads, setThreads] = useState([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [sort, setSort] = useState('latest')
  const [loading, setLoading] = useState(true)
  const [newContent, setNewContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const textareaRef = useRef(null)

  const fetchThreads = async (p = 0, s = sort, append = false) => {
    try {
      setLoading(true)
      const data = await api.get(`/clubs/${clubId}/jongtobang/stocks/${stockId}/threads?sort=${s}&page=${p}&size=5`)
      const items = data.content || []
      if (append) {
        setThreads(prev => [...prev, ...items])
      } else {
        setThreads(items)
      }
      setHasMore(items.length === 5)
    } catch (e) {
      handleApiError(e, '스레드 조회')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    setPage(0)
    setThreads([])
    setHasMore(true)
    fetchThreads(0, sort)
  }, [stockId, sort])

  const handleLoadMore = () => {
    const nextPage = page + 1
    setPage(nextPage)
    fetchThreads(nextPage, sort, true)
  }

  const handleRefresh = () => {
    setPage(0)
    setThreads([])
    fetchThreads(0, sort)
  }

  const handleInlineSubmit = async () => {
    if (!newContent.trim() || submitting) return
    setSubmitting(true)
    try {
      await api.post(`/clubs/${clubId}/jongtobang/stocks/${stockId}/threads`, { content: newContent })
      setNewContent('')
      if (textareaRef.current) textareaRef.current.style.height = 'auto'
      handleRefresh()
      onThreadsChanged?.()
    } catch (e) {
      handleApiError(e, '스레드 작성')
    } finally {
      setSubmitting(false)
    }
  }

  const handleTextareaInput = (e) => {
    setNewContent(e.target.value)
    e.target.style.height = 'auto'
    e.target.style.height = Math.min(e.target.scrollHeight, 160) + 'px'
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleInlineSubmit()
    }
  }

  const handleThreadDeleted = () => {
    handleRefresh()
    onThreadsChanged?.()
  }

  return (
    <div className="px-4 pb-4 pt-4">
      {/* 인라인 스레드 작성 */}
      <div className="mb-5">
        <div className="border border-slate-200 rounded-xl focus-within:ring-2 focus-within:ring-blue-500/30 focus-within:border-blue-300 transition-all">
          <textarea
            ref={textareaRef}
            value={newContent}
            onChange={handleTextareaInput}
            onKeyDown={handleKeyDown}
            placeholder="이 종목에 대한 의견을 남겨보세요..."
            rows={1}
            className="w-full px-3.5 py-2.5 text-sm resize-none focus:outline-none rounded-xl"
            maxLength={5000}
          />
          {newContent.trim() && (
            <div className="flex items-center justify-between px-3 pb-2">
              <span className="text-xs text-slate-400">Shift+Enter로 줄바꿈</span>
              <button
                onClick={handleInlineSubmit}
                disabled={submitting}
                className="px-3.5 py-1.5 text-xs bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer font-medium"
              >
                {submitting ? '작성 중...' : '작성'}
              </button>
            </div>
          )}
        </div>
      </div>

      {/* 정렬 */}
      <div className="flex items-center gap-1 mb-3">
        {['latest', 'popular'].map(s => (
          <button
            key={s}
            onClick={() => { setSort(s); setPage(0) }}
            className={`text-xs px-2.5 py-1 rounded cursor-pointer transition-colors ${
              sort === s
                ? 'text-blue-600 font-semibold bg-blue-50'
                : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
            }`}
          >
            {s === 'latest' ? '최신순' : '추천순'}
          </button>
        ))}
      </div>

      {/* 스레드 목록 — 모두 펼쳐진 인라인 */}
      {loading && threads.length === 0 ? (
        <div className="flex items-center justify-center h-32">
          <div className="animate-spin w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full" />
        </div>
      ) : threads.length === 0 ? (
        <div className="text-center text-sm text-slate-400 py-8">
          <p>스레드가 없습니다</p>
          <p className="text-xs mt-1">첫 스레드를 작성해보세요</p>
        </div>
      ) : (
        <div className="space-y-3">
          {threads.map(thread => (
            <InlineThread
              key={thread.id}
              clubId={clubId}
              stockId={stockId}
              thread={thread}
              membershipId={membershipId}
              isExecutive={isExecutive}
              onDeleted={handleThreadDeleted}
            />
          ))}
          {hasMore && (
            <button
              onClick={handleLoadMore}
              disabled={loading}
              className="w-full py-2 text-xs text-blue-500 hover:text-blue-600 font-medium cursor-pointer"
            >
              {loading ? '로딩...' : '더보기'}
            </button>
          )}
        </div>
      )}

    </div>
  )
}

/** 개별 스레드 — 인라인 펼침 (제목+본문+공감 노출, 답글은 접힘) */
function InlineThread({ clubId, stockId, thread, membershipId, isExecutive, onDeleted }) {
  const [liked, setLiked] = useState(thread.liked || false)
  const [likeCount, setLikeCount] = useState(thread.likeCount || 0)
  const [replyCount, setReplyCount] = useState(thread.replyCount || 0)
  const [replies, setReplies] = useState([])
  const [repliesOpen, setRepliesOpen] = useState(false)
  const [repliesLoaded, setRepliesLoaded] = useState(false)
  const [newReply, setNewReply] = useState('')
  const [replyTo, setReplyTo] = useState(null) // { id, authorName } — 대댓글 대상
  const [subReplyContent, setSubReplyContent] = useState('')
  const [showDeleteReplyConfirm, setShowDeleteReplyConfirm] = useState(false)
  const [deleteReplyTarget, setDeleteReplyTarget] = useState(null)
  const [showDeleteThreadConfirm, setShowDeleteThreadConfirm] = useState(false)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  const base = `/clubs/${clubId}/jongtobang/stocks/${stockId}/threads/${thread.id}`

  const fetchReplies = async () => {
    try {
      const data = await api.get(`${base}/replies?page=0&size=50`)
      setReplies(data.content || [])
    } catch (e) {
      handleApiError(e, '답글 조회')
    } finally {
      setRepliesLoaded(true)
    }
  }

  const handleToggleReplies = () => {
    if (!repliesOpen && !repliesLoaded) {
      fetchReplies()
    }
    setRepliesOpen(!repliesOpen)
  }

  const handleLike = async () => {
    try {
      const data = await api.post(`${base}/like`)
      setLiked(data.liked)
      setLikeCount(data.likeCount)
    } catch (e) {
      setAlertModal({ open: true, message: '공감 실패: ' + (e.message || '') })
    }
  }

  const handleCreateReply = async (e) => {
    e.preventDefault()
    if (!newReply.trim()) return
    try {
      await api.post(`${base}/replies`, { content: newReply })
      setNewReply('')
      setReplyCount(prev => prev + 1)
      fetchReplies()
    } catch (e) {
      setAlertModal({ open: true, message: '답글 작성 실패: ' + (e.message || '') })
    }
  }

  const handleCreateSubReply = async (e) => {
    e.preventDefault()
    if (!subReplyContent.trim() || !replyTo) return
    try {
      await api.post(`${base}/replies`, { content: subReplyContent.trim(), parentReplyId: replyTo.id })
      setSubReplyContent('')
      setReplyTo(null)
      setReplyCount(prev => prev + 1)
      fetchReplies()
    } catch (e) {
      setAlertModal({ open: true, message: '답글 작성 실패: ' + (e.message || '') })
    }
  }

  const handleDeleteReplyClick = (replyId) => {
    setDeleteReplyTarget(replyId)
    setShowDeleteReplyConfirm(true)
  }

  const handleDeleteReplyConfirm = async () => {
    const replyId = deleteReplyTarget
    setShowDeleteReplyConfirm(false)
    setDeleteReplyTarget(null)
    try {
      await api.delete(`${base}/replies/${replyId}`)
      setReplyCount(prev => Math.max(0, prev - 1))
      fetchReplies()
    } catch (e) {
      setAlertModal({ open: true, message: '삭제 실패: ' + (e.message || '') })
    }
  }

  const handleDeleteThreadClick = () => {
    setShowDeleteThreadConfirm(true)
  }

  const handleDeleteThreadConfirm = async () => {
    setShowDeleteThreadConfirm(false)
    try {
      await api.delete(base)
      onDeleted?.()
    } catch (e) {
      setAlertModal({ open: true, message: '삭제 실패: ' + (e.message || '') })
    }
  }

  // 답글을 부모-자식 트리로 그룹핑 (depth-1)
  const rootReplies = replies.filter(r => !r.parentReplyId)
  const childReplyMap = {}
  replies.filter(r => r.parentReplyId).forEach(r => {
    if (!childReplyMap[r.parentReplyId]) childReplyMap[r.parentReplyId] = []
    childReplyMap[r.parentReplyId].push(r)
  })

  return (
    <div className="border border-slate-200 shadow-sm rounded-lg p-4">
      {alertModal.open && (
        <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
      {showDeleteReplyConfirm && (
        <ConfirmModal
          title="답글 삭제"
          message="답글을 삭제하시겠습니까?"
          confirmLabel="삭제"
          onConfirm={handleDeleteReplyConfirm}
          onClose={() => { setShowDeleteReplyConfirm(false); setDeleteReplyTarget(null) }}
          danger
        />
      )}
      {showDeleteThreadConfirm && (
        <ConfirmModal
          title="스레드 삭제"
          message="스레드를 삭제하시겠습니까?"
          confirmLabel="삭제"
          onConfirm={handleDeleteThreadConfirm}
          onClose={() => setShowDeleteThreadConfirm(false)}
          danger
        />
      )}
      {/* 작성자 + AI뱃지 + 시간 + 삭제 */}
      <div className="flex items-center gap-2 text-xs text-slate-400 mb-2">
        <span className="font-medium text-slate-600">{thread.authorName}</span>
        <AiBadge aiGenerated={thread.aiGenerated} variant="bot" />
        <span>{new Date(thread.createdAt).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
        <span className="flex-1" />
        {(thread.authorMembershipId === membershipId || isExecutive) && (
          <button onClick={handleDeleteThreadClick} className="text-xs text-red-400 hover:text-red-600 cursor-pointer">
            삭제
          </button>
        )}
      </div>

      {/* 본문 */}
      <p className="text-sm text-slate-700 whitespace-pre-wrap leading-relaxed mb-3">{thread.content}</p>

      {/* 하단 액션: 공감 + 답글 토글 */}
      <div className="flex items-center gap-3">
        {/* 공감 버튼 */}
        <button
          onClick={handleLike}
          className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer ${
            liked ? 'bg-red-50 text-red-500' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
          }`}
        >
          <span>{liked ? '♥' : '♡'}</span>
          <span>{likeCount}</span>
        </button>

        {/* 답글 토글 버튼 */}
        <button
          onClick={handleToggleReplies}
          className="flex items-center gap-1 text-xs text-slate-500 hover:text-blue-500 cursor-pointer transition-colors"
        >
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
          </svg>
          <span>답글 {replyCount}개</span>
          <svg className={`w-3 h-3 transition-transform ${repliesOpen ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>

      {/* 답글 섹션 — 접힘/펼침 */}
      {repliesOpen && (
        <div className="mt-3 pt-3 border-t border-slate-50">
          {/* 답글 목록 (트리 구조) */}
          {repliesLoaded && replies.length > 0 && (
            <div className="space-y-2 mb-3 pl-3 border-l-2 border-slate-100">
              {rootReplies.map(reply => (
                <div key={reply.id}>
                  {/* 루트 답글 */}
                  <div className="group">
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs font-medium text-slate-700">{reply.authorName}</span>
                      <AiBadge aiGenerated={reply.aiGenerated} variant="bot" />
                      <span className="text-xs text-slate-400">
                        {new Date(reply.createdAt).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                      </span>
                      {(reply.authorMembershipId === membershipId || isExecutive) && (
                        <button
                          onClick={() => handleDeleteReplyClick(reply.id)}
                          className="text-xs text-red-400 hover:text-red-600 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                        >
                          삭제
                        </button>
                      )}
                    </div>
                    <p className="text-xs text-slate-600 mt-0.5">{reply.content}</p>
                    <button
                      onClick={() => setReplyTo(replyTo?.id === reply.id ? null : { id: reply.id, authorName: reply.authorName })}
                      className="text-xs text-slate-400 hover:text-blue-500 mt-0.5 cursor-pointer"
                    >
                      답글
                    </button>
                  </div>
                  {/* 자식 답글 */}
                  {childReplyMap[reply.id]?.map(child => (
                    <div key={child.id} className="pl-3 mt-1 border-l-2 border-slate-50 group">
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-medium text-slate-700">{child.authorName}</span>
                        <AiBadge aiGenerated={child.aiGenerated} variant="bot" />
                        <span className="text-xs text-slate-400">
                          {new Date(child.createdAt).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                        </span>
                        {(child.authorMembershipId === membershipId || isExecutive) && (
                          <button
                            onClick={() => handleDeleteReplyClick(child.id)}
                            className="text-xs text-red-400 hover:text-red-600 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                          >
                            삭제
                          </button>
                        )}
                      </div>
                      <p className="text-xs text-slate-600 mt-0.5">{child.content}</p>
                    </div>
                  ))}
                  {/* 대댓글 입력 */}
                  {replyTo?.id === reply.id && (
                    <form onSubmit={handleCreateSubReply} className="pl-3 mt-1.5 border-l-2 border-blue-200">
                      <p className="text-xs text-blue-500 mb-1">@{replyTo.authorName}에게 답글</p>
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={subReplyContent}
                          onChange={e => setSubReplyContent(e.target.value)}
                          placeholder="답글을 입력하세요..."
                          autoFocus
                          className="flex-1 px-2.5 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/30"
                        />
                        <button
                          type="submit"
                          disabled={!subReplyContent.trim()}
                          className="px-3 py-1.5 text-xs bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer"
                        >
                          전송
                        </button>
                        <button
                          type="button"
                          onClick={() => { setReplyTo(null); setSubReplyContent('') }}
                          className="px-2 py-1.5 text-xs text-slate-400 hover:text-slate-600 cursor-pointer"
                        >
                          취소
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              ))}
            </div>
          )}

          {/* 답글 입력 (루트 레벨) */}
          <form onSubmit={handleCreateReply} className="flex gap-2">
            <input
              type="text"
              value={newReply}
              onChange={e => setNewReply(e.target.value)}
              placeholder="답글을 입력하세요..."
              className="flex-1 px-2.5 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/30"
            />
            <button
              type="submit"
              disabled={!newReply.trim()}
              className="px-3 py-1.5 text-xs bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer"
            >
              전송
            </button>
          </form>
        </div>
      )}
    </div>
  )
}
