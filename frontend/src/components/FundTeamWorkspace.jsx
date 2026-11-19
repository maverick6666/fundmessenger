import { useState, useEffect, useMemo } from 'react'
import { TrendingUp, Plus, Check, X, MessageSquare, FileText, BarChart3, Activity, ChevronDown, ChevronUp, Edit3, Trash2, ToggleLeft, ToggleRight, Sparkles } from 'lucide-react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import StockPriceChart from './StockPriceChart'
import BenchmarkChart from './BenchmarkChart'
import { useUnsavedChanges, useUnsavedChangesGuard } from './UnsavedChangesProvider'
import useConfirm from '../hooks/useConfirm'

// ── 포맷 유틸 ──
function fmtKrw(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (Math.abs(n) >= 1e8) return (n / 1e8).toFixed(1) + '억'
  if (Math.abs(n) >= 1e4) return (n / 1e4).toFixed(0) + '만'
  return n.toLocaleString('ko-KR')
}
function fmtQty(v) { return v != null ? Number(v).toLocaleString('ko-KR', { maximumFractionDigits: 4 }) : '-' }
function fmtPct(v) { return v != null ? (Number(v) * 100).toFixed(2) + '%' : '-' }
function fmtPrice(v) { return v != null ? Number(v).toLocaleString('ko-KR', { maximumFractionDigits: 4 }) : '-' }
function fmtDate(d) { if (!d) return '-'; const dt = new Date(d); return `${dt.getMonth()+1}/${dt.getDate()}` }

const STATUS_BADGE = {
  PENDING: { bg: 'bg-amber-50', text: 'text-amber-700', border: 'border-amber-200', label: '대기중' },
  DISCUSSION: { bg: 'bg-blue-50', text: 'text-blue-700', border: 'border-blue-200', label: '토론중' },
  APPROVED: { bg: 'bg-emerald-50', text: 'text-emerald-700', border: 'border-emerald-200', label: '승인' },
  REJECTED: { bg: 'bg-rose-50', text: 'text-rose-700', border: 'border-rose-200', label: '거절' },
  EXECUTED: { bg: 'bg-slate-50', text: 'text-slate-600', border: 'border-slate-200', label: '체결' },
}

const TYPE_LABEL = { BUY: '매수', SELL: '매도', ADJUST: '조정' }
const TYPE_COLOR = { BUY: 'text-rose-600', SELL: 'text-blue-600', ADJUST: 'text-slate-600' }

export default function FundTeamWorkspace({ clubId, teamId, team, isTeamLeader, isTeamManager }) {
  const [activeTab, setActiveTab] = useState('portfolio')
  const canManage = isTeamLeader || isTeamManager
  const { confirmIfDirty } = useUnsavedChanges()

  const TABS = [
    { key: 'portfolio', label: '포트폴리오', icon: BarChart3 },
    { key: 'positions', label: '종목', icon: TrendingUp },
    { key: 'requests', label: '매매요청', icon: FileText },
    { key: 'documents', label: '문서', icon: FileText },
    { key: 'stats', label: '통계', icon: Activity },
  ]

  return (
    <div className="space-y-4">
      {/* 탭 바 */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1">
        {TABS.map(t => {
          const Icon = t.icon
          const active = activeTab === t.key
          return (
            <button
              key={t.key}
              onClick={() => confirmIfDirty(() => setActiveTab(t.key))}
              className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-md text-sm font-medium transition-all cursor-pointer
                ${active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}
            >
              <Icon size={15} />
              {t.label}
            </button>
          )
        })}
      </div>

      {/* 탭 콘텐츠 */}
      {activeTab === 'portfolio' && <PortfolioTab clubId={clubId} teamId={teamId} canManage={canManage} />}
      {activeTab === 'positions' && <PositionsTab clubId={clubId} teamId={teamId} canManage={canManage} />}
      {activeTab === 'requests' && <RequestsTab clubId={clubId} teamId={teamId} canManage={canManage} />}
      {activeTab === 'documents' && <DocumentsTab clubId={clubId} teamId={teamId} canManage={canManage} />}
      {activeTab === 'stats' && <StatsTab clubId={clubId} teamId={teamId} canManage={canManage} />}
    </div>
  )
}

// ═══════════════════════════════════════
// ① 포트폴리오 탭
// ═══════════════════════════════════════
function PortfolioTab({ clubId, teamId, canManage }) {
  const [positions, setPositions] = useState([])
  const [settings, setSettings] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!clubId || !teamId) return
    setLoading(true)
    Promise.all([
      api.get(`/clubs/${clubId}/fund/teams/${teamId}/positions?status=OPEN`),
      api.get(`/clubs/${clubId}/fund/teams/${teamId}/settings`),
    ])
      .then(([pos, set]) => { setPositions(pos); setSettings(set) })
      .catch(e => handleApiError(e, '포지션 조회'))
      .finally(() => setLoading(false))
  }, [clubId, teamId])

  const summary = useMemo(() => {
    const totalBuy = positions.reduce((s, p) => s + Number(p.totalBuyAmount || 0), 0)
    const totalRealized = positions.reduce((s, p) => s + Number(p.realizedProfitLoss || 0), 0)
    const openCount = positions.length
    return { totalBuy, totalRealized, openCount }
  }, [positions])

  if (loading) return <LoadingSpinner />

  const capitalKrw = Number(settings?.initialCapitalKrw || 0)

  return (
    <div className="space-y-4">
      {/* 요약 카드 3개 */}
      <div className="grid grid-cols-3 gap-3">
        <SummaryCard
          label="초기 자본금"
          value={fmtKrw(capitalKrw)}
          sub="KRW"
        />
        <SummaryCard
          label="투자금액"
          value={fmtKrw(summary.totalBuy)}
          sub={`${summary.openCount}개 포지션`}
        />
        <SummaryCard
          label="실현 손익"
          value={fmtKrw(summary.totalRealized)}
          sub={capitalKrw > 0 ? fmtPct(summary.totalRealized / capitalKrw) : '-'}
          positive={summary.totalRealized >= 0}
        />
      </div>

      {/* 진행중 포지션 리스트 */}
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-800">진행중 포지션</h3>
          <span className="text-xs text-slate-400">{positions.length}건</span>
        </div>
        {positions.length === 0 ? (
          <div className="py-10 text-center text-sm text-slate-400">진행중인 포지션이 없습니다</div>
        ) : (
          <div className="divide-y divide-slate-100">
            {positions.map(p => (
              <PositionRow key={p.id} p={p} />
            ))}
          </div>
        )}
      </div>

      {/* 팀 설정 (자본금) */}
      {canManage && settings && (
        <SettingsCard clubId={clubId} teamId={teamId} settings={settings} onUpdate={setSettings} />
      )}
    </div>
  )
}

function SummaryCard({ label, value, sub, positive }) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 px-4 py-3.5">
      <p className="text-xs font-medium text-slate-400 uppercase tracking-wide">{label}</p>
      <p className={`text-lg font-bold mt-1 ${positive === true ? 'text-emerald-600' : positive === false ? 'text-rose-600' : 'text-slate-900'}`}>
        {value}
      </p>
      {sub && <p className="text-xs text-slate-400 mt-0.5">{sub}</p>}
    </div>
  )
}

