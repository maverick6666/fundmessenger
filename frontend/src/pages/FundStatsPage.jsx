import { useState, useEffect, useMemo } from 'react'
import { useParams, useOutletContext } from 'react-router-dom'
import {
  BarChart3, TrendingUp, Trophy, Target, DollarSign,
  List, LayoutGrid, X, ChevronDown, ChevronUp, User,
} from 'lucide-react'
import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer,
} from 'recharts'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import BenchmarkChart from '../components/BenchmarkChart'
import FundPageWrapper from '../components/fund/FundPageWrapper'
import {
  formatNumber, formatCurrency, formatPercent, formatProfitRate,
  formatHours, getProfitLossColor,
} from '../lib/format'

// ── Constants ──

const PERIOD_OPTIONS = [
  { key: '1w', label: '1주' },
  { key: '1m', label: '1개월' },
  { key: '3m', label: '분기' },
  { key: 'all', label: '전체' },
]

const TAB_OPTIONS = [
  { key: 'team', label: '팀 전체' },
  { key: 'my', label: '내 성과' },
]

const CHART_TYPE_OPTIONS = [
  { key: 'total', label: '총 자산' },
  { key: 'realized', label: '실현손익' },
  { key: 'unrealized', label: '미실현손익' },
]

const TICKER_FILTER_OPTIONS = [
  { key: 'open', label: '진행중' },
  { key: 'closed', label: '종료됨' },
  { key: 'all', label: '전체' },
]

// ── Heatmap color scale (v1 premium design) ──

function getHeatmapColor(profitRate) {
  if (profitRate == null) return 'bg-slate-100 text-slate-600'
  const pct = profitRate * 100
  if (pct >= 20) return 'bg-red-600 text-white'
  if (pct >= 10) return 'bg-red-500 text-white'
  if (pct >= 5) return 'bg-red-400 text-white'
  if (pct >= 2) return 'bg-red-200 text-red-800'
  if (pct >= 0) return 'bg-red-50 text-red-700'
  if (pct >= -2) return 'bg-blue-50 text-blue-700'
  if (pct >= -5) return 'bg-blue-200 text-blue-800'
  if (pct >= -10) return 'bg-blue-400 text-white'
  return 'bg-primary text-white'
}

// ── Main Component ──

