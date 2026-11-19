import NewsMarkdownRenderer from './NewsMarkdownRenderer'

function extractPreviewTitle(markdown, fallback) {
  if (!markdown) return fallback

  const heading = markdown
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => /^#{1,3}\s+/.test(line))

  if (!heading) return fallback
  return heading.replace(/^#{1,3}\s+/, '').trim() || fallback
}

function CompareColumn({ job, onOpenPreview }) {
  const fallbackTitle = job.driveFileName || `AI 결과 #${job.jobId}`
  const title = extractPreviewTitle(job.resultMarkdown, fallbackTitle)

  return (
    <div className="flex min-h-0 flex-1 flex-col rounded-2xl border border-slate-200 bg-white">
      <div className="border-b border-slate-100 px-5 py-4">
        <p className="truncate text-sm font-semibold text-slate-900">{title}</p>
        <p className="mt-1 truncate text-xs text-slate-400">{job.driveFileName}</p>
        <p className="mt-1 text-xs text-slate-400">
          {new Date(job.completedAt || job.createdAt).toLocaleString('ko-KR')}
        </p>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
        <NewsMarkdownRenderer content={job.resultMarkdown} />
      </div>
      <div className="border-t border-slate-100 px-5 py-3">
        <button
          onClick={() => onOpenPreview(job)}
          className="cursor-pointer rounded-lg border border-slate-200 px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-50"
        >
          단일 미리보기 열기
        </button>
      </div>
    </div>
  )
}

export default function AiCompareModal({ jobs, onClose, onOpenPreview }) {
  return (
    <div
      className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/40"
      onClick={onClose}
    >
      <div
        className="mx-4 flex h-[85vh] w-full max-w-7xl flex-col rounded-2xl bg-slate-50 shadow-xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
          <div>
            <h2 className="text-base font-bold text-slate-900">AI 생성 결과 비교</h2>
            <p className="mt-1 text-xs text-slate-400">선택한 두 AI 결과를 같은 화면에서 비교합니다.</p>
          </div>
          <button
            onClick={onClose}
            className="cursor-pointer text-lg text-slate-400 hover:text-slate-600"
            aria-label="비교 닫기"
          >
            ×
          </button>
        </div>

        <div className="grid min-h-0 flex-1 gap-4 px-4 py-4 lg:grid-cols-2">
          {jobs.map((job) => (
            <CompareColumn key={job.jobId} job={job} onOpenPreview={onOpenPreview} />
          ))}
        </div>
      </div>
    </div>
  )
}
