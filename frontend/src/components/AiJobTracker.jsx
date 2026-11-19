import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import useAiJobStore from '../store/aiJobStore'
import useClubStore from '../store/clubStore'
import AiPreviewModal from './AiPreviewModal'
import { useUnsavedChanges } from './UnsavedChangesProvider'

/**
 * AI 작업 추적 — 글로벌 우상단 고정 카드
 * ClubLayout에서 렌더링, Zustand aiJobStore 구독
 */
export default function AiJobTracker() {
  const navigate = useNavigate()
  const { confirmIfDirty } = useUnsavedChanges()
  const { clubId, jobId, job, clear } = useAiJobStore()
  const startTracking = useAiJobStore((s) => s.startTracking)
  const clubInfo = useClubStore((s) => s.clubInfo)
  const [showPreview, setShowPreview] = useState(false)

  if (!job || !jobId) return null

  const handlePublish = (postId) => {
    confirmIfDirty(() => {
      setShowPreview(false)
      clear()
      navigate(`/club/boards?post=${postId}`)
    })
  }

  const handleRegenerate = (nextJob) => {
    setShowPreview(false)
    startTracking(clubId, nextJob.jobId)
  }

  return (
    <>
      <div className="fixed top-4 right-4 z-50 w-72 shadow-lg rounded-xl overflow-hidden">
        {job.status === 'QUEUED' && (
          <div className="bg-violet-50 border border-violet-200 rounded-xl p-3 flex items-center gap-3">
            <div className="w-5 h-5 rounded-full bg-violet-400 animate-pulse shrink-0" />
            <div className="min-w-0">
              <p className="text-sm font-medium text-violet-700">대기중...</p>
              {job.driveFileName && (
                <p className="text-xs text-violet-500 truncate">{job.driveFileName}</p>
              )}
            </div>
          </div>
        )}

        {job.status === 'PROCESSING' && (
          <div className="bg-violet-50 border border-violet-200 rounded-xl p-3 flex items-center gap-3">
            <div className="w-5 h-5 border-2 border-violet-400 border-t-transparent rounded-full animate-spin shrink-0" />
            <div className="min-w-0">
              <p className="text-sm font-medium text-violet-700">
                {job.processingStep || '생성중'}
                {!job.processingStep && <span className="inline-flex ml-0.5"><span className="animate-bounce" style={{ animationDelay: '0ms' }}>.</span><span className="animate-bounce" style={{ animationDelay: '150ms' }}>.</span><span className="animate-bounce" style={{ animationDelay: '300ms' }}>.</span></span>}
              </p>
              {job.driveFileName && (
                <p className="text-xs text-violet-500 truncate">{job.driveFileName}</p>
              )}
            </div>
          </div>
        )}

        {job.status === 'COMPLETED' && (
          <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-3 flex items-center justify-between">
            <div className="min-w-0">
              <p className="text-sm font-medium text-emerald-700">완료!</p>
              {job.driveFileName && (
                <p className="text-xs text-emerald-500 truncate">{job.driveFileName}</p>
              )}
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <button
                onClick={() => setShowPreview(true)}
                className="px-2.5 py-1 text-xs bg-violet-500 text-white rounded-lg hover:bg-violet-600 cursor-pointer"
              >
                보기
              </button>
              <button
                onClick={clear}
                className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer"
              >
                ✕
              </button>
            </div>
          </div>
        )}

        {job.status === 'FAILED' && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-3">
            <div className="flex items-center justify-between">
              <div className="min-w-0">
                <p className="text-sm font-medium text-red-700">생성 실패</p>
                <p className="text-xs text-red-500 mt-0.5 truncate">{job.errorMessage || '알 수 없는 오류'}</p>
              </div>
              <button onClick={clear} className="text-xs text-red-400 hover:text-red-600 cursor-pointer shrink-0">✕</button>
            </div>
          </div>
        )}
      </div>

      {showPreview && (
        <AiPreviewModal
          clubId={clubId}
          jobId={jobId}
          markdown={job.resultMarkdown}
          subtitle={job.driveFileName}
          clubInfo={clubInfo}
          onPublish={handlePublish}
          onRegenerate={handleRegenerate}
          onClose={() => setShowPreview(false)}
        />
      )}
    </>
  )
}