export default function FundStatsPage() {
  const { teamId } = useParams()
  const { clubInfo } = useOutletContext()
  const clubId = clubInfo?.clubId
  const tid = Number(teamId)

  // UI state
  const [activeTab, setActiveTab] = useState('team')
  const [periodFilter, setPeriodFilter] = useState('all')
  const [chartType, setChartType] = useState('total')
  const [tickerFilter, setTickerFilter] = useState('open')
  const [tickerViewMode, setTickerViewMode] = useState('table')
  const [snapshotDetail, setSnapshotDetail] = useState(null)

  // Data state
  const [settings, setSettings] = useState(null)
  const [positions, setPositions] = useState([])
  const [closedPositions, setClosedPositions] = useState([])
  const [snapshots, setSnapshots] = useState([])
  const [loading, setLoading] = useState(true)
  const [chartLoading, setChartLoading] = useState(false)
  const [myStats, setMyStats] = useState(null)
  const [myStatsLoading, setMyStatsLoading] = useState(false)

  // ── Fetch core data ──
  useEffect(() => {
    if (!clubId) return
    setLoading(true)
    Promise.all([
      api.get(`/clubs/${clubId}/fund/teams/${tid}/settings`).catch(() => null),
      api.get(`/clubs/${clubId}/fund/teams/${tid}/positions?status=OPEN`).catch(() => []),
      api.get(`/clubs/${clubId}/fund/teams/${tid}/positions?status=CLOSED`).catch(() => []),
    ])
      .then(([s, openP, closedP]) => {
        setSettings(s)
        setPositions(Array.isArray(openP) ? openP : [])
        setClosedPositions(Array.isArray(closedP) ? closedP : [])
      })
      .catch(e => handleApiError(e, '통계 조회'))
      .finally(() => setLoading(false))
  }, [clubId, tid])

  // ── Fetch my stats when "my" tab is selected ──
  useEffect(() => {
    if (!clubId || activeTab !== 'my') return
    setMyStatsLoading(true)
    api.get(`/clubs/${clubId}/fund/teams/${tid}/stats/my`)
      .then(data => setMyStats(data))
      .catch(() => {
        // API might not exist yet — gracefully fall back to position-derived data
        setMyStats(null)
      })
      .finally(() => setMyStatsLoading(false))
  }, [clubId, tid, activeTab])

  // ── Fetch snapshots (period-dependent) ──
  useEffect(() => {
    if (!clubId) return
    setChartLoading(true)
    api.get(`/clubs/${clubId}/fund/teams/${tid}/stats/snapshots${periodFilter !== 'all' ? `?period=${periodFilter}` : ''}`)
      .then(d => setSnapshots(Array.isArray(d) ? d : []))
      .catch(() => setSnapshots([]))
      .finally(() => setChartLoading(false))
  }, [clubId, tid, periodFilter])

  // ── Derived values ──
  const allPositions = useMemo(() => [...positions, ...closedPositions], [positions, closedPositions])

  const totalBuy = positions.reduce((s, p) => s + Number(p.totalBuyAmount || 0), 0)
  const totalEval = positions.reduce((s, p) => s + Number(p.evaluationAmount || p.totalBuyAmount || 0), 0)
  const totalUnrealized = totalEval - totalBuy
  const totalUnrealizedRate = totalBuy > 0 ? totalUnrealized / totalBuy : 0

  const closedPnl = closedPositions.reduce((s, p) => s + Number(p.realizedProfitLoss || 0), 0)
  const closedCount = closedPositions.length
  const winCount = closedPositions.filter(p => Number(p.realizedProfitLoss || 0) > 0).length
  const loseCount = closedPositions.filter(p => Number(p.realizedProfitLoss || 0) < 0).length
  const winRate = closedCount > 0 ? winCount / closedCount : 0
  const avgProfitRate = closedCount > 0
    ? closedPositions.reduce((s, p) => s + Number(p.profitRate || 0), 0) / closedCount
    : 0
  const avgHoldingHours = closedCount > 0
    ? closedPositions.reduce((s, p) => s + Number(p.holdingPeriodHours || 0), 0) / closedCount
    : 0

  // ── Chart config ──
  const chartConfig = useMemo(() => {
    const configs = {
      total: { dataKey: 'totalKrw', label: '총 자산', formatter: v => formatCurrency(v, 'KRX') },
      realized: { dataKey: 'realizedPnl', label: '실현손익', formatter: v => formatCurrency(v, 'KRX') },
      unrealized: { dataKey: 'unrealizedPnl', label: '미실현손익', formatter: v => formatCurrency(v, 'KRX') },
    }
    return configs[chartType] || configs.total
  }, [chartType])

  const chartColor = totalUnrealizedRate >= 0 ? '#10b981' : '#f43f5e'

  // ── Ticker breakdown ──
  const tickerMap = useMemo(() => {
    const map = {}
    for (const p of allPositions) {
      const key = p.ticker || p.tickerName || 'UNKNOWN'
      if (!map[key]) map[key] = { ticker: key, tickerName: p.tickerName || key, market: p.market || 'KRX', openPositions: [], closedPositionsList: [] }
      if (p.status === 'CLOSED') map[key].closedPositionsList.push(p)
      else map[key].openPositions.push(p)
    }
    return map
  }, [allPositions])

  const filteredTickers = useMemo(() => {
    const arr = Object.values(tickerMap)
    if (tickerFilter === 'open') return arr.filter(t => t.openPositions.length > 0)
    if (tickerFilter === 'closed') return arr.filter(t => t.closedPositionsList.length > 0)
    return arr
  }, [tickerMap, tickerFilter])

  // ── Snapshot click ──
  const handleChartClick = (data) => {
    if (!data?.activePayload?.[0]?.payload) return
    const payload = data.activePayload[0].payload
    setSnapshotDetail(snapshotDetail?.snapshotDate === payload.snapshotDate ? null : payload)
  }

  // ── My stats values (from API or fallback to position-derived) ──
  const myTotalTrades = myStats?.totalTrades ?? closedCount
  const myWinRate = myStats?.winRate != null ? myStats.winRate / 100 : winRate
  const myTotalPnl = myStats?.totalProfitLoss ?? closedPnl
  const myAvgProfitRate = myStats?.avgProfitRate ?? avgProfitRate
  const myWinCount = myStats?.successCount ?? winCount
  const myLoseCount = myStats?.failCount ?? loseCount
  const myAvgHoldingHours = myStats?.avgHoldingHours ?? avgHoldingHours
  const myBestTrade = myStats?.bestTrade ?? null
  const myWorstTrade = myStats?.worstTrade ?? null

  // ── Loading skeleton ──
  if (loading) {
    return (
      <FundPageWrapper teamId={tid}>
        <div className="space-y-4">
          {[1, 2, 3].map(i => (
            <div key={i} className="animate-pulse bg-white rounded-2xl border border-slate-200 h-32" />
          ))}
        </div>
      </FundPageWrapper>
    )
  }

  return (
    <FundPageWrapper teamId={tid}>
      {/* ── Header: Tab + Period Filter ── */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        {/* Tab Toggle */}
        <div className="flex bg-slate-100 rounded-lg p-0.5">
          {TAB_OPTIONS.map(t => (
            <button
              key={t.key}
              onClick={() => setActiveTab(t.key)}
              className={`cursor-pointer px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                activeTab === t.key
                  ? 'bg-white text-slate-800 shadow-sm'
                  : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* Period Pill Buttons */}
        <div className="flex bg-slate-100 rounded-lg p-0.5 gap-0.5">
          {PERIOD_OPTIONS.map(p => (
            <button
              key={p.key}
              onClick={() => setPeriodFilter(p.key)}
              className={`cursor-pointer px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                periodFilter === p.key
                  ? 'bg-white text-slate-800 shadow-sm'
                  : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {/* ══════════ TEAM TAB ══════════ */}
      {activeTab === 'team' && (
        <div className="space-y-5">
          {/* ── Asset History Chart ── */}
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <div className="px-5 pt-4 pb-2">
              <div className="flex items-center justify-between mb-1">
                <span className="text-sm font-semibold text-slate-800">자산 추이</span>
              </div>
              {/* Chart type toggle */}
              <div className="flex gap-1 mt-2">
                {CHART_TYPE_OPTIONS.map(opt => (
                  <button
                    key={opt.key}
                    onClick={() => setChartType(opt.key)}
                    className={`cursor-pointer px-2.5 py-1 text-xs rounded-md transition-colors ${
                      chartType === opt.key
                        ? 'bg-slate-800 text-white font-medium'
                        : 'text-slate-500 hover:text-slate-700 hover:bg-slate-100'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Chart */}
            <div className="h-48 w-full px-2">
              {chartLoading ? (
                <div className="h-full flex items-center justify-center text-slate-400 text-sm">차트 로딩중...</div>
              ) : snapshots.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart
                    data={snapshots}
                    margin={{ top: 10, right: 10, left: 10, bottom: 0 }}
                    onClick={handleChartClick}
                    style={{ cursor: 'pointer' }}
                  >
                    <defs>
                      <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor={chartColor} stopOpacity={0.3} />
                        <stop offset="95%" stopColor={chartColor} stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <XAxis
                      dataKey="snapshotDate"
                      axisLine={false}
                      tickLine={false}
                      tick={{ fontSize: 10, fill: '#94a3b8' }}
                      interval="preserveStartEnd"
                    />
                    <YAxis hide domain={['auto', 'auto']} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'rgba(15, 23, 42, 0.95)',
                        border: 'none',
                        borderRadius: '8px',
                        padding: '8px 12px',
                        fontSize: '12px',
                      }}
                      labelStyle={{ color: '#94a3b8', fontSize: '11px' }}
                      formatter={(value) => [chartConfig.formatter(value), chartConfig.label]}
                    />
                    <Area
                      type="monotone"
                      dataKey={chartConfig.dataKey}
                      stroke={chartColor}
                      strokeWidth={2}
                      fill="url(#chartGradient)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex items-center justify-center text-slate-400 text-sm">
                  자산 히스토리 데이터가 없습니다
                </div>
              )}
            </div>

            {/* Snapshot Detail (chart date click) */}
            {snapshotDetail && (
              <div className="border-t border-slate-100 bg-slate-50/50">
                <div className="flex items-center justify-between px-5 py-2.5">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-slate-700">{snapshotDetail.snapshotDate} 스냅샷</span>
                    <span className="text-xs text-slate-400">
                      총 {formatCurrency(snapshotDetail.totalKrw, 'KRX')}
                    </span>
                  </div>
                  <button onClick={() => setSnapshotDetail(null)} className="cursor-pointer p-1 rounded text-slate-400 hover:text-slate-600 hover:bg-slate-200">
                    <X size={14} />
                  </button>
                </div>
                <div className="grid grid-cols-4 gap-2 px-5 py-2 text-xs border-t border-slate-100">
                  <div>
                    <span className="text-slate-400">실현</span>
                    <p className={`font-medium ${getProfitLossColor(snapshotDetail.realizedPnl)}`}>
                      {formatCurrency(snapshotDetail.realizedPnl, 'KRX')}
                    </p>
                  </div>
                  <div>
                    <span className="text-slate-400">미실현</span>
                    <p className={`font-medium ${getProfitLossColor(snapshotDetail.unrealizedPnl)}`}>
                      {formatCurrency(snapshotDetail.unrealizedPnl, 'KRX')}
                    </p>
                  </div>
                  <div>
                    <span className="text-slate-400">KRW 현금</span>
                    <p className="font-medium text-slate-700">{formatCurrency(snapshotDetail.krwCash, 'KRX')}</p>
                  </div>
                  <div>
                    <span className="text-slate-400">USD 현금</span>
                    <p className="font-medium text-slate-700">{formatCurrency(snapshotDetail.usdCash, 'USD')}</p>
                  </div>
                </div>
                {/* Position details within snapshot */}
                {snapshotDetail.positionDetails?.length > 0 && (
                  <div className="px-5 py-2 border-t border-slate-100 space-y-1.5">
                    {snapshotDetail.positionDetails.map((pos, i) => (
                      <div key={i} className="flex items-center justify-between text-xs">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-slate-700">{pos.tickerName || pos.ticker}</span>
                          <span className="text-slate-400">{pos.ticker}</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="text-slate-500 tabular-nums">{formatCurrency(pos.evalAmount, pos.market)}</span>
                          <span className={`font-medium tabular-nums ${getProfitLossColor(pos.pnl)}`}>
                            {formatProfitRate(pos.pnlRate)}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* ── Currency Breakdown Cards ── */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <CurrencyCard label="KRW 보유" value={settings?.initialCapitalKrw != null ? Number(settings.initialCapitalKrw) - totalBuy : null} market="KRX" />
            <CurrencyCard label="KRW 평가" value={totalEval > 0 ? totalEval : null} market="KRX" />
            <CurrencyCard label="USD 보유" value={settings?.initialCapitalUsd != null ? Number(settings.initialCapitalUsd) : null} market="USD" />
            <CurrencyCard label="USD 평가" value={null} market="USD" />
          </div>

          {/* ── Realized Performance Cards ── */}
          <div>
            <h3 className="text-sm font-medium text-slate-500 mb-3">실현 성과</h3>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <StatCard label="종료 거래" value={closedCount} sub={`${winCount}승 / ${loseCount}패`} />
              <WinRateCard winRate={winRate} />
              <StatCard
                label="실현 손익"
                value={formatCurrency(closedPnl, 'KRX')}
                valueColor={getProfitLossColor(closedPnl)}
              />
              <StatCard
                label="평균 수익률"
                value={formatProfitRate(avgProfitRate)}
                valueColor={getProfitLossColor(avgProfitRate)}
              />
            </div>
          </div>

          {/* ── Team Detail Metrics (v1 pattern: 4-col grid) ── */}
          <div className="bg-white rounded-2xl border border-slate-200 p-5">
            <h4 className="text-sm font-semibold text-slate-800 mb-4">팀 상세지표</h4>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              <MiniStat label="수익 거래" value={`${winCount}건`} color="text-red-600" />
              <MiniStat label="손실 거래" value={`${loseCount}건`} color="text-blue-600" />
              <MiniStat label="평균 보유시간" value={formatHours(avgHoldingHours)} />
              <MiniStat label="평균 수익률" value={formatProfitRate(avgProfitRate)} color={getProfitLossColor(avgProfitRate)} />
            </div>
          </div>

          {/* ── Benchmark Chart ── */}
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <div className="flex items-center gap-1.5 px-5 py-3 border-b border-slate-100">
              <BarChart3 size={16} className="text-slate-400" />
              <span className="text-sm font-semibold text-slate-800">벤치마크 비교</span>
            </div>
            <div className="p-4">
              <BenchmarkChart clubId={clubId} teamId={tid} />
            </div>
          </div>

          {/* ── Ticker Breakdown ── */}
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <div className="flex items-center justify-between flex-wrap gap-2 px-5 py-3 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-slate-800">종목별 현황</span>
                <span className="text-xs text-slate-400">{filteredTickers.length}개</span>
              </div>
              <div className="flex items-center gap-2">
                {/* View mode toggle */}
                <div className="flex bg-slate-100 p-0.5 rounded-md">
                  <button
                    onClick={() => setTickerViewMode('table')}
                    className={`cursor-pointer p-1.5 rounded ${tickerViewMode === 'table' ? 'bg-white shadow-sm' : 'text-slate-400 hover:text-slate-600'}`}
                    title="테이블 뷰"
                  >
                    <List size={14} />
                  </button>
                  <button
                    onClick={() => setTickerViewMode('heatmap')}
                    className={`cursor-pointer p-1.5 rounded ${tickerViewMode === 'heatmap' ? 'bg-white shadow-sm' : 'text-slate-400 hover:text-slate-600'}`}
                    title="히트맵 뷰"
                  >
                    <LayoutGrid size={14} />
                  </button>
                </div>
                {/* Filter pills */}
                <div className="flex gap-1">
                  {TICKER_FILTER_OPTIONS.map(f => (
                    <button
                      key={f.key}
                      onClick={() => setTickerFilter(f.key)}
                      className={`cursor-pointer px-2.5 py-1 text-xs rounded-md transition-colors ${
                        tickerFilter === f.key
                          ? 'bg-slate-800 text-white font-medium'
                          : 'text-slate-500 hover:text-slate-700 hover:bg-slate-100'
                      }`}
                    >
                      {f.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Heatmap View (v1 premium design) */}
            {tickerViewMode === 'heatmap' && (
              <div className="p-4">
                {filteredTickers.length === 0 ? (
                  <div className="text-center py-8 text-sm text-slate-400">해당 종목이 없습니다</div>
                ) : (
                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2">
                    {filteredTickers.map((t, i) => {
                      const rate = getTickerRate(t, tickerFilter)
                      const colorClass = getHeatmapColor(rate)
                      return (
                        <div
                          key={i}
                          className={`${colorClass} rounded-lg p-3 flex flex-col items-center justify-center min-h-[100px] transition-transform hover:scale-105 cursor-default`}
                        >
                          <span className="font-semibold text-sm text-center truncate w-full">{t.tickerName}</span>
                          <span className="opacity-70 text-xs mt-0.5 font-mono">{t.ticker}</span>
                          <span className="font-bold text-lg mt-1 tabular-nums">
                            {rate != null && rate !== 0 ? formatProfitRate(rate * 100) : '-'}
                          </span>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )}

            {/* Table View */}
            {tickerViewMode === 'table' && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-100 text-slate-400 bg-slate-50/50">
                      <th className="py-2 px-4 text-left font-medium text-xs">종목</th>
                      <th className="py-2 px-4 text-right font-medium text-xs">투자금</th>
                      <th className="py-2 px-4 text-right font-medium text-xs">평가/실현</th>
                      <th className="py-2 px-4 text-right font-medium text-xs">손익</th>
                      <th className="py-2 px-4 text-right font-medium text-xs">수익률</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredTickers.length === 0 ? (
                      <tr><td colSpan={5} className="text-center py-8 text-sm text-slate-400">해당 종목이 없습니다</td></tr>
                    ) : filteredTickers.map((t, i) => {
                      const invested = sumField(tickerFilter === 'closed' ? t.closedPositionsList : t.openPositions, 'totalBuyAmount')
                      const evaluation = sumField(t.openPositions, 'evaluationAmount')
                      const pnl = tickerFilter === 'closed'
                        ? sumField(t.closedPositionsList, 'realizedProfitLoss')
                        : evaluation - invested
                      const rate = invested > 0 ? pnl / invested : 0
                      return (
                        <tr key={i} className="border-b border-slate-50 hover:bg-slate-50 transition-colors">
                          <td className="py-2.5 px-4">
                            <div className="font-medium text-slate-700">{t.tickerName}</div>
                            <div className="text-xs text-slate-400">{t.ticker} · {t.market}</div>
                          </td>
                          <td className="py-2.5 px-4 text-right tabular-nums text-slate-600">
                            {formatCurrency(invested, t.market)}
                          </td>
                          <td className="py-2.5 px-4 text-right tabular-nums text-slate-600">
                            {tickerFilter === 'closed'
                              ? formatCurrency(sumField(t.closedPositionsList, 'realizedProfitLoss') + invested, t.market)
                              : evaluation > 0 ? formatCurrency(evaluation, t.market) : '-'
                            }
                          </td>
                          <td className={`py-2.5 px-4 text-right tabular-nums ${getProfitLossColor(pnl)}`}>
                            {pnl !== 0 ? `${pnl > 0 ? '+' : ''}${formatCurrency(pnl, t.market)}` : '-'}
                          </td>
                          <td className={`py-2.5 px-4 text-right font-medium tabular-nums ${getProfitLossColor(rate)}`}>
                            {rate !== 0 ? formatProfitRate(rate * 100) : '-'}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ══════════ MY STATS TAB ══════════ */}
      {activeTab === 'my' && (
        <div className="space-y-5">
          {/* 4-column summary row */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <StatCard label="총 거래" value={myTotalTrades} />
            <StatCard label="승률" value={formatPercent(myWinRate)} />
            <StatCard
              label="총 손익"
              value={formatCurrency(myTotalPnl, 'KRX')}
              valueColor={getProfitLossColor(myTotalPnl)}
            />
            <StatCard
              label="평균 수익률"
              value={formatProfitRate(myAvgProfitRate)}
              valueColor={getProfitLossColor(myAvgProfitRate)}
            />
          </div>

          {/* 2-column: Detail + Best/Worst Trade */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Detail Metrics */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5">
              <h4 className="text-sm font-semibold text-slate-800 mb-4">상세 지표</h4>
              <div className="space-y-3">
                <DetailRow label="수익 거래" value={myWinCount} color="text-red-600" />
                <DetailRow label="손실 거래" value={myLoseCount} color="text-blue-600" />
                <DetailRow label="평균 보유 시간" value={formatHours(myAvgHoldingHours)} />
                <DetailRow label="평균 수익률" value={formatProfitRate(myAvgProfitRate)} color={getProfitLossColor(myAvgProfitRate)} />
              </div>
            </div>

            {/* Best / Worst Trade */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5">
              <h4 className="text-sm font-semibold text-slate-800 mb-4">최고 / 최악 거래</h4>
              {myBestTrade || myWorstTrade ? (
                <div className="space-y-3">
                  {myBestTrade && (
                    <div className="p-3 bg-green-50 rounded-lg">
                      <p className="text-xs text-slate-400 mb-0.5">최고 거래</p>
                      <p className="font-medium text-slate-700">{myBestTrade.tickerName || myBestTrade.ticker}</p>
                      <p className="text-red-600 font-semibold tabular-nums">{formatProfitRate(myBestTrade.profitRate)}</p>
                    </div>
                  )}
                  {myWorstTrade && (
                    <div className="p-3 bg-red-50 rounded-lg">
                      <p className="text-xs text-slate-400 mb-0.5">최악 거래</p>
                      <p className="font-medium text-slate-700">{myWorstTrade.tickerName || myWorstTrade.ticker}</p>
                      <p className="text-blue-600 font-semibold tabular-nums">{formatProfitRate(myWorstTrade.profitRate)}</p>
                    </div>
                  )}
                </div>
              ) : (
                <BestWorstTrades positions={closedPositions} />
              )}
            </div>
          </div>

          {/* Benchmark (also on My tab) */}
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <div className="flex items-center gap-1.5 px-5 py-3 border-b border-slate-100">
              <BarChart3 size={16} className="text-slate-400" />
              <span className="text-sm font-semibold text-slate-800">벤치마크 비교</span>
            </div>
            <div className="p-4">
              <BenchmarkChart clubId={clubId} teamId={tid} />
            </div>
          </div>
        </div>
      )}
    </FundPageWrapper>
  )
}

// ── Sub-components ──

function CurrencyCard({ label, value, market }) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-4">
      <span className="text-xs text-slate-400 font-medium">{label}</span>
      <p className="text-lg font-semibold text-slate-700 tabular-nums mt-1">
        {value != null ? formatCurrency(value, market) : '-'}
      </p>
    </div>
  )
}

function StatCard({ label, value, sub, valueColor }) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-4">
      <p className="text-xs text-slate-400 font-medium mb-1">{label}</p>
      <p className={`text-xl font-bold tabular-nums ${valueColor || 'text-slate-800'}`}>{value}</p>
      {sub && <p className="text-xs text-slate-400 mt-1">{sub}</p>}
    </div>
  )
}

function WinRateCard({ winRate }) {
  const pct = Math.round(winRate * 100)
  const circumference = 2 * Math.PI * 20
  const dashLen = winRate * circumference
  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-4">
      <p className="text-xs text-slate-400 font-medium mb-1">승률</p>
      <div className="flex items-center gap-3">
        <div className="relative w-12 h-12 shrink-0">
          <svg className="w-12 h-12 -rotate-90">
            <circle cx="24" cy="24" r="20" stroke="#e2e8f0" strokeWidth="4" fill="none" />
            <circle
              cx="24" cy="24" r="20"
              stroke="#10b981" strokeWidth="4" fill="none"
              strokeDasharray={`${dashLen} ${circumference}`}
              strokeLinecap="round"
            />
          </svg>
          <span className="absolute inset-0 flex items-center justify-center text-xs font-bold text-slate-700">
            {pct}%
          </span>
        </div>
        <p className="text-xl font-bold text-slate-800 tabular-nums">{formatPercent(winRate)}</p>
      </div>
    </div>
  )
}

function MiniStat({ label, value, color }) {
  return (
    <div>
      <span className="text-xs text-slate-400">{label}</span>
      <p className={`text-lg font-semibold tabular-nums ${color || 'text-slate-700'}`}>{value}</p>
    </div>
  )
}

function DetailRow({ label, value, color }) {
  return (
    <div className="flex justify-between">
      <span className="text-sm text-slate-400">{label}</span>
      <span className={`font-medium tabular-nums ${color || 'text-slate-700'}`}>{value}</span>
    </div>
  )
}

function BestWorstTrades({ positions }) {
  if (!positions || positions.length === 0) {
    return <p className="text-sm text-slate-400">종료된 거래가 없습니다</p>
  }
  const sorted = [...positions].sort((a, b) => Number(b.profitRate || 0) - Number(a.profitRate || 0))
  const best = sorted[0]
  const worst = sorted[sorted.length - 1]

  return (
    <div className="space-y-3">
      {best && Number(best.profitRate || 0) > 0 && (
        <div className="p-3 bg-green-50 rounded-lg">
          <p className="text-xs text-slate-400 mb-0.5">최고 거래</p>
          <p className="font-medium text-slate-700">{best.tickerName || best.ticker}</p>
          <p className="text-red-600 font-semibold tabular-nums">{formatProfitRate(best.profitRate)}</p>
        </div>
      )}
      {worst && Number(worst.profitRate || 0) < 0 && (
        <div className="p-3 bg-red-50 rounded-lg">
          <p className="text-xs text-slate-400 mb-0.5">최악 거래</p>
          <p className="font-medium text-slate-700">{worst.tickerName || worst.ticker}</p>
          <p className="text-blue-600 font-semibold tabular-nums">{formatProfitRate(worst.profitRate)}</p>
        </div>
      )}
      {(!best || Number(best.profitRate || 0) <= 0) && (!worst || Number(worst.profitRate || 0) >= 0) && (
        <p className="text-sm text-slate-400">수익/손실 거래 없음</p>
      )}
    </div>
  )
}

// ── Helpers ──

function sumField(arr, field) {
  return (arr || []).reduce((s, p) => s + Number(p[field] || 0), 0)
}

function getTickerRate(t, filter) {
  if (filter === 'open' && t.openPositions.length > 0) {
    const inv = sumField(t.openPositions, 'totalBuyAmount')
    const eval_ = sumField(t.openPositions, 'evaluationAmount')
    return inv > 0 ? (eval_ - inv) / inv : 0
  }
  if (filter === 'closed' && t.closedPositionsList.length > 0) {
    const inv = sumField(t.closedPositionsList, 'totalBuyAmount')
    const pnl = sumField(t.closedPositionsList, 'realizedProfitLoss')
    return inv > 0 ? pnl / inv : 0
  }
  const allPos = [...t.openPositions, ...t.closedPositionsList]
  const inv = sumField(allPos, 'totalBuyAmount')
  const pnl = sumField(t.closedPositionsList, 'realizedProfitLoss') +
    sumField(t.openPositions, 'evaluationAmount') - sumField(t.openPositions, 'totalBuyAmount')
  return inv > 0 ? pnl / inv : 0
}