function PositionRow({ p }) {
  const pnl = Number(p.realizedProfitLoss || 0)
  const isProfit = pnl >= 0

  return (
    <div className="px-5 py-3 flex items-center gap-3 hover:bg-slate-50/50 transition-colors">
      {/* 종목 아이콘 */}
      <div className={`shrink-0 w-9 h-9 rounded-lg flex items-center justify-center text-xs font-bold text-white
        ${p.status === 'OPEN' ? 'bg-blue-500' : 'bg-slate-400'}`}>
        {p.ticker?.slice(0, 2)}
      </div>
      {/* 종목 정보 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-slate-800 truncate">{p.tickerName || p.ticker}</span>
          <span className="text-xs text-slate-400">{p.ticker}</span>
          {!p.infoConfirmed && (
            <span className="text-xs px-1.5 py-0.5 bg-amber-50 text-amber-600 border border-amber-200 rounded-full">미확인</span>
          )}
        </div>
        <p className="text-xs text-slate-400 mt-0.5">
          {fmtQty(p.totalQuantity)}주 · 평균 {fmtPrice(p.averageBuyPrice)}
        </p>
      </div>
      {/* 손익 */}
      <div className="text-right shrink-0">
        <p className={`text-sm font-semibold ${isProfit ? 'text-emerald-600' : 'text-rose-600'}`}>
          {isProfit ? '+' : ''}{fmtKrw(pnl)}
        </p>
        {p.statusInfo?.status === 'needs_close' && (
          <span className="text-xs text-rose-500">종료 필요</span>
        )}
        {p.statusInfo?.status === 'no_plan' && (
          <span className="text-xs text-amber-500">매도계획 없음</span>
        )}
      </div>
    </div>
  )
}

function SettingsCard({ clubId, teamId, settings, onUpdate }) {
  const { confirmIfDirty } = useUnsavedChanges()
  const [editing, setEditing] = useState(false)
  const [krw, setKrw] = useState('')
  const [usd, setUsd] = useState('')
  const [saving, setSaving] = useState(false)
  const isDirty = editing && !saving && (
    String(krw) !== String(settings.initialCapitalKrw || '0') ||
    String(usd) !== String(settings.initialCapitalUsd || '0')
  )

  useUnsavedChangesGuard(isDirty)

  const startEdit = () => {
    setKrw(settings.initialCapitalKrw || '0')
    setUsd(settings.initialCapitalUsd || '0')
    setEditing(true)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const updated = await api.put(`/clubs/${clubId}/fund/teams/${teamId}/settings`, {
        initialCapitalKrw: Number(krw),
        initialCapitalUsd: Number(usd),
      })
      onUpdate(updated)
      setEditing(false)
    } catch (e) { handleApiError(e, '팀 설정 저장') }
    finally { setSaving(false) }
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 px-5 py-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-slate-700">팀 설정</h3>
        {!editing && (
          <button onClick={startEdit} className="text-xs text-blue-500 hover:text-blue-600 cursor-pointer">편집</button>
        )}
      </div>
      {editing ? (
        <div className="space-y-2">
          <label className="block">
            <span className="text-xs text-slate-500">초기 자본금 (KRW)</span>
            <input type="number" value={krw} onChange={e => setKrw(e.target.value)}
              className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400" />
          </label>
          <label className="block">
            <span className="text-xs text-slate-500">초기 자본금 (USD)</span>
            <input type="number" value={usd} onChange={e => setUsd(e.target.value)}
              className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400" />
          </label>
          <div className="flex gap-2 pt-1">
            <button onClick={handleSave} disabled={saving}
              className="px-3 py-1.5 text-xs font-medium bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer">
              {saving ? '저장중...' : '저장'}
            </button>
            <button onClick={() => confirmIfDirty(() => setEditing(false))}
              className="px-3 py-1.5 text-xs text-slate-500 hover:text-slate-700 cursor-pointer">취소</button>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <span className="text-slate-400">KRW</span>
              <p className="font-semibold text-slate-800">{fmtKrw(settings.initialCapitalKrw)}</p>
            </div>
            <div>
              <span className="text-slate-400">USD</span>
              <p className="font-semibold text-slate-800">{settings.initialCapitalUsd ? `$${Number(settings.initialCapitalUsd).toLocaleString()}` : '-'}</p>
            </div>
          </div>
          {/* 수익률 공개 토글 */}
          <div className="flex items-center justify-between pt-2 border-t border-slate-100">
            <div>
              <p className="text-xs font-medium text-slate-700">수익률 공개</p>
              <p className="text-xs text-slate-400">게시판 펀드팀 뷰에서 수익률을 공개합니다</p>
            </div>
            <ProfitToggle clubId={clubId} teamId={teamId} initial={settings.profitPublic} onUpdate={onUpdate} settings={settings} />
          </div>
        </div>
      )}
    </div>
  )
}

