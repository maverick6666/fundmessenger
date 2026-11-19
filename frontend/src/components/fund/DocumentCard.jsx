import { formatTime } from '../../lib/format'

const TYPE_STYLES = {
  decision: {
    headerBg: 'bg-cyan-600',
    label: '의사결정',
  },
  report: {
    headerBg: 'bg-teal-600',
    label: '운용보고',
  },
}

export default function DocumentCard({ type, data, onClick }) {
  const style = TYPE_STYLES[type] || TYPE_STYLES.decision

  const title =
    data.title ||
    `${data.positionTickerName || data.positionTicker || data.targetTicker || '포지션'} 보고서`

  const positionName =
    data.positionTickerName || data.positionTicker || data.targetTicker
  const market = data.positionMarket || data.market

  return (
    <button
      onClick={onClick}
      className="group text-left w-[140px] shrink-0 focus:outline-none focus-visible:ring-2 focus-visible:ring-black"
    >
      <div className="relative aspect-[3/4] overflow-hidden bg-stone-50 border-2 border-slate-800 hover:border-black transition-colors duration-150">
        {/* Top Color Block */}
        <div
          className={`${style.headerBg} px-2.5 py-1.5 flex items-center justify-between border-b-2 border-slate-800`}
        >
          <span className="text-xs font-bold uppercase tracking-wider text-white whitespace-nowrap">
            {style.label}
          </span>
        </div>

        {/* Content */}
        <div className="h-[calc(100%-2rem)] px-2.5 py-2 flex flex-col overflow-hidden">
          {/* Title */}
          <h3 className="font-bold text-slate-900 text-sm leading-tight line-clamp-3 group-hover:underline decoration-2 underline-offset-2 break-words">
            {title}
          </h3>

          {/* Spacer */}
          <div className="flex-1 min-h-[8px]" />

          {/* Meta row */}
          <div className="flex items-center justify-between gap-1 mb-1.5">
            <span className="text-xs font-mono text-slate-500 whitespace-nowrap shrink-0">
              {formatTime(data.createdAt)}
            </span>
            {data.positionStatus === 'OPEN' && (
              <span className="text-xs font-bold uppercase tracking-wide text-emerald-700 bg-emerald-100 px-1.5 py-0.5 shrink-0">
                OPEN
              </span>
            )}
          </div>

          {/* Footer */}
          <div className="pt-1.5 border-t-2 border-black/20">
            {type === 'decision' && data.authorName ? (
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-md bg-slate-700/80 flex items-center justify-center text-white text-xs font-bold ring-1 ring-black/10 shrink-0">
                  {data.authorName.charAt(0)}
                </div>
                <span className="text-xs font-medium text-slate-700 truncate">
                  {data.authorName}
                </span>
              </div>
            ) : positionName ? (
              <div className="flex items-center justify-between gap-1">
                <span className="text-xs font-bold text-slate-800 truncate">
                  {positionName}
                </span>
                {market && (
                  <span className="text-xs font-mono font-bold text-slate-500 bg-slate-200 px-1 py-0.5">
                    {market}
                  </span>
                )}
              </div>
            ) : data.authorName ? (
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-md bg-slate-700/80 flex items-center justify-center text-white text-xs font-bold ring-1 ring-black/10 shrink-0">
                  {data.authorName.charAt(0)}
                </div>
                <span className="text-xs font-medium text-slate-700 truncate">
                  {data.authorName}
                </span>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </button>
  )
}
