import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useOutletContext } from 'react-router-dom'
import {
  Settings, ArrowRightLeft, AlertCircle, ChevronRight, FileText,
  TrendingUp, TrendingDown, Users, Trophy, X, ArrowRight, RefreshCw,
} from 'lucide-react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import useToastStore from '../store/toastStore'
import useSidePanelStore from '../store/sidePanelStore'
import { useGuardedNavigate } from '../components/UnsavedChangesProvider'
import FundPageWrapper from '../components/fund/FundPageWrapper'
import DocumentCard from '../components/fund/DocumentCard'
import { MiniTargetProgressBar } from '../components/fund/ProfitProgressBar'
import {
  formatNumber, formatCurrency, formatProfitRate, formatTime,
  getProfitLossColor, formatPrice, formatQuantity, formatPercent,
} from '../lib/format'
import { PRICE_REFRESH_INTERVAL_MS } from '../lib/fundConstants'

const REQUEST_TYPE_LABEL = { BUY: '매수', SELL: '매도' }
const REQUEST_STATUS_PILL = {
  PENDING:    { bg: 'bg-amber-50', text: 'text-amber-700', label: '대기' },
  APPROVED:   { bg: 'bg-emerald-50', text: 'text-emerald-700', label: '승인' },
  REJECTED:   { bg: 'bg-rose-50', text: 'text-rose-700', label: '거절' },
  DISCUSSION: { bg: 'bg-blue-50', text: 'text-blue-700', label: '토론' },
  EXECUTED:   { bg: 'bg-slate-100', text: 'text-slate-600', label: '체결' },
}

const MEDAL = ['', '\u{1F947}', '\u{1F948}', '\u{1F949}']

