import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../../lib/api'
import { formatPrice, formatProfitRate, formatTime, getProfitLossColor } from '../../lib/format'

/**
 * 게시판 사이드바에서 펀드팀 선택 시 표시되는 공개 종목 리스트
 * Position.isPublished=true인 종목만 표시, lastExportedAt DESC 정렬
 */
export default function FundPublicPositionList({ fundTeamId, clubId }) {
  const navigate = useNavigate()
  const [positions, setPositions] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!clubId || !fundTeamId) return
    setLoading(true)
    api.get(`/clubs/${clubId}/fund/teams/${fundTeamId}/positions?status=OPEN&published=true`)
      .then(data => {
        const arr = Array.isArray(data) ? data : []
        setPositions(arr.filter(p => p.published).sort((a, b) =>
          new Date(b.lastExportedAt || b.createdAt) - new Date(a.lastExportedAt || a.createdAt)
        ))
      })
      .catch(() => setPositions([]))
      .finally(() => setLoading(false))
  }, [clubId, fundTeamId])

  if (loading) {
    return (
      <div className="space-y-3">
        {[...Array(4)].map((_, i) => <div key={i} className="animate-pulse bg-slate-100 rounded-xl h-16" />)}
      </div>
    )
  }

  if (positions.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <div className="text-3xl mb-3">📈</div>
        <p className="text-sm text-slate-500 font-medium">공개된 종목이 없습니다</p>
        <p className="text-xs text-slate-400 mt-1">펀드팀에서 종목을 공개하면 여기에 표시됩니다</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <h2 className="text-base font-semibold text-slate-800 fund-serif">펀드팀 공개 종목</h2>
      <div className="space-y-2">
        {positions.map(pos => {
          const rate = pos.profitRate != null ? Number(pos.profitRate) * 100 : null
          return (
            <button
              key={pos.id}
              onClick={() => navigate(`/club/fund/${fundTeamId}/positions/${pos.id}?mode=public`)}
              className="w-full text-left p-4 bg-white rounded-2xl border border-slate-200 hover:border-slate-300 hover:shadow-sm transition-all cursor-pointer group"
            >
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-semibold text-slate-800 group-hover:text-indigo-600 transition-colors fund-serif">
                    {pos.tickerName || pos.ticker}
                  </h3>
                  <p className="text-xs text-slate-400 font-mono mt-0.5">
                    {pos.ticker} · {pos.market}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-slate-500">{formatPrice(pos.averageBuyPrice, pos.market)}</p>
                  {rate != null && (
                    <p className={`text-sm font-semibold tabular-nums ${getProfitLossColor(rate)}`}>
                      {formatProfitRate(rate)}
                    </p>
                  )}
                </div>
              </div>
              {pos.lastExportedAt && (
                <p className="text-xs text-slate-400 mt-2">최근 문서 공개: {formatTime(pos.lastExportedAt)}</p>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
