import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AiPreviewModal from './AiPreviewModal'
import AiCompareModal from './AiCompareModal'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import useAiJobStore from '../store/aiJobStore'
import useClubStore from '../store/clubStore'

function extractPreviewTitle(markdown, fallback) {
  if (!markdown) return fallback

  const heading = markdown
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => /^#{1,3}\s+/.test(line))

  if (!heading) return fallback
  return heading.replace(/^#{1,3}\s+/, '').trim() || fallback
}

export default function MyAiHistoryPanel({ clubId }) {
  const navigate = useNavigate()
  const clubInfo = useClubStore((s) => s.clubInfo)
  const [jobs, setJobs] = useState([])
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)
  const [previewJob, setPreviewJob] = useState(null)
  const [compareIds, setCompareIds] = useState([])
  const [compareOpen, setCompareOpen] = useState(false)
  const startTracking = useAiJobStore((s) => s.startTracking)
  const trackedClubId = useAiJobStore((s) => s.clubId)
  const trackedJob = useAiJobStore((s) => s.job)

  const fetchJobs = () => {
    if (!clubId) return Promise.resolve()

    setLoading(true)
    setAccessDenied(false)
    return api.get(`/clubs/${clubId}/ai/jobs?status=COMPLETED`)
      .then((data) => setJobs(data || []))
      .catch((error) => {
        if (error?.response?.status === 401 || error?.response?.status === 403) {
          setJobs([])
          setAccessDenied(true)
          return
        }
        handleApiError(error, '내 AI 생성 목록 조회')
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchJobs()
  }, [clubId])

  useEffect(() => {
    if (!clubId || trackedClubId !== clubId || trackedJob?.status !== 'COMPLETED') return
    fetchJobs()
  }, [clubId, trackedClubId, trackedJob?.status])

  const toggleCompare = (jobId) => {
    setCompareIds((prev) => {
      if (prev.includes(jobId)) {
        return prev.filter((id) => id !== jobId)
      }

      if (prev.length < 2) {
        return [...prev, jobId]
      }

      return [prev[1], jobId]
    })
  }

  const compareJobs = useMemo(
    () => compareIds.map((id) => jobs.find((job) => job.jobId === id)).filter(Boolean),
    [compareIds, jobs],
  )

  const handleRegenerate = (nextJob) => {
    setPreviewJob(null)
    startTracking(clubId, nextJob.jobId)
  }

  const handlePublish = (postId) => {
    setPreviewJob(null)
    navigate(`/club/boards?post=${postId}`)
  }

  return (
    <>
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-slate-700">내 AI 생성</p>
            <p className="mt-1 text-xs text-slate-400">
              AI 요청자는 드라이브 파일 작성자가 아니라 AI 요청을 보낸 본인 기준으로 귀속됩니다.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-400">
              {compareIds.length}/2 선택
            </span>
            <button
              onClick={() => setCompareOpen(true)}
              disabled={compareJobs.length !== 2}
              className="cursor-pointer rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              비교
            </button>
          </div>
        </div>

        {loading ? (
          <div className="flex justify-center py-6">
            <div className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-slate-600" />
          </div>
        ) : accessDenied ? (
          <p className="py-8 text-center text-xs text-slate-400">
            AI 생성 권한이 있는 요청자만 자신의 AI 결과를 볼 수 있습니다.
          </p>
        ) : jobs.length === 0 ? (
          <p className="py-8 text-center text-xs text-slate-400">완료된 AI 생성 결과가 없습니다.</p>
        ) : (
          <div className="space-y-2">
            {jobs.map((job) => {
              const fallbackTitle = job.driveFileName || `AI 결과 #${job.jobId}`
              const title = extractPreviewTitle(job.resultMarkdown, fallbackTitle)
              const selected = compareIds.includes(job.jobId)

              return (
                <div
                  key={job.jobId}
                  className={`flex items-center gap-3 rounded-xl border px-3 py-3 transition-colors ${
                    selected ? 'border-violet-300 bg-violet-50/50' : 'border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={selected}
                    onChange={() => toggleCompare(job.jobId)}
                    className="h-4 w-4 cursor-pointer rounded border-slate-300"
                    aria-label={`${title} 비교 선택`}
                  />
                  <button
                    onClick={() => setPreviewJob(job)}
                    className="min-w-0 flex-1 cursor-pointer text-left"
                  >
                    <div className="flex items-center gap-2">
                      <span className="rounded-full bg-violet-100 px-2 py-0.5 text-xs font-semibold text-violet-600">
                        AI
                      </span>
                      <span className="truncate text-sm font-medium text-slate-900">
                        {title}
                      </span>
                    </div>
                    <p className="mt-1 truncate text-xs text-slate-400">{job.driveFileName}</p>
                    <p className="mt-1 text-xs text-slate-400">
                      {new Date(job.completedAt || job.createdAt).toLocaleString('ko-KR')}
                    </p>
                    {job.linkedPostId && (
                      <p className="mt-1 text-xs text-slate-400">
                        이미 발행됨: {job.linkedPostTitle || `게시글 #${job.linkedPostId}`}
                      </p>
                    )}
                  </button>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {previewJob && (
        <AiPreviewModal
          clubId={clubId}
          jobId={previewJob.jobId}
          markdown={previewJob.resultMarkdown}
          subtitle={previewJob.driveFileName}
          clubInfo={clubInfo}
          onPublish={previewJob.publishable ? handlePublish : undefined}
          onRegenerate={handleRegenerate}
          onClose={() => setPreviewJob(null)}
        />
      )}

      {compareOpen && compareJobs.length === 2 && (
        <AiCompareModal
          jobs={compareJobs}
          onClose={() => setCompareOpen(false)}
          onOpenPreview={(job) => {
            setCompareOpen(false)
            setPreviewJob(job)
          }}
        />
      )}
    </>
  )
}
