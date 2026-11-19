/**
 * GenerationBadge — 기수 태그 뱃지
 * mode: "latest" (최신 기수 + 나머지 수), "first" (최초 기수), "full" (전체 나열)
 */
export default function GenerationBadge({ generations = [], mode = 'latest', className = '' }) {
  if (!generations || generations.length === 0) return null

  const sorted = [...generations].sort((a, b) => a.displayOrder - b.displayOrder)

  let label = ''
  if (mode === 'first') {
    label = sorted[0].name
  } else if (mode === 'full') {
    label = sorted.map(g => g.name).join(', ')
  } else {
    // latest — displayOrder 가장 큰 것
    const latest = sorted[sorted.length - 1]
    label = sorted.length > 1 ? `${latest.name} +${sorted.length - 1}` : latest.name
  }

  return (
    <span className={`inline-flex items-center text-xs px-2 py-0.5 bg-indigo-50 text-indigo-600 rounded-full font-medium ${className}`}>
      {label}
    </span>
  )
}
