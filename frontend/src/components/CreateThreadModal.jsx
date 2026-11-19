import { useState } from 'react'
import api from '../lib/api'
import ConfirmModal from './ConfirmModal'

export default function CreateThreadModal({ clubId, stockId, stockName, onClose, onCreated }) {
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!title.trim()) return
    setSubmitting(true)
    try {
      await api.post(`/clubs/${clubId}/jongtobang/stocks/${stockId}/threads`, { title, content })
      onCreated?.()
      onClose()
    } catch (e) {
      setAlertModal({ open: true, message: '스레드 작성 실패: ' + (e.message || '') })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <form
        onSubmit={handleSubmit}
        className="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6"
        onClick={e => e.stopPropagation()}
      >
        <h2 className="text-lg font-bold text-slate-900 mb-1">새 스레드</h2>
        <p className="text-xs text-slate-500 mb-4">{stockName} 종목에 대한 의견을 작성하세요</p>

        <div className="space-y-3">
          <div>
            <label className="text-xs text-slate-500 mb-1 block">제목 *</label>
            <input
              type="text"
              value={title}
              onChange={e => setTitle(e.target.value)}
              placeholder="스레드 제목을 입력하세요"
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/30"
              required
              maxLength={200}
            />
          </div>
          <div>
            <label className="text-xs text-slate-500 mb-1 block">내용</label>
            <textarea
              value={content}
              onChange={e => setContent(e.target.value)}
              placeholder="종목에 대한 의견을 작성하세요..."
              rows={5}
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/30 resize-none"
              maxLength={5000}
            />
          </div>
        </div>

        <div className="flex justify-end gap-2 mt-5">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 cursor-pointer"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={!title.trim() || submitting}
            className="px-4 py-2 text-sm bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer"
          >
            {submitting ? '작성 중...' : '작성하기'}
          </button>
        </div>
      </form>

      {alertModal.open && (
        <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
    </div>
  )
}
