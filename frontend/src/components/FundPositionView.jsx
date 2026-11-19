import { useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import api from '../lib/api'

/**
 * 펀드팀 뷰 — 게시판에서 FUND 타입 팀 선택 시 표시
 * 동아리 멤버 누구나 열람 가능 (공개용 Position 리스트)
 */
export default function FundPositionView({ teamId, teamName }) {
  const { clubInfo } = useOutletContext()
  const [positions, setPositions] = useState([])
  const [loading, setLoading] = useState(false)
  const [statusFilter, setStatusFilter] = useState(null) // null=전체, 'ACTIVE', 'CLOSED'
  const [profitPublic, setProfitPublic] = useState(false)

  useEffect(() => {
    if (!clubInfo?.clubId || !teamId) return
    // 수익률 공개 설정 조회
    api.get(`/clubs/${clubInfo.clubId}/boards/fund-teams/${teamId}/settings`)
      .then(data => setProfitPublic(data?.profitPublic || false))
      .catch(() => setProfitPublic(false))
  }, [clubInfo?.clubId, teamId])

  useEffect(() => {
    if (!clubInfo?.clubId || !teamId) return
    setLoading(true)
    const url = statusFilter
      ? `/clubs/${clubInfo.clubId}/boards/fund-teams/${teamId}/positions?status=${statusFilter}`
      : `/clubs/${clubInfo.clubId}/boards/fund-teams/${teamId}/positions`
    api.get(url)
      .then(data => setPositions(data || []))
      .catch(() => setPositions([]))
      .finally(() => setLoading(false))
  }, [clubInfo?.clubId, teamId, statusFilter])

  const formatNumber = (num) => {
    if (num == null) return '-'
    return Number(num).toLocaleString('ko-KR')
  }

  const statusLabel = (status) => {
    switch (status) {
      case 'ACTIVE': return '진행중'
      case 'CLOSED': return '종료'
      case 'PENDING_APPROVAL': return '승인대기'
      default: return status || '-'
    }
  }

  const statusColor = (status) => {
    switch (status) {
      case 'ACTIVE': return 'bg-emerald-50 text-emerald-700'
      case 'CLOSED': return 'bg-slate-100 text-slate-500'
      case 'PENDING_APPROVAL': return 'bg-amber-50 text-amber-700'
      default: return 'bg-slate-100 text-slate-500'
    }
  }

  const filters = [
    { key: null, label: '전체' },
    { key: 'ACTIVE', label: '진행중' },
    { key: 'CLOSED', label: '종료' },
  ]

  return (
    <div className="flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* 헤더 */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
        <div className="flex items-center gap-2">
          <h3 className="text-base font-semibold text-slate-900">{teamName || '펀드팀'}</h3>
          <span className="text-xs text-slate-400">종목 현황</span>
        </div>
      </div>

      {/* 상태 필터 */}
      <div className="px-6 py-2 border-b border-slate-100 flex items-center gap-2">
        {filters.map(f => (
          <button
            key={f.key ?? 'all'}
            onClick={() => { setStatusFilter(f.key) }}
            className={`px-3 py-1 text-xs rounded-full border transition-colors cursor-pointer ${
              statusFilter === f.key
                ? 'bg-slate-700 text-white border-slate-700'
                : 'bg-white text-slate-500 border-slate-200 hover:border-slate-400'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* 종목 리스트 */}
      <div>
        {loading ? (
          <div className="p-6 text-sm text-slate-400">로딩 중...</div>
        ) : positions.length === 0 ? (
          <div className="p-6 text-sm text-slate-400">등록된 종목이 없습니다</div>
        ) : (
          <div>
            {/* 테이블 헤더 */}
            <div className={`hidden sm:grid ${profitPublic ? 'grid-cols-[1fr_80px_80px_100px_100px_100px_80px]' : 'grid-cols-[1fr_80px_80px_100px_100px_80px]'} gap-2 px-6 py-2 text-xs text-slate-400 font-medium border-b border-slate-100`}>
              <span>종목</span>
              <span className="text-right">시장</span>
              <span className="text-right">수량</span>
              <span className="text-right">평균매수가</span>
              <span className="text-right">매수금액</span>
              {profitPublic && <span className="text-right">수익률</span>}
              <span className="text-center">상태</span>
            </div>

            {positions.map(pos => (
              <div
                key={pos.id}
                className="px-6 py-3 border-b border-slate-50 hover:bg-slate-50 transition-colors"
              >
                {/* 데스크톱 */}
                <div className={`hidden sm:grid ${profitPublic ? 'grid-cols-[1fr_80px_80px_100px_100px_100px_80px]' : 'grid-cols-[1fr_80px_80px_100px_100px_80px]'} gap-2 items-center`}>
                  <div className="min-w-0">
                    <span className="text-sm font-medium text-slate-900">{pos.tickerName || pos.ticker}</span>
                    {pos.ticker && pos.tickerName && (
                      <span className="ml-1.5 text-xs text-slate-400">{pos.ticker}</span>
                    )}
                  </div>
                  <span className="text-xs text-slate-500 text-right">{pos.market || '-'}</span>
                  <span className="text-xs text-slate-700 text-right font-medium">{formatNumber(pos.totalQuantity)}</span>
                  <span className="text-xs text-slate-700 text-right">{formatNumber(pos.averageBuyPrice)}</span>
                  <span className="text-xs text-slate-700 text-right">{formatNumber(pos.totalBuyAmount)}</span>
                  {profitPublic && (
                    <span className={`text-xs text-right font-medium ${Number(pos.profitRate || 0) >= 0 ? 'text-rose-600' : 'text-blue-600'}`}>
                      {pos.profitRate != null ? (Number(pos.profitRate) * 100).toFixed(2) + '%' : '-'}
                    </span>
                  )}
                  <div className="flex justify-center">
                    <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${statusColor(pos.status)}`}>
                      {statusLabel(pos.status)}
                    </span>
                  </div>
                </div>

                {/* 모바일 */}
                <div className="sm:hidden">
                  <div className="flex items-center justify-between">
                    <div className="min-w-0 flex-1">
                      <span className="text-sm font-medium text-slate-900">{pos.tickerName || pos.ticker}</span>
                      {pos.ticker && pos.tickerName && (
                        <span className="ml-1.5 text-xs text-slate-400">{pos.ticker}</span>
                      )}
                    </div>
                    <span className={`ml-2 px-2 py-0.5 text-xs rounded-full font-medium shrink-0 ${statusColor(pos.status)}`}>
                      {statusLabel(pos.status)}
                    </span>
                  </div>
                  <div className="mt-1 text-xs text-slate-500 flex items-center gap-3">
                    <span>{pos.market || '-'}</span>
                    <span>{formatNumber(pos.totalQuantity)}주</span>
                    <span>@{formatNumber(pos.averageBuyPrice)}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 문서 영역 — 향후 DecisionNote 내보내기 구현 시 활성화 */}
      {!loading && positions.length > 0 && (
        <div className="px-6 py-4 border-t border-slate-100">
          <p className="text-xs text-slate-400">
            의사결정서 / 운용보고서 내보내기 기능은 준비 중입니다.
          </p>
        </div>
      )}
    </div>
  )
}
