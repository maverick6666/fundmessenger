import { useState, useEffect } from 'react'
import api from '../lib/api'
import AiBadge from './AiBadge'
import ConfirmModal from './ConfirmModal'
import handleApiError from '../lib/handleApiError'

export default function NewsCommentSection({ clubId, stockId, couplingId, membershipId, isExecutive }) {
  const [comments, setComments] = useState([])
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(true)
  const [replyTo, setReplyTo] = useState(null) // { id, authorName }
  const [replyContent, setReplyContent] = useState('')
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  const basePath = `/clubs/${clubId}/jongtobang/stocks/${stockId}/news/${couplingId}/comments`

  const fetchComments = async () => {
    try {
      const data = await api.get(basePath)
      setComments(data || [])
    } catch (e) {
      handleApiError(e, '뉴스 댓글 조회')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchComments()
  }, [couplingId])

  // 부모-자식 트리 그룹핑
  const rootComments = comments.filter(c => !c.parentCommentId)
  const childMap = {}
  comments.filter(c => c.parentCommentId).forEach(c => {
    if (!childMap[c.parentCommentId]) childMap[c.parentCommentId] = []
    childMap[c.parentCommentId].push(c)
  })

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!content.trim()) return
    try {
      await api.post(basePath, { content })
      setContent('')
      fetchComments()
    } catch (e) {
      setAlertModal({ open: true, message: '댓글 작성 실패: ' + (e.message || '') })
    }
  }

  const handleReplySubmit = async (e) => {
    e.preventDefault()
    if (!replyContent.trim() || !replyTo) return
    try {
      await api.post(basePath, { content: replyContent.trim(), parentCommentId: replyTo.id })
      setReplyContent('')
      setReplyTo(null)
      fetchComments()
    } catch (e) {
      setAlertModal({ open: true, message: '답글 작성 실패: ' + (e.message || '') })
    }
  }

  const handleDeleteClick = (commentId) => {
    setDeleteTarget(commentId)
    setShowDeleteConfirm(true)
  }

  const handleDeleteConfirm = async () => {
    const commentId = deleteTarget
    setShowDeleteConfirm(false)
    setDeleteTarget(null)
    try {
      await api.delete(`${basePath}/${commentId}`)
      fetchComments()
    } catch (e) {
      setAlertModal({ open: true, message: '삭제 실패: ' + (e.message || '') })
    }
  }

  const renderComment = (c, isChild = false) => (
    <div key={c.id} className={`flex items-start gap-2 group ${isChild ? 'pl-3 border-l-2 border-slate-100' : ''}`}>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
          <span className="text-xs font-medium text-slate-700">{c.authorName}</span>
          <AiBadge aiGenerated={c.aiGenerated} variant="bot" />
          <span className="text-xs text-slate-400">
            {new Date(c.createdAt).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
          </span>
        </div>
        <p className="text-xs text-slate-600 mt-0.5">{c.content}</p>
        {!isChild && (
          <button
            onClick={() => setReplyTo(replyTo?.id === c.id ? null : { id: c.id, authorName: c.authorName })}
            className="text-xs text-slate-400 hover:text-blue-500 mt-0.5 cursor-pointer"
          >
            답글
          </button>
        )}
      </div>
      {!c.aiGenerated && (c.authorMembershipId === membershipId || isExecutive) && (
        <button
          onClick={() => handleDeleteClick(c.id)}
          className="text-xs text-red-400 hover:text-red-600 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer flex-shrink-0"
        >
          삭제
        </button>
      )}
    </div>
  )

  return (
    <div className="mt-3 pt-3 border-t border-slate-100">
      {showDeleteConfirm && (
        <ConfirmModal
          title="댓글 삭제"
          message="댓글을 삭제하시겠습니까?"
          confirmLabel="삭제"
          onConfirm={handleDeleteConfirm}
          onClose={() => { setShowDeleteConfirm(false); setDeleteTarget(null) }}
          danger
        />
      )}
      {alertModal.open && (
        <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
      <h4 className="text-xs font-semibold text-slate-500 mb-2">댓글 {comments.length}건</h4>

      {loading ? (
        <div className="py-2 flex justify-center">
          <div className="animate-spin w-4 h-4 border-2 border-slate-300 border-t-gray-600 rounded-full" />
        </div>
      ) : (
        <div className="space-y-2 mb-3">
          {rootComments.map(c => (
            <div key={c.id}>
              {renderComment(c)}
              {/* 자식 댓글 */}
              {childMap[c.id]?.map(child => renderComment(child, true))}
              {/* 답글 입력 */}
              {replyTo?.id === c.id && (
                <form onSubmit={handleReplySubmit} className="pl-3 mt-1.5 border-l-2 border-blue-200">
                  <p className="text-xs text-blue-500 mb-1">@{replyTo.authorName}에게 답글</p>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={replyContent}
                      onChange={e => setReplyContent(e.target.value)}
                      placeholder="답글을 입력하세요..."
                      autoFocus
                      className="flex-1 px-2.5 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/30"
                    />
                    <button
                      type="submit"
                      disabled={!replyContent.trim()}
                      className="px-3 py-1.5 text-xs bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer"
                    >
                      전송
                    </button>
                    <button
                      type="button"
                      onClick={() => { setReplyTo(null); setReplyContent('') }}
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

      <form onSubmit={handleSubmit} className="flex gap-2">
        <input
          type="text"
          value={content}
          onChange={e => setContent(e.target.value)}
          placeholder="댓글을 입력하세요..."
          className="flex-1 px-2.5 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/30"
        />
        <button
          type="submit"
          disabled={!content.trim()}
          className="px-3 py-1.5 text-xs bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer"
        >
          전송
        </button>
      </form>
    </div>
  )
}
