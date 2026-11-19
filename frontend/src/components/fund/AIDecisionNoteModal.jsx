import { useState, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import api from '../../lib/api'
import { formatPrice, formatNumber } from '../../lib/format'
import handleApiError from '../../lib/handleApiError'
import useToastStore from '../../store/toastStore'

/* ─────────────────────────────────────────────
   AIDecisionNoteModal
   포지션에 대한 AI 의사결정서를 생성하고 미리보기/저장하는 모달
   ───────────────────────────────────────────── */

export default function AIDecisionNoteModal({
  isOpen,
  onClose,
  clubId,
  teamId,
  position,
  onSaved,
}) {
  const addToast = useToastStore((s) => s.addToast)

  const [generating, setGenerating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [generatedNote, setGeneratedNote] = useState(null)
  const [error, setError] = useState(null)

  // 모달 열릴 때 상태 초기화
  useEffect(() => {
    if (isOpen) {
      setGeneratedNote(null)
      setError(null)
      setGenerating(false)
      setSaving(false)
    }
  }, [isOpen])

  if (!isOpen) return null

  const handleGenerate = async () => {
    setGenerating(true)
    setError(null)
    try {
      const result = await api.post(
        `/clubs/${clubId}/fund/teams/${teamId}/ai/decision-note`,
        { positionId: position.id },
      )
      setGeneratedNote(result.content || result)
    } catch (err) {
      const msg =
        err?.data?.message || err?.message || 'AI 의사결정서 생성에 실패했습니다.'
      setError(msg)
    } finally {
      setGenerating(false)
    }
  }

  const handleSave = async () => {
    if (!generatedNote) return
    setSaving(true)
    try {
      // 저장 API가 별도로 있으면 호출, 없으면 콜백으로 전달
      onSaved?.(generatedNote)
      addToast({ type: 'SUCCESS', title: 'AI 의사결정서', body: '저장되었습니다.' })
      onClose()
    } catch (err) {
      handleApiError(err, '의사결정서 저장')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center">
      {/* 백드롭 */}
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />

      {/* 모달 본체 */}
      <div className="relative bg-white rounded-2xl shadow-xl w-full max-w-lg mx-4 max-h-[85vh] flex flex-col">
        {/* 헤더 */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200">
          <h2 className="text-lg font-bold text-slate-900">AI 의사결정서</h2>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 text-xl leading-none"
          >
            &times;
          </button>
        </div>

        {/* 본문 */}
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {/* 포지션 정보 */}
          {position && (
            <div className="p-3 bg-slate-50 rounded-lg text-sm grid grid-cols-2 gap-y-1.5 gap-x-4">
              <div className="flex justify-between col-span-2 sm:col-span-1">
                <span className="text-slate-500">종목</span>
                <span className="font-medium text-slate-900">
                  {position.tickerName || position.ticker}
                </span>
              </div>
              <div className="flex justify-between col-span-2 sm:col-span-1">
                <span className="text-slate-500">시장</span>
                <span className="font-medium text-slate-900">{position.market}</span>
              </div>
              <div className="flex justify-between col-span-2 sm:col-span-1">
                <span className="text-slate-500">평단가</span>
                <span className="font-medium text-slate-900">
                  {formatPrice(position.averagePrice, position.market)}
                </span>
              </div>
              <div className="flex justify-between col-span-2 sm:col-span-1">
                <span className="text-slate-500">수량</span>
                <span className="font-medium text-slate-900">
                  {formatNumber(position.quantity)}
                </span>
              </div>
            </div>
          )}

          {/* 에러 메시지 */}
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg">
              <p className="text-sm text-red-600">{error}</p>
            </div>
          )}

          {/* 생성 전: 버튼 */}
          {!generatedNote && !generating && (
            <div className="text-center py-6">
              <p className="text-sm text-slate-500 mb-4">
                AI가 해당 포지션에 대한 의사결정서를 자동 생성합니다.
              </p>
              <button
                onClick={handleGenerate}
                className="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-semibold transition-colors"
              >
                생성하기
              </button>
            </div>
          )}

          {/* 생성 중: 스피너 */}
          {generating && (
            <div className="flex flex-col items-center py-10 gap-3">
              <svg
                className="animate-spin h-8 w-8 text-indigo-600"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle
                  className="opacity-25"
                  cx="12" cy="12" r="10"
                  stroke="currentColor" strokeWidth="4"
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                />
              </svg>
              <p className="text-sm text-slate-500">AI가 분석 중입니다...</p>
            </div>
          )}

          {/* 생성 완료: 마크다운 미리보기 */}
          {generatedNote && (
            <div className="p-4 bg-slate-50 rounded-lg max-h-80 overflow-y-auto">
              <div className="prose prose-sm max-w-none
                prose-headings:text-slate-900
                prose-h1:text-xl prose-h1:font-bold prose-h1:mt-4 prose-h1:mb-2
                prose-h2:text-lg prose-h2:font-semibold prose-h2:mt-4 prose-h2:mb-2
                prose-h3:text-base prose-h3:font-semibold prose-h3:mt-3 prose-h3:mb-1
                prose-p:text-slate-700 prose-p:my-1
                prose-li:text-slate-700
                prose-table:text-sm
                prose-th:bg-slate-100 prose-th:px-3 prose-th:py-2
                prose-td:px-3 prose-td:py-2 prose-td:border prose-td:border-slate-200
                prose-strong:text-slate-900
              ">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {generatedNote}
                </ReactMarkdown>
              </div>
            </div>
          )}
        </div>

        {/* 푸터 */}
        <div className="flex justify-end gap-3 px-5 py-4 border-t border-slate-200">
          {generatedNote ? (
            <>
              <button
                onClick={() => { setGeneratedNote(null); setError(null) }}
                className="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
              >
                다시 생성
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg disabled:opacity-50 transition-colors"
              >
                {saving ? '저장 중...' : '저장'}
              </button>
            </>
          ) : (
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors"
            >
              닫기
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
