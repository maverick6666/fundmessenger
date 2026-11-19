/**
 * 스켈레톤 로딩 컴포넌트 모음
 * - PostSkeleton: 게시글 목록용
 * - FileSkeleton: 드라이브 파일 목록용
 * - GenericSkeleton: 범용 1줄 bar
 * - PostDetailSkeleton: 글 상세 페이지용
 */

export function PostSkeleton({ count = 5 }) {
  return Array.from({ length: count }, (_, i) => (
    <div key={i} className="px-6 py-3 border-b border-slate-50 animate-pulse">
      <div className="h-4 bg-slate-200 rounded w-3/4 mb-2" />
      <div className="h-3 bg-slate-100 rounded w-1/3" />
    </div>
  ))
}

export function FileSkeleton({ count = 5 }) {
  return Array.from({ length: count }, (_, i) => (
    <div key={i} className="px-6 py-3 border-b border-slate-50 animate-pulse flex items-center gap-4">
      <div className="w-10 h-10 bg-slate-200 rounded-lg shrink-0" />
      <div className="flex-1 min-w-0">
        <div className="h-4 bg-slate-200 rounded w-2/3 mb-2" />
        <div className="h-3 bg-slate-100 rounded w-2/5" />
      </div>
    </div>
  ))
}

export function GenericSkeleton({ count = 3 }) {
  return Array.from({ length: count }, (_, i) => (
    <div key={i} className="px-5 py-3 animate-pulse">
      <div className="h-4 bg-slate-200 rounded w-4/5" />
    </div>
  ))
}

export function PostDetailSkeleton() {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden animate-pulse">
      {/* 상단 바 */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-slate-200">
        <div className="h-4 bg-slate-200 rounded w-12" />
        <div className="h-7 bg-slate-200 rounded w-16" />
      </div>
      {/* 헤더 */}
      <div className="px-6 py-4 border-b border-slate-100">
        <div className="flex items-center gap-2 mb-3">
          <div className="h-5 bg-slate-200 rounded w-14" />
          <div className="h-5 bg-slate-100 rounded w-20" />
        </div>
        <div className="h-6 bg-slate-200 rounded w-3/5 mb-3" />
        <div className="h-3 bg-slate-100 rounded w-1/3" />
      </div>
      {/* 본문 */}
      <div className="px-6 py-6 space-y-3">
        <div className="h-4 bg-slate-100 rounded w-full" />
        <div className="h-4 bg-slate-100 rounded w-5/6" />
        <div className="h-4 bg-slate-100 rounded w-4/6" />
        <div className="h-4 bg-slate-100 rounded w-full" />
        <div className="h-4 bg-slate-100 rounded w-3/5" />
      </div>
    </div>
  )
}
