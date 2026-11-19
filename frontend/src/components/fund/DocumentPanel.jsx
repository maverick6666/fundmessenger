import { useState, useEffect, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { useOutletContext, useParams } from 'react-router-dom'
import api from '../../lib/api'
import { formatTime } from '../../lib/format'
import useSidePanelStore from '../../store/sidePanelStore'
import useToastStore from '../../store/toastStore'
import useAuthStore from '../../store/authStore'
import ConfirmModal from '../ConfirmModal'

/**
 * 사이드 패널용 문서 뷰어 — v1 DocumentPanel 포팅
 *
 * - 마크다운 렌더링 (BlockRenderer 제거 → react-markdown)
 * - 칼럼 검증 로직 제거 (칼럼은 게시판 글로 대체)
 * - 라이트모드 전용
 * - 댓글 섹션 — Phase 2에서 노트 전용 댓글 API 추가 후 활성화
 */
export default function DocumentPanel({ document: doc, type = 'decision-note', onSaved }) {
  const { clubInfo } = useOutletContext()
  const { teamId } = useParams()
  const clubId = clubInfo?.clubId
  const tid = Number(teamId)
  const user = useAuthStore(s => s.user)
  const { openNoteEditor, closePanel } = useSidePanelStore()
  const addToast = useToastStore(s => s.addToast)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  if (!doc) return null

  const isAuthor = doc.authorId === user?.id || doc.author?.id === user?.id
  const canEdit = type === 'decision-note'
  const canDelete = isAuthor

  const handleEdit = () => {
    if (type === 'decision-note' && doc?.id) {
      openNoteEditor(doc, onSaved)
    }
  }

  const handleDelete = async () => {
    try {
      if (type === 'decision-note' && doc?.positionId && doc?.id) {
        await api.delete(`/clubs/${clubId}/fund/teams/${tid}/notes/${doc.id}`)
        addToast({ type: 'SUCCESS', title: '문서가 삭제되었습니다' })
        if (onSaved) onSaved()
        closePanel()
      }
    } catch {
      addToast({ type: 'ERROR', title: '삭제에 실패했습니다' })
    }
    setShowDeleteConfirm(false)
  }

  return (
    <div className="px-6 py-6">
      {/* 제목 */}
      <h1 className="text-2xl sm:text-3xl font-semibold leading-tight text-slate-900 mb-5"
        style={{ fontFamily: "'Noto Serif KR', 'Crimson Pro', serif" }}>
        {doc.title}
      </h1>

      {/* 작성자 & 날짜 */}
      {(doc.author || doc.authorName) && (
        <div className="flex items-center gap-3 mb-6 pb-5 border-b border-slate-200">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-emerald-400 to-emerald-600 flex items-center justify-center text-white text-sm font-medium">
            {(doc.author?.fullName || doc.authorName || '?').charAt(0)}
          </div>
          <div>
            <p className="text-sm font-medium text-slate-700">
              {doc.author?.fullName || doc.authorName}
            </p>
            <p className="text-xs text-slate-500">
              {formatTime(doc.createdAt)}
              {doc.updatedAt && doc.updatedAt !== doc.createdAt && (
                <span className="ml-1.5 opacity-70">(수정됨)</span>
              )}
            </p>
          </div>
        </div>
      )}

      {/* 포지션 정보 (의사결정서) */}
      {type === 'decision-note' && doc.position && (
        <div className="mb-5 px-3 py-2 rounded-lg bg-slate-50">
          <span className="text-sm text-slate-600">
            {doc.position.tickerName || doc.position.ticker}
            <span className="ml-2 font-mono text-xs text-slate-500">
              {doc.position.ticker} · {doc.position.market}
            </span>
          </span>
        </div>
      )}

      {/* 본문 — 마크다운 */}
      <article>
        {doc.content ? (
          <div className="prose prose-sm max-w-none prose-headings:font-semibold prose-p:leading-relaxed prose-a:text-indigo-600">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
              {doc.content}
            </ReactMarkdown>
          </div>
        ) : (
          <p className="text-slate-500 italic">내용이 없습니다.</p>
        )}
      </article>

      {/* 액션 버튼 */}
      {(canEdit || canDelete) && (
        <div className="flex items-center justify-end gap-2 mt-6 pt-4 border-t border-slate-200">
          {canEdit && (
            <button onClick={handleEdit}
              className="flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium rounded-lg transition-colors text-slate-600 bg-slate-50 hover:bg-slate-100 border border-slate-200">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
              수정
            </button>
          )}
          {canDelete && (
            <button onClick={() => setShowDeleteConfirm(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium rounded-lg transition-colors text-red-600 bg-red-50 hover:bg-red-100 border border-red-200">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              삭제
            </button>
          )}
        </div>
      )}

      {/* TODO: 댓글 섹션 — 노트 전용 댓글 API 구현 후 활성화 */}

      {/* 삭제 확인 모달 */}
      {showDeleteConfirm && (
        <ConfirmModal
          onClose={() => setShowDeleteConfirm(false)}
          onConfirm={handleDelete}
          title="의사결정서 삭제"
          message="정말 이 의사결정서를 삭제하시겠습니까?"
          confirmLabel="삭제"
          danger
        />
      )}
    </div>
  )
}