function ProfitToggle({ clubId, teamId, initial, onUpdate, settings }) {
  const [on, setOn] = useState(initial || false)
  const [toggling, setToggling] = useState(false)

  const handleToggle = async () => {
    setToggling(true)
    try {
      const updated = await api.put(`/clubs/${clubId}/fund/teams/${teamId}/settings`, {
        profitPublic: !on
      })
      setOn(updated.profitPublic)
      onUpdate(updated)
    } catch (e) { handleApiError(e, '수익률 공개 설정') }
    finally { setToggling(false) }
  }

  return (
    <button
      onClick={handleToggle}
      disabled={toggling}
      className={`relative w-10 h-5 rounded-full transition-colors cursor-pointer disabled:opacity-50 ${on ? 'bg-emerald-500' : 'bg-slate-300'}`}
    >
      <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform ${on ? 'translate-x-5' : 'translate-x-0.5'}`} />
    </button>
  )
}

// ═══════════════════════════════════════
// ② 종목 탭
// ═══════════════════════════════════════
function PositionsTab({ clubId, teamId, canManage }) {
  const [positions, setPositions] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('OPEN')
  const [selected, setSelected] = useState(null)

  useEffect(() => {
    if (!clubId || !teamId) return
    setLoading(true)
    const params = filter ? `?status=${filter}` : ''
    api.get(`/clubs/${clubId}/fund/teams/${teamId}/positions${params}`)
      .then(setPositions)
      .catch(e => handleApiError(e, '포지션 목록 조회'))
      .finally(() => setLoading(false))
  }, [clubId, teamId, filter])

  const handlePositionUpdate = (updated) => {
    setPositions(prev => prev.map(p => p.id === updated.id ? updated : p))
  }

  if (loading) return <LoadingSpinner />

  return (
    <div className="space-y-3">
      {/* 필터 */}
      <div className="flex gap-2">
        {[['OPEN', '진행중'], ['CLOSED', '종료'], [null, '전체']].map(([val, label]) => (
          <button
            key={label}
            onClick={() => { setFilter(val); setSelected(null) }}
            className={`px-3 py-1.5 text-xs rounded-lg font-medium transition-colors cursor-pointer
              ${filter === val ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'}`}
          >
            {label}
          </button>
        ))}
      </div>

      {positions.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200 py-10 text-center text-sm text-slate-400">
          포지션이 없습니다
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-slate-200 divide-y divide-slate-100 overflow-hidden">
          {positions.map(p => (
            <div key={p.id} className="cursor-pointer" onClick={() => setSelected(selected === p.id ? null : p.id)}>
              <PositionRow p={p} />
              {selected === p.id && <PositionDetail p={p} clubId={clubId} teamId={teamId} canManage={canManage} onUpdate={handlePositionUpdate} />}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function PositionDetail({ p, clubId, teamId, canManage, onUpdate }) {
  const planProps = { positionId: p.id, clubId, teamId, canManage, onUpdate }
  return (
    <div className="px-5 py-4 bg-slate-50/70 border-t border-slate-100 space-y-3">
      {/* 종목 차트 */}
      {p.ticker && (
        <StockPriceChart
          clubId={clubId}
          apiUrl={`/clubs/${clubId}/fund/teams/${teamId}/positions/${p.id}/chart`}
        />
      )}

      {/* 매수 정보 */}
      <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-xs">
        <div><span className="text-slate-400">시장</span> <span className="ml-2 text-slate-700">{p.market}</span></div>
        <div><span className="text-slate-400">상태</span> <span className="ml-2 text-slate-700">{p.status === 'OPEN' ? '진행중' : '종료'}</span></div>
        <div><span className="text-slate-400">총 매수금액</span> <span className="ml-2 text-slate-700 font-medium">{fmtKrw(p.totalBuyAmount)}</span></div>
        <div><span className="text-slate-400">실현손익</span> <span className={`ml-2 font-medium ${Number(p.realizedProfitLoss || 0) >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>{fmtKrw(p.realizedProfitLoss)}</span></div>
        {p.holdingPeriodHours != null && (
          <div><span className="text-slate-400">보유기간</span> <span className="ml-2 text-slate-700">{Math.floor(p.holdingPeriodHours / 24)}일 {p.holdingPeriodHours % 24}시간</span></div>
        )}
        <div><span className="text-slate-400">개설일</span> <span className="ml-2 text-slate-700">{fmtDate(p.openedAt)}</span></div>
      </div>

      {/* 매매계획 요약 */}
      {p.buyPlan?.length > 0 && (
        <PlanSection title="매수 계획" items={p.buyPlan} type="buy" remaining={p.remainingBuys} {...planProps} />
      )}
      {p.takeProfitTargets?.length > 0 && (
        <PlanSection title="익절 목표" items={p.takeProfitTargets} type="tp" remaining={p.remainingTakeProfits} {...planProps} />
      )}
      {p.stopLossTargets?.length > 0 && (
        <PlanSection title="손절 목표" items={p.stopLossTargets} type="sl" remaining={p.remainingStopLosses} {...planProps} />
      )}
    </div>
  )
}

const PLAN_TYPE_MAP = { buy: 'BUY', tp: 'TAKE_PROFIT', sl: 'STOP_LOSS' }

function PlanSection({ title, items, type, remaining, positionId, clubId, teamId, canManage, onUpdate }) {
  const [toggling, setToggling] = useState(false)
  const colorMap = { buy: 'text-blue-600', tp: 'text-emerald-600', sl: 'text-rose-600' }

  const handleToggle = async (e, index) => {
    e.stopPropagation()
    if (!canManage || toggling) return
    setToggling(true)
    try {
      const updated = await api.post(`/clubs/${clubId}/fund/teams/${teamId}/positions/${positionId}/toggle-plan`, {
        planType: PLAN_TYPE_MAP[type], index
      })
      onUpdate?.(updated)
    } catch (err) { handleApiError(err, '매매계획 토글') }
    finally { setToggling(false) }
  }

  return (
    <div>
      <p className={`text-xs font-semibold ${colorMap[type]} mb-1`}>{title} ({remaining}건 남음)</p>
      <div className="flex flex-wrap gap-2">
        {items.map((item, i) => {
          const completed = item.completed
          return (
            <div
              key={i}
              onClick={(e) => handleToggle(e, i)}
              className={`text-xs px-2.5 py-1 rounded-md border inline-flex items-center gap-1 transition-colors select-none
                ${completed ? 'bg-slate-100 border-slate-200 text-slate-400 line-through' : 'bg-white border-slate-200 text-slate-700'}
                ${canManage ? 'cursor-pointer hover:border-blue-300' : ''}
                ${toggling ? 'opacity-50 pointer-events-none' : ''}`}
            >
              {completed && <Check size={10} className="text-emerald-500 shrink-0" />}
              {fmtPrice(item.price)} × {fmtQty(item.quantity)}
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ═══════════════════════════════════════
// ③ 매매요청 탭
// ═══════════════════════════════════════
function RequestsTab({ clubId, teamId, canManage }) {
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState(null)
  const [showForm, setShowForm] = useState(null) // 'buy' | 'sell' | null
  const [confirmModal, setConfirmModal] = useState(null) // { action, requestId, ... }
  const { confirm: showConfirm, modal: ucModal } = useConfirm()

  const fetchRequests = () => {
    if (!clubId || !teamId) return
    setLoading(true)
    const params = filter ? `?status=${filter}` : ''
    api.get(`/clubs/${clubId}/fund/teams/${teamId}/requests${params}`)
      .then(setRequests)
      .catch(e => handleApiError(e, '매매요청 조회'))
      .finally(() => setLoading(false))
  }

  useEffect(fetchRequests, [clubId, teamId, filter])

  const [error, setError] = useState(null)

  const handleApprove = async (requestId) => {
    const ok = await showConfirm('이 매매요청을 승인하시겠습니까?', { confirmLabel: '승인' })
    if (!ok) return
    try {
      setError(null)
      await api.post(`/clubs/${clubId}/fund/teams/${teamId}/requests/${requestId}/approve`, {})
      fetchRequests()
    } catch (e) { handleApiError(e, '매매요청 승인'); setError('승인 실패: ' + (e.message || '알 수 없는 오류')) }
  }

  const handleReject = async (requestId, reason) => {
    try {
      setError(null)
      await api.post(`/clubs/${clubId}/fund/teams/${teamId}/requests/${requestId}/reject`, { rejectionReason: reason })
      fetchRequests()
    } catch (e) { handleApiError(e, '매매요청 거절'); setError('거절 실패: ' + (e.message || '알 수 없는 오류')) }
  }

  const handleDiscuss = async (requestId) => {
    try {
      setError(null)
      await api.post(`/clubs/${clubId}/fund/teams/${teamId}/requests/${requestId}/discuss`, {})
      fetchRequests()
    } catch (e) { handleApiError(e, '토론 시작'); setError('토론 시작 실패: ' + (e.message || '알 수 없는 오류')) }
  }

  if (loading && requests.length === 0) return <LoadingSpinner />

  return (
    <div className="space-y-3">
      {/* 상단 액션 */}
      <div className="flex items-center justify-between">
        <div className="flex gap-2">
          {[[null, '전체'], ['PENDING', '대기중'], ['APPROVED', '승인'], ['REJECTED', '거절']].map(([val, label]) => (
            <button
              key={label}
              onClick={() => setFilter(val)}
              className={`px-3 py-1.5 text-xs rounded-lg font-medium transition-colors cursor-pointer
                ${filter === val ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'}`}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          <button onClick={() => setShowForm('buy')}
            className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-rose-500 text-white rounded-lg hover:bg-rose-600 cursor-pointer">
            <Plus size={13} /> 매수
          </button>
          <button onClick={() => setShowForm('sell')}
            className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-primary text-white rounded-lg hover:bg-primary-hover cursor-pointer">
            <Plus size={13} /> 매도
          </button>
        </div>
      </div>

      {/* 에러 배너 */}
      {error && (
        <div className="bg-rose-50 border border-rose-200 rounded-lg px-4 py-2.5 flex items-center justify-between">
          <span className="text-xs text-rose-600">{error}</span>
          <button onClick={() => setError(null)} className="text-rose-400 hover:text-rose-600 cursor-pointer"><X size={14} /></button>
        </div>
      )}

      {/* 매매요청 폼 */}
      {showForm === 'buy' && (
        <BuyRequestForm clubId={clubId} teamId={teamId} onClose={() => setShowForm(null)} onCreated={fetchRequests} />
      )}
      {showForm === 'sell' && (
        <SellRequestForm clubId={clubId} teamId={teamId} onClose={() => setShowForm(null)} onCreated={fetchRequests} />
      )}

      {/* 요청 리스트 */}
      {requests.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200 py-10 text-center text-sm text-slate-400">
          매매요청이 없습니다
        </div>
      ) : (
        <div className="space-y-2">
          {requests.map(r => (
            <RequestCard
              key={r.id}
              r={r}
              canManage={canManage}
              onApprove={() => handleApprove(r.id)}
              onReject={() => setConfirmModal({ action: 'reject', requestId: r.id })}
              onDiscuss={() => handleDiscuss(r.id)}
            />
          ))}
        </div>
      )}

      {/* 거절 사유 모달 */}
      {confirmModal?.action === 'reject' && (
        <RejectModal
          onConfirm={(reason) => { handleReject(confirmModal.requestId, reason); setConfirmModal(null) }}
          onClose={() => setConfirmModal(null)}
        />
      )}
      {ucModal}
    </div>
  )
}

function RequestCard({ r, canManage, onApprove, onReject, onDiscuss }) {
  const badge = STATUS_BADGE[r.status] || STATUS_BADGE.PENDING
  const isPending = r.status === 'PENDING' || r.status === 'DISCUSSION'

  return (
    <div className="bg-white rounded-2xl border border-slate-200 px-5 py-4 space-y-2">
      {/* 헤더 */}
      <div className="flex items-center gap-2">
        <span className={`text-xs font-bold ${TYPE_COLOR[r.requestType]}`}>{TYPE_LABEL[r.requestType]}</span>
        <span className="text-sm font-semibold text-slate-800">{r.targetTickerName || r.targetTicker}</span>
        <span className="text-xs text-slate-400">{r.targetTicker}</span>
        <div className="flex-1" />
        <span className={`text-xs px-2 py-0.5 rounded-full border font-medium ${badge.bg} ${badge.text} ${badge.border}`}>
          {badge.label}
        </span>
      </div>

      {/* 수량/가격 */}
      <div className="flex items-center gap-4 text-xs text-slate-500">
        {r.requestType === 'BUY' ? (
          <>
            <span>{fmtQty(r.orderQuantity)}주</span>
            <span>@{fmtPrice(r.buyPrice)}</span>
            {r.targetRatio && <span>비중 {fmtPct(r.targetRatio)}</span>}
          </>
        ) : (
          <>
            <span>{fmtQty(r.sellQuantity)}주</span>
            <span>@{fmtPrice(r.sellPrice)}</span>
          </>
        )}
        <span className="text-slate-300">|</span>
        <span>{r.requesterName}</span>
        <span>{fmtDate(r.createdAt)}</span>
      </div>

      {/* 메모 */}
      {r.memo && (
        <p className="text-xs text-slate-500 bg-slate-50 rounded-lg px-3 py-2 line-clamp-2">{r.memo}</p>
      )}

      {/* 거절 사유 */}
      {r.status === 'REJECTED' && r.rejectionReason && (
        <p className="text-xs text-rose-500 bg-rose-50 rounded-lg px-3 py-2">{r.rejectionReason}</p>
      )}

      {/* 액션 버튼 (매니저만, 대기/토론 상태만) */}
      {canManage && isPending && (
        <div className="flex gap-2 pt-1">
          <button onClick={onApprove}
            className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-emerald-500 text-white rounded-lg hover:bg-emerald-600 cursor-pointer">
            <Check size={12} /> 승인
          </button>
          <button onClick={onReject}
            className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-rose-500 text-white rounded-lg hover:bg-rose-600 cursor-pointer">
            <X size={12} /> 거절
          </button>
          {r.status === 'PENDING' && (
            <button onClick={onDiscuss}
              className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-primary text-white rounded-lg hover:bg-primary-hover cursor-pointer">
              <MessageSquare size={12} /> 토론
            </button>
          )}
        </div>
      )}
    </div>
  )
}

// ── 매수 요청 폼 ──
function BuyRequestForm({ clubId, teamId, onClose, onCreated }) {
  const { confirmIfDirty } = useUnsavedChanges()
  const [form, setForm] = useState({ targetTicker: '', targetMarket: 'KOSPI', targetTickerName: '', orderQuantity: '', buyPrice: '', memo: '' })
  const [submitting, setSubmitting] = useState(false)
  const isDirty = !submitting && (
    form.targetTicker !== '' ||
    form.targetMarket !== 'KOSPI' ||
    form.targetTickerName !== '' ||
    form.orderQuantity !== '' ||
    form.buyPrice !== '' ||
    form.memo !== ''
  )

  useUnsavedChangesGuard(isDirty)

  const MARKETS = ['KOSPI', 'KOSDAQ', 'NASDAQ', 'NYSE', 'CRYPTO']

  const handleSubmit = async () => {
    if (!form.targetTicker || !form.orderQuantity || !form.buyPrice || !form.memo) return
    setSubmitting(true)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${teamId}/requests/buy`, {
        ...form,
        orderQuantity: Number(form.orderQuantity),
        buyPrice: Number(form.buyPrice),
      })
      onCreated()
      onClose()
    } catch (e) { handleApiError(e, '매수 요청') }
    finally { setSubmitting(false) }
  }

  return (
    <div className="bg-white rounded-2xl border border-rose-200 px-5 py-4 space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-rose-700">매수 요청</h4>
        <button onClick={() => confirmIfDirty(onClose)} className="text-slate-400 hover:text-slate-600 cursor-pointer"><X size={16} /></button>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <FormField label="종목코드" value={form.targetTicker} onChange={v => setForm(p => ({ ...p, targetTicker: v }))} placeholder="005930" />
        <FormField label="종목명" value={form.targetTickerName} onChange={v => setForm(p => ({ ...p, targetTickerName: v }))} placeholder="삼성전자" />
        <div>
          <span className="text-xs text-slate-500">시장</span>
          <select value={form.targetMarket} onChange={e => setForm(p => ({ ...p, targetMarket: e.target.value }))}
            className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 bg-white">
            {MARKETS.map(m => <option key={m} value={m}>{m}</option>)}
          </select>
        </div>
        <FormField label="수량" value={form.orderQuantity} onChange={v => setForm(p => ({ ...p, orderQuantity: v }))} type="number" />
        <FormField label="매수가" value={form.buyPrice} onChange={v => setForm(p => ({ ...p, buyPrice: v }))} type="number" />
      </div>
      <div>
        <span className="text-xs text-slate-500">매매 사유 (필수)</span>
        <textarea value={form.memo} onChange={e => setForm(p => ({ ...p, memo: e.target.value }))}
          className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 h-20 resize-none"
          placeholder="왜 이 종목을 매수하고 싶은지 작성하세요" />
      </div>
      <button onClick={handleSubmit} disabled={submitting}
        className="w-full py-2 text-sm font-medium bg-rose-500 text-white rounded-lg hover:bg-rose-600 disabled:opacity-50 cursor-pointer">
        {submitting ? '제출중...' : '매수 요청 제출'}
      </button>
    </div>
  )
}

// ── 매도 요청 폼 ──
function SellRequestForm({ clubId, teamId, onClose, onCreated }) {
  const { confirmIfDirty } = useUnsavedChanges()
  const [positions, setPositions] = useState([])
  const [form, setForm] = useState({ positionId: '', sellQuantity: '', sellPrice: '', sellReason: '', memo: '' })
  const [submitting, setSubmitting] = useState(false)
  const isDirty = !submitting && (
    form.positionId !== '' ||
    form.sellQuantity !== '' ||
    form.sellPrice !== '' ||
    form.sellReason !== '' ||
    form.memo !== ''
  )

  useUnsavedChangesGuard(isDirty)

  useEffect(() => {
    api.get(`/clubs/${clubId}/fund/teams/${teamId}/positions?status=OPEN`)
      .then(setPositions)
      .catch(e => handleApiError(e, '포지션 조회'))
  }, [clubId, teamId])

  const handleSubmit = async () => {
    if (!form.positionId || !form.sellQuantity || !form.memo) return
    setSubmitting(true)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${teamId}/requests/sell`, {
        ...form,
        positionId: Number(form.positionId),
        sellQuantity: Number(form.sellQuantity),
        sellPrice: form.sellPrice ? Number(form.sellPrice) : null,
      })
      onCreated()
      onClose()
    } catch (e) { handleApiError(e, '매도 요청') }
    finally { setSubmitting(false) }
  }

  return (
    <div className="bg-white rounded-2xl border border-blue-200 px-5 py-4 space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-blue-700">매도 요청</h4>
        <button onClick={() => confirmIfDirty(onClose)} className="text-slate-400 hover:text-slate-600 cursor-pointer"><X size={16} /></button>
      </div>
      <div>
        <span className="text-xs text-slate-500">포지션 선택</span>
        <select value={form.positionId} onChange={e => setForm(p => ({ ...p, positionId: e.target.value }))}
          className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 bg-white">
          <option value="">선택하세요</option>
          {positions.map(pos => (
            <option key={pos.id} value={pos.id}>{pos.tickerName || pos.ticker} ({fmtQty(pos.totalQuantity)}주 보유)</option>
          ))}
        </select>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <FormField label="매도 수량" value={form.sellQuantity} onChange={v => setForm(p => ({ ...p, sellQuantity: v }))} type="number" />
        <FormField label="매도가 (선택)" value={form.sellPrice} onChange={v => setForm(p => ({ ...p, sellPrice: v }))} type="number" />
      </div>
      <div>
        <span className="text-xs text-slate-500">매도 사유</span>
        <textarea value={form.sellReason} onChange={e => setForm(p => ({ ...p, sellReason: e.target.value }))}
          className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 h-16 resize-none"
          placeholder="매도 이유" />
      </div>
      <div>
        <span className="text-xs text-slate-500">매매 사유 (필수)</span>
        <textarea value={form.memo} onChange={e => setForm(p => ({ ...p, memo: e.target.value }))}
          className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 h-16 resize-none"
          placeholder="왜 매도하고 싶은지 작성하세요" />
      </div>
      <button onClick={handleSubmit} disabled={submitting}
        className="w-full py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer">
        {submitting ? '제출중...' : '매도 요청 제출'}
      </button>
    </div>
  )
}

// ── 거절 모달 ──
function RejectModal({ onConfirm, onClose }) {
  const { confirmIfDirty } = useUnsavedChanges()
  const [reason, setReason] = useState('')
  useUnsavedChangesGuard(reason.trim() !== '')
  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/40" onClick={() => confirmIfDirty(onClose)}>
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm mx-4 p-5 space-y-3" onClick={e => e.stopPropagation()}>
        <h3 className="text-sm font-semibold text-slate-800">거절 사유</h3>
        <textarea value={reason} onChange={e => setReason(e.target.value)}
          className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg h-24 resize-none focus:outline-none focus:border-blue-400"
          placeholder="거절 사유를 입력하세요" autoFocus />
        <div className="flex justify-end gap-2">
          <button onClick={() => confirmIfDirty(onClose)} className="px-4 py-2 text-xs text-slate-500 hover:text-slate-700 cursor-pointer">취소</button>
          <button onClick={() => reason.trim() && onConfirm(reason.trim())} disabled={!reason.trim()}
            className="px-4 py-2 text-xs font-medium bg-rose-500 text-white rounded-lg hover:bg-rose-600 disabled:opacity-50 cursor-pointer">거절</button>
        </div>
      </div>
    </div>
  )
}

// ═══════════════════════════════════════
// ④ 문서 탭
// ═══════════════════════════════════════
const NOTE_TYPE_BADGE = {
  DECISION: { bg: 'bg-teal-50', text: 'text-teal-700', border: 'border-teal-200', label: '의사결정서' },
  REPORT: { bg: 'bg-purple-50', text: 'text-purple-700', border: 'border-purple-200', label: '운용보고서' },
}

function DocumentsTab({ clubId, teamId, canManage }) {
  const [notes, setNotes] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState(null) // null=전체, 'DECISION', 'REPORT'
  const [expandedId, setExpandedId] = useState(null)
  const { confirm: showConfirm, modal: ucModal } = useConfirm()
  const [showForm, setShowForm] = useState(false)
  const [editingNote, setEditingNote] = useState(null)
  const [error, setError] = useState(null)
  const [showAiDecision, setShowAiDecision] = useState(false)
  const [showReportWizard, setShowReportWizard] = useState(false)

  const fetchNotes = () => {
    if (!clubId || !teamId) return
    setLoading(true)
    const params = filter ? `?noteType=${filter}` : ''
    api.get(`/clubs/${clubId}/fund/teams/${teamId}/notes${params}`)
      .then(setNotes)
      .catch(e => { handleApiError(e, '팀 문서 조회'); setError(e.message) })
      .finally(() => setLoading(false))
  }

  useEffect(fetchNotes, [clubId, teamId, filter])

  const handleToggleExport = async (note) => {
    try {
      setError(null)
      const updated = await api.patch(`/clubs/${clubId}/fund/teams/${teamId}/notes/${note.id}/export`, {})
      setNotes(prev => prev.map(n => n.id === note.id ? { ...n, exportEnabled: updated.exportEnabled ?? !n.exportEnabled } : n))
    } catch (e) { handleApiError(e, '내보내기 토글'); setError('내보내기 토글 실패: ' + e.message) }
  }

  const handleDelete = async (noteId) => {
    const ok = await showConfirm('이 문서를 삭제하시겠습니까?', { confirmLabel: '삭제', danger: true })
    if (!ok) return
    try {
      setError(null)
      await api.delete(`/clubs/${clubId}/fund/teams/${teamId}/notes/${noteId}`)
      setNotes(prev => prev.filter(n => n.id !== noteId))
      if (expandedId === noteId) setExpandedId(null)
    } catch (e) { handleApiError(e, '문서 삭제'); setError('삭제 실패: ' + e.message) }
  }

  const startEdit = (note) => {
    setEditingNote(note)
    setShowForm(true)
  }

  if (loading && notes.length === 0) return <LoadingSpinner />

  return (
    <div className="space-y-3">
      {/* 상단: 필터 + 작성 버튼 */}
      <div className="flex items-center justify-between">
        <div className="flex gap-2">
          {[[null, '전체'], ['DECISION', '의사결정서'], ['REPORT', '운용보고서']].map(([val, label]) => (
            <button
              key={label}
              onClick={() => setFilter(val)}
              className={`px-3 py-1.5 text-xs rounded-lg font-medium transition-colors cursor-pointer
                ${filter === val ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'}`}
            >
              {label}
            </button>
          ))}
        </div>
        {canManage && (
          <div className="flex gap-2">
            <button onClick={() => setShowAiDecision(true)}
              className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-teal-500 text-white rounded-lg hover:bg-teal-600 cursor-pointer">
              <Sparkles size={13} /> AI 의사결정서
            </button>
            <button onClick={() => setShowReportWizard(true)}
              className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-purple-500 text-white rounded-lg hover:bg-purple-600 cursor-pointer">
              <Sparkles size={13} /> 운용보고서
            </button>
            <button onClick={() => { setEditingNote(null); setShowForm(true) }}
              className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-primary text-white rounded-lg hover:bg-primary-hover cursor-pointer">
              <Plus size={13} /> 문서 작성
            </button>
          </div>
        )}
      </div>

      {/* 에러 배너 */}
      {error && (
        <div className="bg-rose-50 border border-rose-200 rounded-lg px-4 py-2.5 flex items-center justify-between">
          <span className="text-xs text-rose-600">{error}</span>
          <button onClick={() => setError(null)} className="text-rose-400 hover:text-rose-600 cursor-pointer"><X size={14} /></button>
        </div>
      )}

      {/* AI 의사결정서 모달 */}
      {showAiDecision && (
        <AiDecisionModal clubId={clubId} teamId={teamId} onClose={() => setShowAiDecision(false)} onGenerated={fetchNotes} />
      )}

      {/* AI 운용보고서 위자드 */}
      {showReportWizard && (
        <ReportWizardModal clubId={clubId} teamId={teamId} onClose={() => setShowReportWizard(false)} onGenerated={fetchNotes} />
      )}

      {/* 문서 작성/수정 폼 */}
      {showForm && (
        <NoteForm
          clubId={clubId}
          teamId={teamId}
          editingNote={editingNote}
          onClose={() => { setShowForm(false); setEditingNote(null) }}
          onSaved={fetchNotes}
        />
      )}

      {/* 문서 리스트 */}
      {notes.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200 py-10 text-center text-sm text-slate-400">
          문서가 없습니다
        </div>
      ) : (
        <div className="space-y-2">
          {notes.map(note => {
            const badge = NOTE_TYPE_BADGE[note.noteType] || NOTE_TYPE_BADGE.DECISION
            const isExpanded = expandedId === note.id
            return (
              <div key={note.id} className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
                {/* 카드 헤더 */}
                <div
                  className="px-5 py-3.5 flex items-center gap-3 cursor-pointer hover:bg-slate-50/50 transition-colors"
                  onClick={() => setExpandedId(isExpanded ? null : note.id)}
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-slate-800 truncate">{note.title}</span>
                      <span className={`text-xs px-2 py-0.5 rounded-full border font-medium ${badge.bg} ${badge.text} ${badge.border}`}>
                        {badge.label}
                      </span>
                      {note.aiGenerated && note.sourceChatRoomId && (
                        <span className="text-xs px-1.5 py-0.5 bg-sky-50 text-sky-600 border border-sky-200 rounded-full">AI 토론방</span>
                      )}
                      {note.exportEnabled && (
                        <span className="text-xs px-1.5 py-0.5 bg-emerald-50 text-emerald-600 border border-emerald-200 rounded-full">내보내기</span>
                      )}
                    </div>
                    <div className="flex items-center gap-3 mt-1 text-xs text-slate-400">
                      <span>{note.authorName || '작성자'}</span>
                      <span>{fmtDate(note.createdAt)}</span>
                      {note.positionTickerName && (
                        <span className="text-blue-500">#{note.positionTickerName}</span>
                      )}
                    </div>
                  </div>
                  {isExpanded ? <ChevronUp size={16} className="text-slate-400 shrink-0" /> : <ChevronDown size={16} className="text-slate-400 shrink-0" />}
                </div>

                {/* 펼침 본문 */}
                {isExpanded && (
                  <div className="px-5 py-4 border-t border-slate-100 space-y-3">
                    <div className="text-sm text-slate-700 whitespace-pre-wrap leading-relaxed">{note.content}</div>
                    {/* 액션 버튼 */}
                    <div className="flex items-center gap-2 pt-1">
                      {canManage && (
                        <button onClick={(e) => { e.stopPropagation(); handleToggleExport(note) }}
                          className="flex items-center gap-1 text-xs text-slate-500 hover:text-slate-700 cursor-pointer">
                          {note.exportEnabled ? <ToggleRight size={16} className="text-emerald-500" /> : <ToggleLeft size={16} />}
                          <span>{note.exportEnabled ? '내보내기 ON' : '내보내기 OFF'}</span>
                        </button>
                      )}
                      {(canManage || note.isAuthor) && (
                        <>
                          <button onClick={(e) => { e.stopPropagation(); startEdit(note) }}
                            className="flex items-center gap-1 text-xs text-blue-500 hover:text-blue-600 cursor-pointer">
                            <Edit3 size={13} /> 수정
                          </button>
                          <button onClick={(e) => { e.stopPropagation(); handleDelete(note.id) }}
                            className="flex items-center gap-1 text-xs text-rose-500 hover:text-rose-600 cursor-pointer">
                            <Trash2 size={13} /> 삭제
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function NoteForm({ clubId, teamId, editingNote, onClose, onSaved }) {
  const { confirmIfDirty } = useUnsavedChanges()
  const [form, setForm] = useState({
    title: editingNote?.title || '',
    content: editingNote?.content || '',
    noteType: editingNote?.noteType || 'DECISION',
    positionId: editingNote?.positionId || '',
  })
  const [positions, setPositions] = useState([])
  const [submitting, setSubmitting] = useState(false)
  const initialForm = {
    title: editingNote?.title || '',
    content: editingNote?.content || '',
    noteType: editingNote?.noteType || 'DECISION',
    positionId: String(editingNote?.positionId || ''),
  }
  const isDirty = !submitting && (
    form.title !== initialForm.title ||
    form.content !== initialForm.content ||
    form.noteType !== initialForm.noteType ||
    String(form.positionId || '') !== initialForm.positionId
  )

  useUnsavedChangesGuard(isDirty)

  useEffect(() => {
    api.get(`/clubs/${clubId}/fund/teams/${teamId}/positions?status=OPEN`)
      .then(setPositions)
      .catch(e => handleApiError(e, '포지션 조회'))
  }, [clubId, teamId])

  const handleSubmit = async () => {
    if (!form.title.trim() || !form.content.trim()) return
    setSubmitting(true)
    try {
      const payload = {
        title: form.title,
        content: form.content,
        noteType: form.noteType,
        positionId: form.positionId ? Number(form.positionId) : null,
      }
      if (editingNote) {
        await api.patch(`/clubs/${clubId}/fund/teams/${teamId}/notes/${editingNote.id}`, payload)
      } else {
        await api.post(`/clubs/${clubId}/fund/teams/${teamId}/notes`, payload)
      }
      onSaved()
      onClose()
    } catch (e) { handleApiError(e, '문서 저장') }
    finally { setSubmitting(false) }
  }

  return (
    <div className="bg-white rounded-2xl border border-blue-200 px-5 py-4 space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-blue-700">{editingNote ? '문서 수정' : '문서 작성'}</h4>
        <button onClick={() => confirmIfDirty(onClose)} className="text-slate-400 hover:text-slate-600 cursor-pointer"><X size={16} /></button>
      </div>
      <FormField label="제목" value={form.title} onChange={v => setForm(p => ({ ...p, title: v }))} placeholder="문서 제목" />
      <div className="grid grid-cols-2 gap-3">
        <div>
          <span className="text-xs text-slate-500">문서 유형</span>
          <select value={form.noteType} onChange={e => setForm(p => ({ ...p, noteType: e.target.value }))}
            className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 bg-white">
            <option value="DECISION">의사결정서</option>
            <option value="REPORT">운용보고서</option>
          </select>
        </div>
        <div>
          <span className="text-xs text-slate-500">관련 포지션 (선택)</span>
          <select value={form.positionId} onChange={e => setForm(p => ({ ...p, positionId: e.target.value }))}
            className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 bg-white">
            <option value="">없음</option>
            {positions.map(pos => (
              <option key={pos.id} value={pos.id}>{pos.tickerName || pos.ticker}</option>
            ))}
          </select>
        </div>
      </div>
      <div>
        <span className="text-xs text-slate-500">본문</span>
        <textarea value={form.content} onChange={e => setForm(p => ({ ...p, content: e.target.value }))}
          className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 h-32 resize-none"
          placeholder="문서 내용을 작성하세요" />
      </div>
      <button onClick={handleSubmit} disabled={submitting || !form.title.trim() || !form.content.trim()}
        className="w-full py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer">
        {submitting ? '저장중...' : editingNote ? '수정 완료' : '문서 저장'}
      </button>
    </div>
  )
}

// ── AI 의사결정서 생성 모달 ──
function AiDecisionModal({ clubId, teamId, onClose, onGenerated }) {
  const { confirmIfDirty } = useUnsavedChanges()
  const [rooms, setRooms] = useState([])
  const [selectedRoomId, setSelectedRoomId] = useState('')
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [result, setResult] = useState(null)
  useUnsavedChangesGuard(!result && !generating && selectedRoomId !== '')

  useEffect(() => {
    api.get(`/clubs/${clubId}/chat/rooms`)
      .then(data => {
        const discussions = (data || []).filter(r => r.type === 'DISCUSSION' && r.teamId == teamId)
        setRooms(discussions)
      })
      .catch(e => handleApiError(e, '토론방 조회'))
      .finally(() => setLoading(false))
  }, [clubId, teamId])

  const handleGenerate = async () => {
    if (!selectedRoomId || generating) return
    setGenerating(true)
    try {
      const res = await api.post(`/clubs/${clubId}/fund/teams/${teamId}/ai/decision-note`, {
        chatRoomId: Number(selectedRoomId)
      })
      setResult(res)
      onGenerated()
    } catch (e) { handleApiError(e, 'AI 의사결정서 생성') }
    finally { setGenerating(false) }
  }

  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/40" onClick={() => confirmIfDirty(onClose)}>
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm mx-4 p-5 space-y-4" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-800">AI 의사결정서 생성</h3>
          <button onClick={() => confirmIfDirty(onClose)} className="text-slate-400 hover:text-slate-600 cursor-pointer"><X size={16} /></button>
        </div>

        {result ? (
          <div className="space-y-3">
            <div className="bg-emerald-50 border border-emerald-200 rounded-lg px-4 py-3 text-sm text-emerald-700">
              {result.notes?.length || 1}건 의사결정서 생성 완료
            </div>
          <button onClick={() => confirmIfDirty(onClose)}
              className="w-full py-2 text-sm font-medium bg-slate-800 text-white rounded-lg hover:bg-slate-900 cursor-pointer">
              닫기
            </button>
          </div>
        ) : loading ? (
          <div className="py-6 flex justify-center"><div className="animate-spin w-5 h-5 border-2 border-slate-300 border-t-slate-600 rounded-full" /></div>
        ) : rooms.length === 0 ? (
          <div className="space-y-3">
            <p className="text-sm text-slate-500 text-center py-4">토론 중인 매매요청이 없습니다.<br />매매요청에서 "토론" 버튼을 눌러 토론방을 먼저 생성하세요.</p>
            <button onClick={onClose}
              className="w-full py-2 text-sm font-medium bg-slate-100 text-slate-600 rounded-lg hover:bg-slate-200 cursor-pointer">
              닫기
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            <div>
              <span className="text-xs text-slate-500">토론방 선택</span>
              <select value={selectedRoomId} onChange={e => setSelectedRoomId(e.target.value)}
                className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-teal-400 bg-white">
                <option value="">선택하세요</option>
                {rooms.map(r => (
                  <option key={r.id} value={r.id}>{r.name || `토론방 #${r.id}`}</option>
                ))}
              </select>
            </div>
            <p className="text-xs text-slate-400">선택한 토론방의 대화 내용을 AI가 분석하여 의사결정서를 생성합니다.</p>
            <button onClick={handleGenerate} disabled={!selectedRoomId || generating}
              className="w-full py-2 text-sm font-medium bg-teal-500 text-white rounded-lg hover:bg-teal-600 disabled:opacity-50 cursor-pointer">
              {generating ? 'AI가 분석 중...' : '생성'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

// ── AI 운용보고서 위자드 모달 ──
const REPORT_ITEMS = [
  { key: 'ASSET_CHANGE', label: '자산 변동 추이' },
  { key: 'TRADE_HISTORY', label: '매매 기록' },
  { key: 'NEW_CLOSED_POSITIONS', label: '신규/종료 포지션' },
  { key: 'PORTFOLIO_WEIGHT', label: '포트폴리오 비중 변화' },
  { key: 'BENCHMARK_RETURN', label: '벤치마크 대비 수익률' },
  { key: 'NEWS_SUMMARY', label: '뉴스 요약' },
  { key: 'EXCHANGE_RATE', label: '환율 변동' },
]

function ReportWizardModal({ clubId, teamId, onClose, onGenerated }) {
  const { confirmIfDirty } = useUnsavedChanges()
  const [periodDays, setPeriodDays] = useState(7)
  const [selectedItems, setSelectedItems] = useState(() => new Set(REPORT_ITEMS.map(i => i.key)))
  const [generating, setGenerating] = useState(false)
  const [result, setResult] = useState(null)
  useUnsavedChangesGuard(
    !result && !generating && (
      periodDays !== 7 ||
      selectedItems.size !== REPORT_ITEMS.length
    )
  )

  const toggleItem = (key) => {
    setSelectedItems(prev => {
      const next = new Set(prev)
      next.has(key) ? next.delete(key) : next.add(key)
      return next
    })
  }

  const toggleAll = () => {
    setSelectedItems(prev => prev.size === REPORT_ITEMS.length ? new Set() : new Set(REPORT_ITEMS.map(i => i.key)))
  }

  const handleGenerate = async () => {
    if (selectedItems.size === 0 || generating) return
    setGenerating(true)
    try {
      const res = await api.post(`/clubs/${clubId}/fund/teams/${teamId}/ai/operation-report`, {
        selectedItems: [...selectedItems],
        periodDays
      })
      setResult(res)
      onGenerated()
    } catch (e) { handleApiError(e, '운용보고서 생성') }
    finally { setGenerating(false) }
  }

  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/40" onClick={() => confirmIfDirty(onClose)}>
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 p-5 space-y-4" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-800">운용보고서 작성</h3>
          <button onClick={() => confirmIfDirty(onClose)} className="text-slate-400 hover:text-slate-600 cursor-pointer"><X size={16} /></button>
        </div>

        {result ? (
          <div className="space-y-3">
            <div className="bg-purple-50 border border-purple-200 rounded-lg px-4 py-3 text-sm text-purple-700">
              운용보고서 생성 완료
            </div>
            <button onClick={onClose}
              className="w-full py-2 text-sm font-medium bg-slate-800 text-white rounded-lg hover:bg-slate-900 cursor-pointer">
              닫기
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {/* 기간 선택 */}
            <div>
              <span className="text-xs font-medium text-slate-500">분석 기간</span>
              <div className="flex gap-2 mt-1.5">
                {[7, 14, 30].map(d => (
                  <button key={d} onClick={() => setPeriodDays(d)}
                    className={`px-3 py-1.5 text-xs rounded-lg font-medium transition-colors cursor-pointer
                      ${periodDays === d ? 'bg-purple-600 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'}`}>
                    {d}일
                  </button>
                ))}
                <input type="number" value={periodDays} onChange={e => setPeriodDays(Math.max(1, Number(e.target.value) || 7))} min={1}
                  className="w-16 px-2 py-1.5 text-xs border border-slate-200 rounded-lg text-center focus:outline-none focus:border-purple-400" />
              </div>
            </div>

            {/* 데이터 항목 체크박스 */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <span className="text-xs font-medium text-slate-500">포함 데이터</span>
                <button onClick={toggleAll} className="text-xs text-purple-500 hover:text-purple-600 cursor-pointer">
                  {selectedItems.size === REPORT_ITEMS.length ? '전체 해제' : '전체 선택'}
                </button>
              </div>
              <div className="space-y-1.5">
                {REPORT_ITEMS.map(item => (
                  <label key={item.key} className="flex items-center gap-2.5 py-1 cursor-pointer select-none">
                    <input type="checkbox" checked={selectedItems.has(item.key)} onChange={() => toggleItem(item.key)}
                      className="w-3.5 h-3.5 rounded border-slate-300 text-purple-500 focus:ring-purple-400 cursor-pointer" />
                    <span className="text-xs text-slate-700">{item.label}</span>
                  </label>
                ))}
              </div>
            </div>

            <p className="text-xs text-slate-400">선택한 항목의 데이터를 AI가 분석하여 종합 운용보고서를 생성합니다.</p>

            <button onClick={handleGenerate} disabled={selectedItems.size === 0 || generating}
              className="w-full py-2 text-sm font-medium bg-purple-500 text-white rounded-lg hover:bg-purple-600 disabled:opacity-50 cursor-pointer">
              {generating ? 'AI가 보고서 작성 중...' : `보고서 생성 (${selectedItems.size}개 항목)`}
            </button>
          </div>
        )}
      </div>
      {ucModal}
    </div>
  )
}

// ═══════════════════════════════════════
// ⑤ 통계 탭
// ═══════════════════════════════════════
function StatsTab({ clubId, teamId, canManage }) {
  const [snapshots, setSnapshots] = useState([])
  const [cashAdj, setCashAdj] = useState([])
  const [loading, setLoading] = useState(true)
  const [showCashForm, setShowCashForm] = useState(false)
  const [error, setError] = useState(null)

  const fetchData = () => {
    if (!clubId || !teamId) return
    setLoading(true)
    Promise.all([
      api.get(`/clubs/${clubId}/fund/teams/${teamId}/stats/snapshots`),
      api.get(`/clubs/${clubId}/fund/teams/${teamId}/stats/cash-adjustments`),
    ])
      .then(([snaps, cash]) => { setSnapshots(snaps); setCashAdj(cash) })
      .catch(e => { handleApiError(e, '팀 통계 조회'); setError(e.message) })
      .finally(() => setLoading(false))
  }

  useEffect(fetchData, [clubId, teamId])

  if (loading) return <LoadingSpinner />

  return (
    <div className="space-y-4">
      {/* 에러 배너 */}
      {error && (
        <div className="bg-rose-50 border border-rose-200 rounded-lg px-4 py-2.5 flex items-center justify-between">
          <span className="text-xs text-rose-600">{error}</span>
          <button onClick={() => setError(null)} className="text-rose-400 hover:text-rose-600 cursor-pointer"><X size={14} /></button>
        </div>
      )}

      {/* 벤치마크 비교 차트 */}
      <BenchmarkChart clubId={clubId} teamId={teamId} />

      {/* 자산 스냅샷 테이블 */}
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-800">자산 스냅샷</h3>
          <span className="text-xs text-slate-400">{snapshots.length}건</span>
        </div>
        {snapshots.length === 0 ? (
          <div className="py-10 text-center text-sm text-slate-400">스냅샷이 없습니다</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-slate-50 text-slate-500 border-b border-slate-100">
                  <th className="px-4 py-2.5 text-left font-medium">날짜</th>
                  <th className="px-4 py-2.5 text-right font-medium">KRW 현금</th>
                  <th className="px-4 py-2.5 text-right font-medium">KRW 평가</th>
                  <th className="px-4 py-2.5 text-right font-medium">USD 현금</th>
                  <th className="px-4 py-2.5 text-right font-medium">총자산</th>
                  <th className="px-4 py-2.5 text-right font-medium">실현손익</th>
                  <th className="px-4 py-2.5 text-right font-medium">미실현손익</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {snapshots.map((s, i) => (
                  <tr key={i} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-4 py-2.5 text-slate-700">{fmtDate(s.snapshotDate || s.createdAt)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmtKrw(s.krwCash)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmtKrw(s.krwEvaluation)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{s.usdCash != null ? `$${Number(s.usdCash).toLocaleString()}` : '-'}</td>
                    <td className="px-4 py-2.5 text-right font-semibold text-slate-800">{fmtKrw(s.totalKrw)}</td>
                    <td className={`px-4 py-2.5 text-right font-medium ${Number(s.realizedPnl || 0) >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {fmtKrw(s.realizedPnl)}
                    </td>
                    <td className={`px-4 py-2.5 text-right font-medium ${Number(s.unrealizedPnl || 0) >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {fmtKrw(s.unrealizedPnl)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 입출금 이력 */}
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-800">입출금 이력</h3>
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-400">{cashAdj.length}건</span>
            {canManage && (
              <button onClick={() => setShowCashForm(true)}
                className="flex items-center gap-1 px-2.5 py-1 text-xs font-medium bg-primary text-white rounded-lg hover:bg-primary-hover cursor-pointer">
                <Plus size={12} /> 입출금 기록
              </button>
            )}
          </div>
        </div>

        {showCashForm && (
          <CashAdjustmentForm
            clubId={clubId}
            teamId={teamId}
            onClose={() => setShowCashForm(false)}
            onSaved={fetchData}
          />
        )}

        {cashAdj.length === 0 ? (
          <div className="py-10 text-center text-sm text-slate-400">입출금 기록이 없습니다</div>
        ) : (
          <div className="divide-y divide-slate-100">
            {cashAdj.map((c, i) => (
              <div key={i} className="px-5 py-3 flex items-center gap-3 hover:bg-slate-50/50 transition-colors">
                <div className={`shrink-0 w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold text-white
                  ${c.adjustmentType === 'DEPOSIT' ? 'bg-emerald-500' : 'bg-rose-500'}`}>
                  {c.adjustmentType === 'DEPOSIT' ? '입' : '출'}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className={`text-sm font-semibold ${c.adjustmentType === 'DEPOSIT' ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {c.adjustmentType === 'DEPOSIT' ? '+' : '-'}{c.currency === 'USD' ? `$${Number(c.amount).toLocaleString()}` : fmtKrw(c.amount)}
                    </span>
                    <span className="text-xs text-slate-400">{c.currency}</span>
                  </div>
                  {c.memo && <p className="text-xs text-slate-400 mt-0.5 truncate">{c.memo}</p>}
                </div>
                <span className="text-xs text-slate-400 shrink-0">{fmtDate(c.createdAt)}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function CashAdjustmentForm({ clubId, teamId, onClose, onSaved }) {
  const { confirmIfDirty } = useUnsavedChanges()
  const [form, setForm] = useState({ adjustmentType: 'DEPOSIT', currency: 'KRW', amount: '', memo: '' })
  const [submitting, setSubmitting] = useState(false)
  const isDirty = !submitting && (
    form.adjustmentType !== 'DEPOSIT' ||
    form.currency !== 'KRW' ||
    form.amount !== '' ||
    form.memo !== ''
  )

  useUnsavedChangesGuard(isDirty)

  const handleSubmit = async () => {
    if (!form.amount) return
    setSubmitting(true)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${teamId}/stats/cash-adjustments`, {
        ...form,
        amount: Number(form.amount),
      })
      onSaved()
      onClose()
    } catch (e) { handleApiError(e, '입출금 기록 저장') }
    finally { setSubmitting(false) }
  }

  return (
    <div className="px-5 py-4 border-b border-slate-100 bg-slate-50/50 space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold text-slate-700">입출금 기록</h4>
        <button onClick={() => confirmIfDirty(onClose)} className="text-slate-400 hover:text-slate-600 cursor-pointer"><X size={16} /></button>
      </div>
      <div className="grid grid-cols-3 gap-3">
        <div>
          <span className="text-xs text-slate-500">유형</span>
          <select value={form.adjustmentType} onChange={e => setForm(p => ({ ...p, adjustmentType: e.target.value }))}
            className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 bg-white">
            <option value="DEPOSIT">입금</option>
            <option value="WITHDRAWAL">출금</option>
          </select>
        </div>
        <div>
          <span className="text-xs text-slate-500">통화</span>
          <select value={form.currency} onChange={e => setForm(p => ({ ...p, currency: e.target.value }))}
            className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400 bg-white">
            <option value="KRW">KRW</option>
            <option value="USD">USD</option>
          </select>
        </div>
        <FormField label="금액" value={form.amount} onChange={v => setForm(p => ({ ...p, amount: v }))} type="number" placeholder="0" />
      </div>
      <div>
        <span className="text-xs text-slate-500">메모 (선택)</span>
        <input type="text" value={form.memo} onChange={e => setForm(p => ({ ...p, memo: e.target.value }))}
          className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400"
          placeholder="입출금 메모" />
      </div>
      <button onClick={handleSubmit} disabled={submitting || !form.amount}
        className="w-full py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 cursor-pointer">
        {submitting ? '저장중...' : '기록 저장'}
      </button>
    </div>
  )
}

// ── 공통 ──
function FormField({ label, value, onChange, type = 'text', placeholder }) {
  return (
    <label className="block">
      <span className="text-xs text-slate-500">{label}</span>
      <input type={type} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder}
        className="w-full mt-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:border-blue-400" />
    </label>
  )
}

function LoadingSpinner() {
  return (
    <div className="py-12 flex justify-center">
      <div className="animate-spin w-6 h-6 border-2 border-slate-300 border-t-slate-600 rounded-full" />
    </div>
  )
}
