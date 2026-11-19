import { FileText, FolderOpen, BarChart3, Users, PenLine, Upload } from 'lucide-react'

const ICONS = {
  post: FileText,
  file: FolderOpen,
  stock: BarChart3,
  team: Users,
  write: PenLine,
  upload: Upload,
  default: FileText,
}

/**
 * 빈 상태 컴포넌트 — 아이콘 + 설명 + CTA
 * @param {string} icon - 아이콘 키 (post, file, stock, team, write, upload)
 * @param {string} message - 메인 메시지
 * @param {string} [ctaLabel] - CTA 버튼 텍스트
 * @param {function} [onCta] - CTA 클릭 핸들러
 */
export default function EmptyState({ icon = 'default', message, ctaLabel, onCta }) {
  const Icon = ICONS[icon] || ICONS.default

  return (
    <div className="flex flex-col items-center gap-3 py-12">
      <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center">
        <Icon className="w-6 h-6 text-slate-400" />
      </div>
      <p className="text-sm text-slate-500">{message}</p>
      {ctaLabel && onCta && (
        <button
          onClick={onCta}
          className="text-sm text-blue-500 font-medium hover:text-blue-600 transition-colors cursor-pointer"
        >
          {ctaLabel}
        </button>
      )}
    </div>
  )
}
