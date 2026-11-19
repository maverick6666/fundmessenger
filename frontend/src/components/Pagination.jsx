/**
 * 공유 페이지네이션 컴포넌트
 * Props: { page, totalPages, setPage }
 */
export default function Pagination({ page, totalPages, setPage }) {
  if (!totalPages || totalPages <= 1) return null

  return (
    <div className="flex items-center justify-center gap-3">
      <button
        onClick={() => setPage(Math.max(0, page - 1))}
        disabled={page === 0}
        className="text-xs text-slate-400 hover:text-slate-600 disabled:opacity-30 cursor-pointer transition-colors"
      >
        이전
      </button>
      <span className="text-xs text-slate-400 tabular-nums">
        {page + 1} / {totalPages}
      </span>
      <button
        onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
        disabled={page >= totalPages - 1}
        className="text-xs text-slate-400 hover:text-slate-600 disabled:opacity-30 cursor-pointer transition-colors"
      >
        다음
      </button>
    </div>
  )
}
