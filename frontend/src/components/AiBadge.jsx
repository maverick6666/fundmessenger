/**
 * AI 뱃지 — 게시글/댓글/스레드에서 표시
 * variant="post": AI 글 생성 표시 (기본)
 * variant="bot": AI 댓글봇 표시
 */
export default function AiBadge({ aiGenerated, aiEdited, variant = "post" }) {
  if (!aiGenerated) return null

  if (variant === "bot") {
    return (
      <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 bg-violet-50 text-violet-600 rounded-full text-xs font-medium">
        AI
      </span>
    )
  }

  if (aiEdited) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-slate-100 text-slate-500 rounded-full text-xs font-medium">
        ✦ AI 생성 (수정됨)
      </span>
    )
  }

  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-violet-50 text-violet-600 rounded-full text-xs font-medium">
      ✦ AI 생성
    </span>
  )
}