export default function FundDashboardPage() {
  const { teamId } = useParams()
  const { clubInfo } = useOutletContext()
  const navigate = useGuardedNavigate()
  const addToast = useToastStore(s => s.addToast)
  const { openDocument } = useSidePanelStore()
  const clubId = clubInfo?.clubId
  const tid = Number(teamId)

  const myTeamEntry = clubInfo?.myTeams?.find(t => t.teamId === tid)
  const canManage = myTeamEntry?.teamRole === 'LEADER'

  // ── State ──
  const [activeTab, setActiveTab] = useState('dashboard')
  const [loading, setLoading] = useState(true)
  const [settings, setSettings] = useState(null)
  const [positions, setPositions] = useState([])
  const [requests, setRequests] = useState([])
  const [decisionNotes, setDecisionNotes] = useState([])
  const [reportNotes, setReportNotes] = useState([])
  const [noteTab, setNoteTab] = useState('DECISION')

  // Real-time prices
  const [priceMap, setPriceMap] = useState({})
  const priceTimerRef = useRef(null)

  // Modals
  const [showSettingsModal, setShowSettingsModal] = useState(false)
  const [showExchangeModal, setShowExchangeModal] = useState(false)

  // ── Fetch all dashboard data ──
  const fetchAll = useCallback(async () => {
    if (!clubId) return
    setLoading(true)
    try {
      const [settingsData, posData, reqData, decData, repData] = await Promise.all([
        api.get(`/clubs/${clubId}/fund/teams/${tid}/settings`).catch(() => null),
        api.get(`/clubs/${clubId}/fund/teams/${tid}/positions?status=OPEN`).catch(() => []),
        api.get(`/clubs/${clubId}/fund/teams/${tid}/requests`).catch(() => []),
        api.get(`/clubs/${clubId}/fund/teams/${tid}/notes?noteType=DECISION`).catch(() => []),
        api.get(`/clubs/${clubId}/fund/teams/${tid}/notes?noteType=REPORT`).catch(() => []),
      ])
      setSettings(settingsData)
      setPositions(Array.isArray(posData) ? posData : [])
      setRequests(Array.isArray(reqData) ? reqData : [])
      setDecisionNotes(Array.isArray(decData) ? decData : [])
      setReportNotes(Array.isArray(repData) ? repData : [])
    } catch (e) {
      handleApiError(e, '대시보드 데이터 로딩')
    } finally {
      setLoading(false)
    }
  }, [clubId, tid])

  useEffect(() => { fetchAll() }, [fetchAll])

  // ── Real-time price fetch for OPEN positions ──
  const fetchPrices = useCallback(async () => {
    if (!clubId || positions.length === 0) return
    const openPositions = positions.filter(p => p.status === 'OPEN')
    if (openPositions.length === 0) return

    const results = {}
    await Promise.allSettled(
      openPositions.map(async (p) => {
        try {
          const data = await api.get(`/clubs/${clubId}/fund/prices/quote?ticker=${p.ticker}&market=${p.market}`)
          if (data?.price != null) {
            results[p.id] = data
          }
        } catch { /* ignore individual failures */ }
      })
    )
    setPriceMap(prev => ({ ...prev, ...results }))
  }, [clubId, positions])

  useEffect(() => {
    if (positions.length > 0) {
      fetchPrices()
      priceTimerRef.current = setInterval(fetchPrices, PRICE_REFRESH_INTERVAL_MS)
      return () => clearInterval(priceTimerRef.current)
    }
  }, [fetchPrices, positions.length])

  // ── Derived data ──
  const pendingRequests = requests.filter(r => r.status === 'PENDING')
  const recentRequests = requests.slice(0, 3)
  const initialKrw = settings?.initialCapitalKrw ?? 0
  const initialUsd = settings?.initialCapitalUsd ?? 0

  // KRX position total (includes CRYPTO for USD-denominated capital)
  const krwPositions = positions.filter(p => !['NASDAQ', 'NYSE', 'CRYPTO'].includes(p.market))
  const usdPositions = positions.filter(p => ['NASDAQ', 'NYSE', 'CRYPTO'].includes(p.market))

  const krwPositionValue = krwPositions.reduce((sum, p) => sum + (p.totalBuyAmount ?? 0), 0)
  const usdPositionValue = usdPositions.reduce((sum, p) => sum + (p.totalBuyAmount ?? 0), 0)

  // Evaluation amounts (using real-time prices)
  const krwEvaluation = krwPositions.reduce((sum, p) => {
    const price = priceMap[p.id]?.price
    if (price && p.totalQuantity) return sum + price * p.totalQuantity
    return sum + (p.totalBuyAmount ?? 0)
  }, 0)
  const usdEvaluation = usdPositions.reduce((sum, p) => {
    const price = priceMap[p.id]?.price
    if (price && p.totalQuantity) return sum + price * p.totalQuantity
    return sum + (p.totalBuyAmount ?? 0)
  }, 0)

  const krwCash = Math.max(0, initialKrw - krwPositionValue)
  const usdCash = Math.max(0, initialUsd - usdPositionValue)
  const krwTotalAssets = krwCash + krwEvaluation
  const usdTotalAssets = usdCash + usdEvaluation

  const activeNotes = noteTab === 'DECISION' ? decisionNotes : reportNotes
  const exchangeHistory = settings?.exchangeHistory ?? []

  // ── Loading skeleton ──
  if (loading) {
    return (
      <FundPageWrapper teamId={tid}>
        <div className="space-y-4 p-1">
          <div className="animate-pulse bg-slate-100 rounded-xl h-12" />
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            {[...Array(4)].map((_, i) => <div key={i} className="animate-pulse bg-slate-100 rounded-xl h-24" />)}
          </div>
          <div className="animate-pulse bg-slate-100 rounded-xl h-48" />
        </div>
      </FundPageWrapper>
    )
  }

  return (
    <FundPageWrapper teamId={tid}>
      <div className="space-y-6">
        {/* ── Header ── */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1">
            <h1 className="text-lg font-bold text-slate-800 fund-serif">대시보드</h1>
            <div className="ml-3 flex items-center rounded-lg bg-slate-100 p-0.5">
              {[
                { key: 'dashboard', label: '대시보드' },
                { key: 'teamInfo', label: '팀 정보' },
              ].map(tab => (
                <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                  className={`px-3 py-1 text-xs font-medium rounded-md transition-colors cursor-pointer ${
                    activeTab === tab.key ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'
                  }`}>
                  {tab.label}
                </button>
              ))}
            </div>
          </div>
          {canManage && activeTab === 'dashboard' && (
            <div className="flex items-center gap-2">
              <button onClick={() => setShowExchangeModal(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors cursor-pointer">
                <ArrowRightLeft size={14} />
                환전
              </button>
              <button onClick={() => setShowSettingsModal(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors cursor-pointer">
                <Settings size={14} />
                설정
              </button>
            </div>
          )}
        </div>

        {/* ── Dashboard Tab ── */}
        {activeTab === 'dashboard' && (
          <>
            {/* Action Required Banner (leader only) */}
            {canManage && (pendingRequests.length > 0) && (
              <div className="flex items-center gap-3 px-4 py-3 bg-amber-50 border border-amber-200 rounded-xl">
                <AlertCircle size={16} className="text-amber-600 shrink-0" />
                <p className="text-xs text-amber-800">
                  <span className="font-semibold">대기중 요청 {pendingRequests.length}건</span>이 있습니다.
                  <button onClick={() => navigate(`/club/fund/${tid}/requests`)}
                    className="ml-2 underline font-medium cursor-pointer hover:text-amber-900">
                    바로가기
                  </button>
                </p>
              </div>
            )}

            {/* Summary Cards */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
              <SummaryCard label="원화 자본금" value={`\u20A9${formatNumber(initialKrw)}`}
                sub={krwPositionValue > 0 && initialKrw > 0
                  ? `투자: \u20A9${formatNumber(krwPositionValue)} (${formatPercent(krwPositionValue / initialKrw)})`
                  : '초기 설정'} />
              <SummaryCard label="달러 자본금" value={`$${formatNumber(initialUsd, 2)}`}
                sub={usdPositionValue > 0 && initialUsd > 0
                  ? `투자: $${formatNumber(usdPositionValue, 2)} (${formatPercent(usdPositionValue / initialUsd)})`
                  : '초기 설정'} />
              <SummaryCard label="원화 평가가치"
                value={krwTotalAssets > 0 ? `\u20A9${formatNumber(Math.round(krwTotalAssets))}` : '-'}
                sub={initialKrw > 0 && krwTotalAssets !== initialKrw
                  ? <span className={krwTotalAssets > initialKrw ? 'text-rose-600' : 'text-blue-600'}>
                      {krwTotalAssets > initialKrw ? '+' : ''}{formatPercent((krwTotalAssets - initialKrw) / initialKrw)}
                    </span>
                  : `${krwPositions.length}종목`} />
              <SummaryCard label="달러 평가가치"
                value={usdTotalAssets > 0 ? `$${formatNumber(usdTotalAssets, 2)}` : '-'}
                sub={initialUsd > 0 && usdTotalAssets !== initialUsd
                  ? <span className={usdTotalAssets > initialUsd ? 'text-rose-600' : 'text-blue-600'}>
                      {usdTotalAssets > initialUsd ? '+' : ''}{formatPercent((usdTotalAssets - initialUsd) / initialUsd)}
                    </span>
                  : `${usdPositions.length}종목`} />
            </div>

            {/* 3-column: Positions / Requests / Documents */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

            {/* Open Positions with real-time prices */}
            <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
              <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
                <h2 className="text-sm font-bold text-slate-800 fund-serif">진행중 포지션</h2>
                <button onClick={() => navigate(`/club/fund/${tid}/positions`)}
                  className="flex items-center gap-0.5 text-xs text-slate-400 hover:text-slate-600 cursor-pointer">
                  전체보기 <ChevronRight size={12} />
                </button>
              </div>
              {positions.length === 0 ? (
                <p className="text-sm text-slate-400 py-10 text-center">진행중인 포지션이 없습니다</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b border-slate-100 text-slate-400 bg-slate-50/50">
                        <th className="text-left px-4 py-2 font-medium">종목</th>
                        <th className="text-right px-4 py-2 font-medium">평균단가</th>
                        <th className="text-right px-4 py-2 font-medium">현재가</th>
                        <th className="text-right px-4 py-2 font-medium">수량</th>
                        <th className="text-right px-4 py-2 font-medium">투자금액</th>
                        <th className="text-right px-4 py-2 font-medium">평가금액</th>
                        <th className="text-right px-4 py-2 font-medium">미실현</th>
                        <th className="text-right px-4 py-2 font-medium">수익률</th>
                      </tr>
                    </thead>
                    <tbody>
                      {positions.slice(0, 8).map(pos => {
                        const quote = priceMap[pos.id]
                        const currentPrice = quote?.price
                        const buyAmount = pos.totalBuyAmount ?? 0
                        const evalAmount = currentPrice && pos.totalQuantity ? currentPrice * pos.totalQuantity : null
                        const unrealized = evalAmount != null ? evalAmount - buyAmount : null
                        const unrealizedRate = buyAmount > 0 && unrealized != null ? (unrealized / buyAmount) * 100 : null
                        const changeRate = quote?.changeRate != null ? Number(quote.changeRate) * 100 : null

                        return (
                          <tr key={pos.id} onClick={() => navigate(`/club/fund/${tid}/positions/${pos.id}`)}
                            className="border-b border-slate-50 hover:bg-slate-50 transition-colors cursor-pointer">
                            <td className="px-4 py-2.5">
                              <div className="flex items-center gap-2">
                                <div>
                                  <div className="font-semibold text-slate-800">{pos.ticker}</div>
                                  <div className="text-xs text-slate-400">{pos.tickerName || ''}</div>
                                </div>
                                <MiniTargetProgressBar
                                  currentPrice={currentPrice}
                                  averagePrice={pos.averageBuyPrice}
                                  takeProfitTargets={pos.takeProfitTargets}
                                  stopLossTargets={pos.stopLossTargets}
                                  size="xs"
                                />
                              </div>
                            </td>
                            <td className="px-4 py-2.5 text-right tabular-nums text-slate-500">
                              {formatPrice(pos.averageBuyPrice, pos.market)}
                            </td>
                            <td className="px-4 py-2.5 text-right tabular-nums">
                              {currentPrice != null ? (
                                <div>
                                  <span className="text-slate-700">{formatPrice(currentPrice, pos.market)}</span>
                                  {changeRate != null && (
                                    <span className={`ml-1 text-xs ${changeRate > 0 ? 'text-rose-500' : changeRate < 0 ? 'text-blue-500' : 'text-slate-400'}`}>
                                      {changeRate > 0 ? '\u25B2' : changeRate < 0 ? '\u25BC' : ''}{Math.abs(changeRate).toFixed(1)}%
                                    </span>
                                  )}
                                </div>
                              ) : (
                                <span className="text-slate-300">-</span>
                              )}
                            </td>
                            <td className="px-4 py-2.5 text-right tabular-nums text-slate-500">
                              {formatQuantity(pos.totalQuantity)}
                            </td>
                            <td className="px-4 py-2.5 text-right tabular-nums text-slate-500">
                              {buyAmount ? formatCurrency(buyAmount, pos.market) : '-'}
                            </td>
                            <td className="px-4 py-2.5 text-right tabular-nums text-slate-500">
                              {evalAmount != null ? formatCurrency(evalAmount, pos.market) : '-'}
                            </td>
                            <td className={`px-4 py-2.5 text-right tabular-nums font-medium ${unrealized != null ? getProfitLossColor(unrealized) : 'text-slate-400'}`}>
                              {unrealized != null ? (
                                <div>
                                  <div>{unrealized >= 0 ? '+' : ''}{formatCurrency(unrealized, pos.market)}</div>
                                </div>
                              ) : '-'}
                            </td>
                            <td className={`px-4 py-2.5 text-right tabular-nums font-medium ${unrealizedRate != null ? getProfitLossColor(unrealizedRate) : 'text-slate-400'}`}>
                              {unrealizedRate != null ? formatProfitRate(unrealizedRate) : '-'}
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                  {positions.length > 8 && (
                    <div className="px-4 py-2 text-center">
                      <button onClick={() => navigate(`/club/fund/${tid}/positions`)}
                        className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer">
                        +{positions.length - 8}건 더보기
                      </button>
                    </div>
                  )}
                </div>
              )}
            </section>

            {/* Recent Requests */}
            <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
              <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
                <h2 className="text-sm font-bold text-slate-800 fund-serif">최근 매매요청</h2>
                <button onClick={() => navigate(`/club/fund/${tid}/requests`)}
                  className="flex items-center gap-0.5 text-xs text-slate-400 hover:text-slate-600 cursor-pointer">
                  전체보기 <ChevronRight size={12} />
                </button>
              </div>
              {recentRequests.length === 0 ? (
                <p className="text-sm text-slate-400 py-10 text-center">매매요청이 없습니다</p>
              ) : (
                <div className="divide-y divide-slate-50">
                  {recentRequests.map(req => {
                    const pill = REQUEST_STATUS_PILL[req.status] || REQUEST_STATUS_PILL.PENDING
                    return (
                      <div key={req.id} className="flex items-center justify-between px-5 py-3 hover:bg-slate-50 transition-colors">
                        <div className="flex items-center gap-3">
                          <span className={`px-2 py-0.5 rounded text-xs font-bold ${
                            req.requestType === 'BUY' ? 'bg-rose-50 text-rose-600' : 'bg-blue-50 text-blue-600'
                          }`}>
                            {REQUEST_TYPE_LABEL[req.requestType] || req.requestType}
                          </span>
                          <div>
                            <p className="text-xs font-medium text-slate-800">{req.targetTickerName || req.targetTicker}</p>
                            <p className="text-xs text-slate-400">
                              {req.requesterName} · {formatTime(req.createdAt)}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="text-xs tabular-nums text-slate-500">
                            {req.requestType === 'BUY'
                              ? (req.orderAmount != null ? `\u20A9${formatNumber(req.orderAmount)}` : '-')
                              : (req.sellQuantity != null ? `${formatQuantity(req.sellQuantity)}주` : '-')}
                          </span>
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${pill.bg} ${pill.text}`}>
                            {pill.label}
                          </span>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </section>

            {/* Reports Section — Decision notes & Report notes */}
            <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
              <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
                <div className="flex items-center gap-1">
                  <FileText size={14} className="text-slate-400" />
                  <div className="ml-2 flex items-center rounded-lg bg-slate-100 p-0.5">
                    {[
                      { key: 'DECISION', label: '의사결정서' },
                      { key: 'REPORT', label: '운용보고서' },
                    ].map(tab => (
                      <button key={tab.key} onClick={() => setNoteTab(tab.key)}
                        className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors cursor-pointer ${
                          noteTab === tab.key ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-400 hover:text-slate-600'
                        }`}>
                        {tab.label}
                      </button>
                    ))}
                  </div>
                </div>
                <button onClick={() => navigate(`/club/fund/${tid}/stats`)}
                  className="flex items-center gap-0.5 text-xs text-slate-400 hover:text-slate-600 cursor-pointer">
                  전체보기 <ChevronRight size={12} />
                </button>
              </div>
              {activeNotes.length === 0 ? (
                <p className="text-sm text-slate-400 py-10 text-center">
                  {noteTab === 'DECISION' ? '의사결정서' : '운용보고서'}가 없습니다
                </p>
              ) : (
                <div className="p-4 flex flex-wrap gap-4">
                  {activeNotes.map(note => (
                    <DocumentCard
                      key={note.id}
                      type={noteTab === 'DECISION' ? 'decision' : 'report'}
                      data={note}
                      onClick={() => openDocument(note, 'decision-note')}
                    />
                  ))}
                </div>
              )}
            </section>

            </div>{/* end 3-column grid */}

            {/* Exchange History */}
            {exchangeHistory.length > 0 && (
              <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                <div className="px-5 py-3 border-b border-slate-100">
                  <h2 className="text-sm font-bold text-slate-800">환전 이력</h2>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b border-slate-100 text-slate-400 bg-slate-50/50">
                        <th className="text-left px-4 py-2 font-medium">일시</th>
                        <th className="text-left px-4 py-2 font-medium">방향</th>
                        <th className="text-right px-4 py-2 font-medium">금액</th>
                        <th className="text-right px-4 py-2 font-medium">환율</th>
                        <th className="text-right px-4 py-2 font-medium">결과</th>
                        <th className="text-left px-4 py-2 font-medium">메모</th>
                      </tr>
                    </thead>
                    <tbody>
                      {exchangeHistory.map((ex, i) => (
                        <tr key={i} className="border-b border-slate-50">
                          <td className="px-4 py-2 text-slate-500">{formatTime(ex.createdAt)}</td>
                          <td className="px-4 py-2">
                            <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                              ex.direction === 'KRW_TO_USD' ? 'bg-blue-50 text-blue-600' : 'bg-emerald-50 text-emerald-600'
                            }`}>
                              {ex.direction === 'KRW_TO_USD' ? 'KRW\u2192USD' : 'USD\u2192KRW'}
                            </span>
                          </td>
                          <td className="px-4 py-2 text-right tabular-nums text-slate-600">
                            {ex.direction === 'KRW_TO_USD' ? `\u20A9${formatNumber(ex.fromAmount)}` : `$${formatNumber(ex.fromAmount, 2)}`}
                          </td>
                          <td className="px-4 py-2 text-right tabular-nums text-slate-500">{formatNumber(ex.exchangeRate, 2)}</td>
                          <td className="px-4 py-2 text-right tabular-nums text-slate-600">
                            {ex.direction === 'KRW_TO_USD' ? `$${formatNumber(ex.toAmount, 2)}` : `\u20A9${formatNumber(ex.toAmount)}`}
                          </td>
                          <td className="px-4 py-2 text-slate-400 max-w-[120px] truncate">{ex.memo || '-'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            )}
          </>
        )}

        {/* ── Team Info Tab ── */}
        {activeTab === 'teamInfo' && (
          <TeamInfoTab clubId={clubId} tid={tid} />
        )}
      </div>

      {/* ── Modals ── */}
      {showSettingsModal && (
        <SettingsModal
          clubId={clubId} tid={tid} settings={settings}
          onClose={() => setShowSettingsModal(false)}
          onSaved={() => { setShowSettingsModal(false); fetchAll() }}
        />
      )}
      {showExchangeModal && (
        <ExchangeModal
          clubId={clubId} tid={tid}
          onClose={() => setShowExchangeModal(false)}
          onSaved={() => { setShowExchangeModal(false); fetchAll() }}
        />
      )}
    </FundPageWrapper>
  )
}

// ── Summary Card ──
function SummaryCard({ label, value, sub }) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 px-4 py-3 shadow-sm hover:shadow-md transition-shadow">
      <p className="text-xs text-slate-400 font-medium mb-1">{label}</p>
      <p className="text-base font-bold text-slate-800 tabular-nums">{value}</p>
      {sub && <p className="text-xs text-slate-400 mt-0.5">{typeof sub === 'string' ? sub : sub}</p>}
    </div>
  )
}

// ── Team Info Tab ──
function TeamInfoTab({ clubId, tid }) {
  const [members, setMembers] = useState([])
  const [ranking, setRanking] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!clubId) return
    setLoading(true)
    Promise.all([
      api.get(`/clubs/${clubId}/fund/teams/${tid}/members`).catch(() => []),
      api.get(`/clubs/${clubId}/fund/teams/${tid}/stats/ranking`).catch(() => []),
    ])
      .then(([membersData, rankingData]) => {
        setMembers(Array.isArray(membersData) ? membersData : [])
        setRanking(Array.isArray(rankingData) ? rankingData : [])
      })
      .finally(() => setLoading(false))
  }, [clubId, tid])

  if (loading) {
    return (
      <div className="space-y-3">
        {[...Array(3)].map((_, i) => <div key={i} className="animate-pulse bg-slate-100 rounded-xl h-32" />)}
      </div>
    )
  }

  // Merge ranking stats into member data
  const rankMap = {}
  ranking.forEach(r => { rankMap[r.membershipId] = r })

  return (
    <div className="space-y-4">
      {/* Member Cards — lg:grid-cols-2 */}
      <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        <div className="px-5 py-3 border-b border-slate-100">
          <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2">
            <Users size={14} className="text-slate-400" />
            팀원 현황
          </h2>
        </div>
        {members.length === 0 ? (
          <p className="text-sm text-slate-400 py-10 text-center">팀원이 없습니다</p>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 p-4">
            {members.map(m => {
              const stats = rankMap[m.membershipId] || rankMap[m.id] || {}
              return <MemberCard key={m.userId ?? m.id} member={m} stats={stats} />
            })}
          </div>
        )}
      </section>

      {/* Leaderboard */}
      <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        <div className="px-5 py-3 border-b border-slate-100">
          <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2">
            <Trophy size={14} className="text-amber-500" />
            리더보드
          </h2>
        </div>
        {ranking.length === 0 ? (
          <p className="text-sm text-slate-400 py-10 text-center">아직 거래 데이터가 없습니다</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-slate-100 text-slate-400 bg-slate-50/50">
                  <th className="text-center px-3 py-2 font-medium w-12">순위</th>
                  <th className="text-left px-4 py-2 font-medium">팀원</th>
                  <th className="text-right px-4 py-2 font-medium">실현손익</th>
                  <th className="text-right px-4 py-2 font-medium">승률</th>
                  <th className="text-right px-4 py-2 font-medium">거래수</th>
                </tr>
              </thead>
              <tbody>
                {ranking
                  .sort((a, b) => (b.realizedPnl ?? 0) - (a.realizedPnl ?? 0))
                  .map((r, idx) => {
                    const rank = idx + 1
                    const medal = rank <= 3 ? MEDAL[rank] : ''
                    const pnlColor = getProfitLossColor(r.realizedPnl)
                    return (
                      <tr key={r.membershipId ?? idx} className="border-b border-slate-50 hover:bg-slate-50 transition-colors">
                        <td className="text-center px-3 py-2.5">
                          {medal ? (
                            <span className="text-base">{medal}</span>
                          ) : (
                            <span className="text-xs text-slate-500 font-semibold">{rank}</span>
                          )}
                        </td>
                        <td className="px-4 py-2.5">
                          <span className="text-xs font-medium text-slate-800">{r.memberName || '이름없음'}</span>
                        </td>
                        <td className={`px-4 py-2.5 text-right tabular-nums font-medium ${pnlColor}`}>
                          {r.realizedPnl != null ? `${r.realizedPnl >= 0 ? '+' : ''}\u20A9${formatNumber(r.realizedPnl)}` : '-'}
                        </td>
                        <td className="px-4 py-2.5 text-right tabular-nums text-slate-600">
                          {r.winRate != null ? `${(r.winRate * 100).toFixed(0)}%` : '-'}
                        </td>
                        <td className="px-4 py-2.5 text-right tabular-nums text-slate-500">
                          {r.totalTrades ?? '-'}
                        </td>
                      </tr>
                    )
                  })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}

// ── Member Card (with ranking stats) ──
function MemberCard({ member, stats }) {
  const m = member
  const roleBadge = m.teamRole === 'LEADER'
    ? { cls: 'bg-purple-100 text-purple-700', label: '팀장' }
    : m.teamRole === 'MANAGER'
    ? { cls: 'bg-blue-100 text-blue-700', label: '관리자' }
    : { cls: 'bg-slate-100 text-slate-600', label: '팀원' }

  const statItems = [
    { label: '총거래', value: stats.totalTrades ?? '-' },
    { label: '승률', value: stats.winRate != null ? `${(stats.winRate * 100).toFixed(0)}%` : '-' },
    { label: '실현손익', value: stats.realizedPnl != null ? `\u20A9${formatNumber(stats.realizedPnl)}` : '-',
      color: getProfitLossColor(stats.realizedPnl) },
    { label: '평균수익률', value: stats.avgProfitRate != null ? formatProfitRate(stats.avgProfitRate * 100) : '-',
      color: getProfitLossColor(stats.avgProfitRate) },
  ]

  return (
    <div className="border border-slate-200 shadow-sm rounded-lg p-3">
      <div className="flex items-center gap-2 mb-3">
        <div className="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center text-xs font-bold text-slate-500">
          {(m.userName || m.name || '?').charAt(0)}
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <p className="text-xs font-semibold text-slate-800">{m.userName || m.name || '이름없음'}</p>
            <span className={`px-1.5 py-0.5 rounded text-xs font-medium ${roleBadge.cls}`}>
              {roleBadge.label}
            </span>
          </div>
        </div>
      </div>
      <div className="grid grid-cols-4 gap-1 bg-slate-50 rounded-lg px-2 py-2">
        {statItems.map(item => (
          <div key={item.label} className="text-center">
            <p className="text-xs text-slate-400 mb-0.5">{item.label}</p>
            <p className={`text-xs font-semibold tabular-nums ${item.color || 'text-slate-700'}`}>
              {item.value}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}

// ── Settings Modal ──
function SettingsModal({ clubId, tid, settings, onClose, onSaved }) {
  const addToast = useToastStore(s => s.addToast)
  const [krw, setKrw] = useState(String(settings?.initialCapitalKrw ?? 0))
  const [usd, setUsd] = useState(String(settings?.initialCapitalUsd ?? 0))
  const [saving, setSaving] = useState(false)

  const handleSave = async () => {
    setSaving(true)
    try {
      await api.put(`/clubs/${clubId}/fund/teams/${tid}/settings`, {
        initialCapitalKrw: Number(krw) || 0,
        initialCapitalUsd: Number(usd) || 0,
      })
      addToast({ type: 'SUCCESS', title: '팀 설정이 저장되었습니다' })
      onSaved()
    } catch (e) {
      handleApiError(e, '팀 설정 저장')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 p-6">
        <div className="flex items-center justify-between mb-5">
          <h3 className="text-base font-bold text-slate-800">팀 설정</h3>
          <button onClick={onClose} className="p-1 text-slate-400 hover:text-slate-600 cursor-pointer">
            <X size={18} />
          </button>
        </div>

        <div className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">원화 자본금 (KRW)</label>
            <input type="number" value={krw} onChange={e => setKrw(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-300"
              placeholder="0" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">달러 자본금 (USD)</label>
            <input type="number" value={usd} onChange={e => setUsd(e.target.value)} step="0.01"
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-300"
              placeholder="0" />
          </div>
        </div>

        <div className="flex justify-end gap-2 mt-6">
          <button onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-slate-500 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors cursor-pointer">
            취소
          </button>
          <button onClick={handleSave} disabled={saving}
            className="px-4 py-2 text-xs font-medium text-white bg-slate-800 rounded-lg hover:bg-slate-700 disabled:opacity-50 transition-colors cursor-pointer">
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Exchange Modal ──
function ExchangeModal({ clubId, tid, onClose, onSaved }) {
  const addToast = useToastStore(s => s.addToast)
  const [direction, setDirection] = useState('KRW_TO_USD')
  const [amount, setAmount] = useState('')
  const [exchangeRate, setExchangeRate] = useState('1350')
  const [memo, setMemo] = useState('')
  const [saving, setSaving] = useState(false)

  const numAmount = Number(amount) || 0
  const numRate = Number(exchangeRate) || 1
  const converted = direction === 'KRW_TO_USD'
    ? (numRate > 0 ? numAmount / numRate : 0)
    : numAmount * numRate

  const handleExchange = async () => {
    if (numAmount <= 0) {
      addToast({ type: 'ERROR', title: '금액을 입력해주세요' })
      return
    }
    if (numRate <= 0) {
      addToast({ type: 'ERROR', title: '환율을 입력해주세요' })
      return
    }
    setSaving(true)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${tid}/settings/exchange`, {
        direction,
        fromAmount: numAmount,
        exchangeRate: numRate,
        memo: memo.trim() || null,
      })
      addToast({ type: 'SUCCESS', title: '환전이 완료되었습니다' })
      onSaved()
    } catch (e) {
      handleApiError(e, '환전')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 p-6">
        <div className="flex items-center justify-between mb-5">
          <h3 className="text-base font-bold text-slate-800">환전</h3>
          <button onClick={onClose} className="p-1 text-slate-400 hover:text-slate-600 cursor-pointer">
            <X size={18} />
          </button>
        </div>

        <div className="space-y-4">
          {/* Direction toggle */}
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-2">환전 방향</label>
            <div className="flex items-center rounded-lg bg-slate-100 p-0.5">
              {[
                { key: 'KRW_TO_USD', label: 'KRW \u2192 USD' },
                { key: 'USD_TO_KRW', label: 'USD \u2192 KRW' },
              ].map(opt => (
                <button key={opt.key} onClick={() => setDirection(opt.key)}
                  className={`flex-1 px-3 py-1.5 text-xs font-medium rounded-md transition-colors cursor-pointer ${
                    direction === opt.key ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500'
                  }`}>
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Amount */}
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              {direction === 'KRW_TO_USD' ? '원화 금액 (KRW)' : '달러 금액 (USD)'}
            </label>
            <input type="number" value={amount} onChange={e => setAmount(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-300"
              placeholder="0" step={direction === 'KRW_TO_USD' ? '1' : '0.01'} />
          </div>

          {/* Exchange Rate */}
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">환율 (1 USD = ? KRW)</label>
            <input type="number" value={exchangeRate} onChange={e => setExchangeRate(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-300"
              placeholder="1350" step="0.01" />
          </div>

          {/* Preview */}
          {numAmount > 0 && numRate > 0 && (
            <div className="flex items-center justify-center gap-3 px-4 py-3 bg-slate-50 rounded-lg">
              <span className="text-sm font-semibold text-slate-700 tabular-nums">
                {direction === 'KRW_TO_USD' ? `\u20A9${formatNumber(numAmount)}` : `$${formatNumber(numAmount, 2)}`}
              </span>
              <ArrowRight size={14} className="text-slate-400" />
              <span className="text-sm font-bold text-slate-800 tabular-nums">
                {direction === 'KRW_TO_USD' ? `$${formatNumber(converted, 2)}` : `\u20A9${formatNumber(Math.round(converted))}`}
              </span>
            </div>
          )}

          {/* Memo */}
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">메모 (선택)</label>
            <input type="text" value={memo} onChange={e => setMemo(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-300"
              placeholder="환전 사유 등" maxLength={200} />
          </div>
        </div>

        <div className="flex justify-end gap-2 mt-6">
          <button onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-slate-500 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors cursor-pointer">
            취소
          </button>
          <button onClick={handleExchange} disabled={saving}
            className="px-4 py-2 text-xs font-medium text-white bg-slate-800 rounded-lg hover:bg-slate-700 disabled:opacity-50 transition-colors cursor-pointer">
            {saving ? '처리 중...' : '환전하기'}
          </button>
        </div>
      </div>
    </div>
  )
}
