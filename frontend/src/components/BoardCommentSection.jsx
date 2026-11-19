import { useEffect, useState } from 'react'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import AiBadge from './AiBadge'
import ConfirmModal from './ConfirmModal'
import handleApiError from '../lib/handleApiError'

export default function BoardCommentSection({ clubId, boardId, postId, membershipId, isExecutive }) {
  const [comments, setComments] = useState([])
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [replyTo, setReplyTo] = useState(null) // { id, authorName }
  const [replyContent, setReplyContent] = useState('')
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  const basePath = `/clubs/${clubId}/posts/${postId}/comments`

  useEffect(() => {
    if (!clubId || !postId) return
    api.get(basePath).then(setComments).catch(e => handleApiError(e, '댓글 조회'))
  }, [clubId, postId])

  // 댓글을 부모-자식 트리로 그룹핑
  const rootComments = comments.filter(c => !c.parentCommentId)
  const childMap = {}
  comments.filter(c => c.parentCommentId).forEach(c => {
    if (!childMap[c.parentCommentId]) childMap[c.parentCommentId] = []
    childMap[c.parentCommentId].push(c)
  })

  const handleSubmit = async () => {
    if (!content.trim() || content.length > 500 || submitting) return
    setSubmitting(true)
    try {
      const saved = await api.post(basePath, { content: content.trim() })
      setComments(prev => [...prev, saved])
      setContent('')
      // 새 댓글로 자동 스크롤
      setTimeout(() => {
        const el = document.getElementById(`comment-${saved.id}`)
        el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }, 100)
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setSubmitting(false)
    }
  }

  const handleReplySubmit = async () => {
    if (!replyContent.trim() || replyContent.length > 500 || submitting || !replyTo) return
    setSubmitting(true)
    try {
      const saved = await api.post(basePath, { content: replyContent.trim(), parentCommentId: replyTo.id })
      setComments(prev => [...prev, saved])
      setReplyContent('')
      setReplyTo(null)
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await api.delete(`${basePath}/${deleteTarget}`)
      setComments(prev => prev.filter(c => c.id !== deleteTarget && c.parentCommentId !== deleteTarget))
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setDeleteTarget(null)
    }
  }

  const canDelete = (comment) => {
    if (comment.aiGenerated) return false
    return comment.authorMembershipId === membershipId || isExecutive
  }

  const renderComment = (c, isChild = false) => (
    <div key={c.id} id={`comment-${c.id}`} className={`flex items-start gap-2 ${isChild ? 'pl-4 border-l-2 border-slate-100' : ''}`}>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-slate-700">{c.authorName}</span>
          <AiBadge aiGenerated={c.aiGenerated} variant="bot" />
          <span className="text-xs text-slate-400">{formatTime(c.createdAt)}</span>
        </div>
        <p className="text-sm text-slate-600 mt-0.5 whitespace-pre-wrap break-words">{c.content}</p>
        {!isChild && (
          <button
            onClick={() => setReplyTo(replyTo?.id === c.id ? null : { id: c.id, authorName: c.authorName })}
            className="text-xs text-slate-400 hover:text-blue-500 mt-1 cursor-pointer"
          >
            답글
          </button>
        )}
      </div>
      {canDelete(c) && (
        <button
          onClick={() => setDeleteTarget(c.id)}
          className="text-xs text-slate-400 hover:text-red-500 shrink-0"
        >
          삭제
        </button>
      )}
    </div>
  )

  return (
    <div className="border-t border-slate-200 px-6 md:px-8 py-5 md:py-6 bg-slate-50/50">
      <h3 className="text-sm font-semibold text-slate-700 mb-3">
        댓글 {comments.length > 0 && <span className="text-blue-600">{comments.length}</span>}
      </h3>

      {comments.length === 0 && (
        <p className="text-xs text-slate-400 mb-3">아직 댓글이 없습니다</p>
      )}

      <div className="space-y-3 mb-4">
        {rootComments.map(c => (
          <div key={c.id}>
            {renderComment(c)}
            {/* 자식 댓글 */}
            {childMap[c.id]?.map(child => renderComment(child, true))}
            {/* 답글 입력 (이 댓글에 대한) */}
            {replyTo?.id === c.id && (
              <div className="pl-4 mt-2 border-l-2 border-blue-200">
                <p className="text-xs text-blue-500 mb-1">@{replyTo.authorName}에게 답글</p>
                <div className="flex gap-2 items-end">
                  <textarea
                    value={replyContent}
                    onChange={e => setReplyContent(e.target.value)}
                    placeholder="답글을 입력하세요"
                    rows={2}
                    autoFocus
                    className={`flex-1 px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-1 resize-none ${
                      replyContent.length > 500 ? 'border-red-300 focus:ring-red-400' : 'border-slate-200 focus:ring-blue-400'
                    }`}
                  />
                  <div className="flex flex-col gap-1">
                    <button
                      type="button"
                      onClick={handleReplySubmit}
                      disabled={!replyContent.trim() || replyContent.length > 500 || submitting}
                      className="px-3 py-1.5 text-xs bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                    >
                      등록
                    </button>
                    <button
                      type="button"
                      onClick={() => { setReplyTo(null); setReplyContent('') }}
                      className="px-3 py-1.5 text-xs text-slate-500 hover:text-slate-700 cursor-pointer"
                    >
                      취소
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="space-y-1">
        <div className="flex gap-2 items-end">
          <textarea
            value={content}
            onChange={e => setContent(e.target.value)}
            placeholder="댓글을 입력하세요"
            rows={2}
            className={`flex-1 px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-1 resize-none ${
              content.length > 500 ? 'border-red-300 focus:ring-red-400' : 'border-slate-200 focus:ring-blue-400'
            }`}
          />
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!content.trim() || content.length > 500 || submitting}
            className="relative z-10 px-4 py-2 text-sm bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 disabled:cursor-not-allowed shrink-0 cursor-pointer"
          >
            등록
          </button>
        </div>
        {content.length > 500 && (
          <p className="text-xs text-red-500">{content.length}/500자 — 최대 길이를 초과했습니다</p>
        )}
      </div>

      {deleteTarget && (
        <ConfirmModal
          title="댓글 삭제"
          message="이 댓글을 삭제하시겠습니까?"
          confirmLabel="삭제"
          onConfirm={handleDelete}
          onClose={() => setDeleteTarget(null)}
          danger
        />
      )}

      {alertModal.open && (
        <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
    </div>
  )
}
