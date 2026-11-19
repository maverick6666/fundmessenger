import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { useParams, useOutletContext } from 'react-router-dom'
import {
  ArrowLeft, TrendingUp, TrendingDown, Target, ShieldAlert,
  ShoppingCart, FileText, History, Eye, EyeOff, Trash2, Loader2,
  ChevronDown, ChevronUp, Sparkles, Circle, Check, X, Plus,
  Clock, BarChart3, MessageCircle, ClipboardList, AlertTriangle,
} from 'lucide-react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import useToastStore from '../store/toastStore'
import useAuthStore from '../store/authStore'
import useSidePanelStore from '../store/sidePanelStore'
import { useGuardedNavigate } from '../components/UnsavedChangesProvider'
import FundPageWrapper from '../components/fund/FundPageWrapper'
import { StockChart } from '../components/fund/StockChart'
import { ProfitProgressBar } from '../components/fund/ProfitProgressBar'
import { BuyRequestForm, SellRequestForm } from '../components/fund/BuyRequestForm'
import AIDecisionNoteModal from '../components/fund/AIDecisionNoteModal'
import ConfirmModal from '../components/ConfirmModal'
import useConfirm from '../hooks/useConfirm'
import ExecutionConfirmModal from '../components/fund/ExecutionConfirmModal'
import ClosePositionModal from '../components/fund/ClosePositionModal'
import useMessagingStore from '../store/messagingStore'
import { TIMEFRAMES, PRICE_REFRESH_INTERVAL_MS, MAX_TARGETS } from '../lib/fundConstants'
import {
  formatNumber, formatPrice, formatCurrency, formatQuantity,
  formatProfitRate, formatHours, formatDate, calcHoldingHours,
  getProfitLossColor, getStatusLabel, cleanNumberInput,
} from '../lib/format'

// ── Tab definitions ──
const TABS = [
  { key: 'plan', label: '매매계획', icon: Target },
  { key: 'discussion', label: '토론', icon: MessageCircle },
  { key: 'decision', label: '의사결정서', icon: FileText },
  { key: 'report', label: '운용보고서', icon: BarChart3 },
  { key: 'requests', label: '요청', icon: ClipboardList },
  { key: 'history', label: '거래이력', icon: History },
]

// ── Status badge ──
function StatusBadge({ status }) {
  const isOpen = status === 'OPEN'
  return (
    <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium
      ${isOpen ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
      {isOpen ? '활성' : '종료'}
    </span>
  )
}

// ── Market badge ──
function MarketBadge({ market }) {
  const cls =
    market === 'KOSPI' || market === 'KOSDAQ' ? 'bg-blue-50 text-blue-700'
    : market === 'CRYPTO' ? 'bg-orange-50 text-orange-700'
    : 'bg-green-50 text-green-700'
  return <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${cls}`}>{market}</span>
}

// ── Editable info cell ──
function EditableInfoItem({ label, value, className = '', editing, editValue, onEditChange, onSave, onCancel }) {
  if (editing) {
    return (
      <div className="p-3 rounded-lg bg-slate-50 border border-indigo-200 shadow-sm">
        <div className="text-xs font-medium text-slate-400 mb-1">{label}</div>
        <input
          type="number"
          step="any"
          value={editValue}
          onChange={(e) => onEditChange(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') onSave(); if (e.key === 'Escape') onCancel() }}
          onBlur={onSave}
          autoFocus
          className="w-full text-sm font-semibold tabular-nums text-slate-800 bg-white border border-slate-200 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-indigo-400"
        />
      </div>
    )
  }
  return (
    <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 shadow-sm shadow-sm hover:shadow-md transition-shadow">
      <div className="text-xs font-medium text-slate-400 mb-1">{label}</div>
      <div className={`text-sm font-semibold tabular-nums ${className || 'text-slate-800'}`}>{value}</div>
    </div>
  )
}

// ── Static info cell ──
function InfoItem({ label, value, className = '' }) {
  return (
    <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 shadow-sm shadow-sm hover:shadow-md transition-shadow">
      <div className="text-xs font-medium text-slate-400 mb-1">{label}</div>
      <div className={`text-sm font-semibold tabular-nums ${className || 'text-slate-800'}`}>{value}</div>
    </div>
  )
}

// =========================================================================

export default function FundPositionDetailPage() {
  const { teamId, positionId } = useParams()
  const { clubInfo } = useOutletContext()
  const navigate = useGuardedNavigate()
  const addToast = useToastStore(s => s.addToast)
  const { confirm: showConfirm, modal: confirmModal } = useConfirm()
  const { openDocument } = useSidePanelStore()
  const openChatRoom = useMessagingStore(s => s.openChatRoom)
  const { user } = useAuthStore()
  const clubId = clubInfo?.clubId
  const tid = Number(teamId)
  const posId = Number(positionId)

  // Auth
  const myTeamEntry = clubInfo?.myTeams?.find(t => t.teamId === tid)
  const canManage = myTeamEntry?.teamRole === 'LEADER' || myTeamEntry?.teamRole === 'MANAGER' || clubInfo?.myRole === 'PRESIDENT'

  // ── Core state ──
  const [position, setPosition] = useState(null)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('plan')

  // ── Chart state ──
  const [chartTimeframe, setChartTimeframe] = useState('1d')
  const [candles, setCandles] = useState([])
  const [chartLoading, setChartLoading] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)

  // ── Price state ──
  const [currentPrice, setCurrentPrice] = useState(null)

  // ── Inline editing ──
  const [editField, setEditField] = useState(null) // 'avgPrice' | 'quantity' | null
  const [editValue, setEditValue] = useState('')

  // ── Local plan editing ──
  const [localPlans, setLocalPlans] = useState({ buyPlan: [], takeProfitTargets: [], stopLossTargets: [] })
  const [savedPlans, setSavedPlans] = useState({ buyPlan: [], takeProfitTargets: [], stopLossTargets: [] })
  const [planMemo, setPlanMemo] = useState('')
  const [editingPlanItem, setEditingPlanItem] = useState(null) // { planType, index }
  const [editPlanData, setEditPlanData] = useState({ price: '', quantity: '' })

  // ── Notes / Reports ──
  const [notes, setNotes] = useState([])
  const [notesLoading, setNotesLoading] = useState(false)

  // ── Requests ──
  const [requests, setRequests] = useState([])
  const [requestsLoading, setRequestsLoading] = useState(false)

  // ── Modals ──
  const [showAiModal, setShowAiModal] = useState(false)
  const [showAiReportModal, setShowAiReportModal] = useState(false)
  const [reportGenerating, setReportGenerating] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [showDeleteNoteConfirm, setShowDeleteNoteConfirm] = useState(null)
  const [showBuyForm, setShowBuyForm] = useState(false)
  const [showSellForm, setShowSellForm] = useState(false)
  const [showCloseModal, setShowCloseModal] = useState(false)
  const [executeConfirm, setExecuteConfirm] = useState(null) // { planType, index, item }

  // ── Discussion room ──
  const [discussionRoomId, setDiscussionRoomId] = useState(null)
  const [discussionLoading, setDiscussionLoading] = useState(false)

  // ── Audit logs ──
  const [auditLogs, setAuditLogs] = useState([])
  const [auditLoading, setAuditLoading] = useState(false)

  // ── Plan snapshots ──
  const [planSnapshots, setPlanSnapshots] = useState([])
  const [snapshotsLoading, setSnapshotsLoading] = useState(false)
  const [showPlanHistory, setShowPlanHistory] = useState(false)

  // ── Refs ──
  const priceTimerRef = useRef(null)

  // ===== DATA FETCHING =====

  useEffect(() => {
    if (!clubId) return
    fetchPosition()
  }, [clubId, tid, posId])

  useEffect(() => {
    if (!clubId || !position) return
    fetchNotes()
    fetchRequests()
    fetchDiscussionRoom()
    fetchAuditLogs()
    fetchPlanSnapshots()
  }, [clubId, position?.id])

  // Chart data
  const fetchChart = useCallback(async () => {
    if (!clubId || !position) return
    setChartLoading(true)
    try {
      const data = await api.get(
        `/clubs/${clubId}/fund/prices/candles?ticker=${encodeURIComponent(position.ticker)}&market=${position.market}&period=${chartTimeframe}`
      )
      const candleArr = Array.isArray(data) ? data : data?.candles || []
      setCandles(candleArr)
      setHasMore(data?.hasMore === true)
    } catch (e) {
      console.error('Chart load failed:', e)
      setCandles([])
    } finally {
      setChartLoading(false)
    }
  }, [clubId, position?.ticker, position?.market, chartTimeframe])

  useEffect(() => {
    fetchChart()
  }, [fetchChart])

  // Price quote polling
  const fetchQuote = useCallback(async () => {
    if (!clubId || !position) return
    try {
      const data = await api.get(
        `/clubs/${clubId}/fund/prices/quote?ticker=${encodeURIComponent(position.ticker)}&market=${position.market}`
      )
      if (data?.price != null) setCurrentPrice(data.price)
    } catch (e) {
      // silently ignore
    }
  }, [clubId, position?.ticker, position?.market])

  useEffect(() => {
    if (!position || position.status !== 'OPEN') return
    fetchQuote()
    priceTimerRef.current = setInterval(fetchQuote, PRICE_REFRESH_INTERVAL_MS)
    return () => clearInterval(priceTimerRef.current)
  }, [fetchQuote, position?.status])

  // Past chart data loader
  const handleLoadMore = useCallback(async (beforeTimestamp, neededBars = 200) => {
    if (!position || loadingMore || !hasMore) return
    const limit = Math.min(Math.max(neededBars, 50), 500)
    setLoadingMore(true)
    try {
      const data = await api.get(
        `/clubs/${clubId}/fund/prices/candles?ticker=${encodeURIComponent(position.ticker)}&market=${position.market}&period=${chartTimeframe}&before=${beforeTimestamp}&limit=${limit}`
      )
      const newCandles = Array.isArray(data) ? data : data?.candles || []
      if (newCandles.length > 0) {
        setCandles(prev => {
          const existingTimes = new Set(prev.map(c => c.time))
          const unique = newCandles.filter(c => !existingTimes.has(c.time))
          return [...unique, ...prev]
        })
        setHasMore(data?.hasMore === true)
      } else {
        setHasMore(false)
      }
    } catch (e) {
      console.error('Past data load error:', e)
    } finally {
      setLoadingMore(false)
    }
  }, [clubId, position, chartTimeframe, loadingMore, hasMore])

  async function fetchPosition() {
    setLoading(true)
    try {
      const data = await api.get(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}`)
      setPosition(data)
      // Init local plans
      const plans = {
        buyPlan: data.buyPlan || [],
        takeProfitTargets: data.takeProfitTargets || [],
        stopLossTargets: data.stopLossTargets || [],
      }
      setLocalPlans(plans)
      setSavedPlans(JSON.parse(JSON.stringify(plans)))
    } catch (e) {
      handleApiError(e, '포지션 조회')
    } finally {
      setLoading(false)
    }
  }

  async function fetchNotes() {
    setNotesLoading(true)
    try {
      const data = await api.get(`/clubs/${clubId}/fund/teams/${tid}/notes/position/${posId}`)
      setNotes(Array.isArray(data) ? data : data?.notes || data?.content || [])
    } catch (e) {
      console.error('Notes fetch failed:', e)
    } finally {
      setNotesLoading(false)
    }
  }

  async function fetchRequests() {
    setRequestsLoading(true)
    try {
      const data = await api.get(`/clubs/${clubId}/fund/teams/${tid}/requests`)
      const all = Array.isArray(data) ? data : data?.content || []
      setRequests(all.filter(r => r.positionId === posId || r.position?.id === posId))
    } catch (e) {
      console.error('Requests fetch failed:', e)
    } finally {
      setRequestsLoading(false)
    }
  }

  // ── Fetch discussion room for this position ──
  async function fetchDiscussionRoom() {
    if (!clubId) return
    setDiscussionLoading(true)
    try {
      const rooms = await api.get(`/clubs/${clubId}/chat/rooms`)
      const allRooms = Array.isArray(rooms) ? rooms : []
      const disc = allRooms.find(r => r.type === 'DISCUSSION' && r.positionId === posId)
      setDiscussionRoomId(disc?.id || null)
    } catch { setDiscussionRoomId(null) }
    finally { setDiscussionLoading(false) }
  }

  // ── Fetch audit logs ──
  async function fetchAuditLogs() {
    if (!clubId) return
    setAuditLoading(true)
    try {
      const data = await api.get(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}/audit-logs`)
      setAuditLogs(Array.isArray(data) ? data : [])
    } catch { setAuditLogs([]) }
    finally { setAuditLoading(false) }
  }

  // ── Fetch plan snapshots ──
  async function fetchPlanSnapshots() {
    if (!clubId) return
    setSnapshotsLoading(true)
    try {
      const data = await api.get(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}/plan-snapshots`)
      setPlanSnapshots(Array.isArray(data) ? data : [])
    } catch { setPlanSnapshots([]) }
    finally { setSnapshotsLoading(false) }
  }

  // ── Team member requests ──
  async function handleRequestDiscussion() {
    if (actionLoading) return
    setActionLoading(true)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}/request-discussion`)
      addToast({ type: 'SUCCESS', title: '토론 요청 전송됨', body: '팀장에게 알림이 전송되었습니다.' })
    } catch (e) { handleApiError(e, '토론 요청') }
    finally { setActionLoading(false) }
  }

  async function handleRequestEarlyClose() {
    if (actionLoading) return
    const ok = await showConfirm('포지션 조기종료를 요청하시겠습니까?', { confirmLabel: '요청' })
    if (!ok) return
    setActionLoading(true)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}/request-early-close`)
      addToast({ type: 'SUCCESS', title: '조기종료 요청 전송됨', body: '팀장에게 알림이 전송되었습니다.' })
    } catch (e) { handleApiError(e, '조기종료 요청') }
    finally { setActionLoading(false) }
  }

  // ===== COMPUTED =====

  const profitRatePercent = position?.profitRate != null ? Number(position.profitRate) * 100 : null
  const holdingHrs = position ? (position.holdingPeriodHours ?? calcHoldingHours(position.openedAt)) : null
  const isOpen = position?.status === 'OPEN'

  // Profit info from current price
  const profitInfo = useMemo(() => {
    if (!position || !isOpen || !currentPrice) return null
    const qty = parseFloat(position.totalQuantity) || 0
    const avgPrice = parseFloat(position.averageBuyPrice) || 0
    const totalBuyAmt = parseFloat(position.totalBuyAmount) || 0
    const realized = parseFloat(position.realizedProfitLoss) || 0

    const evalAmount = currentPrice * qty
    const unrealized = qty > 0 ? (currentPrice - avgPrice) * qty : 0
    const totalProfitLoss = realized + unrealized
    const profitRate = totalBuyAmt > 0 ? (totalProfitLoss / totalBuyAmt) * 100 : 0

    return { evalAmount, unrealized, realized, totalProfitLoss, profitRate }
  }, [position, currentPrice, isOpen])

  // Pending plan changes
  const pendingChanges = useMemo(() => {
    return JSON.stringify(localPlans) !== JSON.stringify(savedPlans)
  }, [localPlans, savedPlans])

  // Remaining plan counts
  const remainingBuy = (localPlans.buyPlan || []).filter(p => !p.completed && p.price).length
  const remainingTp = (localPlans.takeProfitTargets || []).filter(p => !p.completed && p.price).length
  const remainingSl = (localPlans.stopLossTargets || []).filter(p => !p.completed && p.price).length

  // Completed (executed) items for history tab
  const executedItems = useMemo(() => {
    const items = []
    ;(position?.buyPlan || []).forEach((p, i) => {
      if (p.completed) items.push({ ...p, type: '매수', index: i + 1 })
    })
    ;(position?.takeProfitTargets || []).forEach((p, i) => {
      if (p.completed) items.push({ ...p, type: '익절', index: i + 1 })
    })
    ;(position?.stopLossTargets || []).forEach((p, i) => {
      if (p.completed) items.push({ ...p, type: '손절', index: i + 1 })
    })
    return items
  }, [position])

  // Decision notes vs reports
  const decisionNotes = useMemo(() => notes.filter(n => n.noteType !== 'report'), [notes])
  const reports = useMemo(() => notes.filter(n => n.noteType === 'report'), [notes])

  // ===== ACTIONS =====

  // Inline edit: avgPrice / totalQuantity
  function startEdit(field) {
    if (!canManage || !isOpen) return
    setEditField(field)
    setEditValue(
      field === 'avgPrice'
        ? cleanNumberInput(position.averageBuyPrice)
        : cleanNumberInput(position.totalQuantity)
    )
  }

  async function saveEdit() {
    if (!editField || !editValue) { setEditField(null); return }
    setActionLoading(true)
    try {
      const payload = editField === 'avgPrice'
        ? { averageBuyPrice: parseFloat(editValue) }
        : { totalQuantity: parseFloat(editValue) }
      await api.patch(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}`, payload)
      addToast({ type: 'SUCCESS', title: '정보 수정', body: '저장되었습니다.' })
      fetchPosition()
    } catch (e) {
      handleApiError(e, '정보 수정')
    } finally {
      setActionLoading(false)
      setEditField(null)
    }
  }

  // Toggle publish
  async function handleTogglePublish() {
    if (actionLoading) return
    setActionLoading(true)
    try {
      await api.patch(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}/publish`)
      const newVal = !position.published
      addToast({ type: 'SUCCESS', title: '공개 설정', body: newVal ? '공개되었습니다.' : '비공개되었습니다.' })
      setPosition(prev => ({ ...prev, published: newVal }))
    } catch (e) {
      handleApiError(e, '공개 설정')
    } finally {
      setActionLoading(false)
    }
  }

  // Delete position
  async function handleDeletePosition() {
    setActionLoading(true)
    try {
      await api.delete(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}`)
      addToast({ type: 'SUCCESS', title: '포지션 삭제', body: '삭제되었습니다.' })
      navigate(`/club/fund/${tid}/positions`)
    } catch (e) {
      handleApiError(e, '포지션 삭제')
    } finally {
      setActionLoading(false)
    }
  }

  // 체결 확인 — 미체결 항목 클릭 시 모달 열기, 체결 항목은 바로 API
  function handleTogglePlan(planType, index) {
    const key = planType === 'buy' ? 'buyPlan'
      : planType === 'take_profit' ? 'takeProfitTargets' : 'stopLossTargets'
    const item = (localPlans[key] || [])[index]
    if (!item) return

    if (item.completed) {
      // 이미 체결된 항목은 취소 불가 (백엔드에서 막지만 UI에서도 표시)
      addToast({ type: 'WARNING', title: '체결된 항목은 취소할 수 없습니다' })
      return
    }
    // 미체결 → 체결 확인 모달 열기
    setExecuteConfirm({ planType: planType.toUpperCase().replace('_', '_'), index, item })
  }

  // 체결 확인 모달에서 확인 → API 호출
  async function handleExecuteConfirm() {
    if (!executeConfirm || actionLoading) return
    setActionLoading(true)
    try {
      const apiPlanType = executeConfirm.planType === 'BUY' ? 'BUY'
        : executeConfirm.planType === 'TAKE_PROFIT' ? 'TAKE_PROFIT' : 'STOP_LOSS'
      const data = await api.post(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}/toggle-plan`, {
        planType: apiPlanType, index: executeConfirm.index,
      })
      if (data) setPosition(data)
      else fetchPosition()
      setExecuteConfirm(null)
      addToast({ type: 'SUCCESS', title: '체결 완료', body: '매매 계획이 체결 처리되었습니다.' })
    } catch (e) {
      handleApiError(e, '체결 확인')
    } finally {
      setActionLoading(false)
    }
  }

  // 포지션 종료 처리
  async function handleClosePosition(sellPrice, sellQuantity) {
    if (actionLoading) return
    setActionLoading(true)
    try {
      const data = await api.post(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}/close`, {
        sellPrice, sellQuantity,
      })
      if (data) setPosition(data)
      else fetchPosition()
      setShowCloseModal(false)
      addToast({ type: 'SUCCESS', title: '포지션 종료', body: '포지션이 종료되었습니다.' })
    } catch (e) {
      handleApiError(e, '포지션 종료')
    } finally {
      setActionLoading(false)
    }
  }

  // Save current plans
  async function handleSavePlans() {
    if (!pendingChanges) {
      addToast({ type: 'INFO', title: '변경사항 없음' })
      return
    }
    setActionLoading(true)
    try {
      const payload = {
        buyPlan: localPlans.buyPlan.filter(p => p.price && p.quantity),
        takeProfitTargets: localPlans.takeProfitTargets.filter(p => p.price && p.quantity),
        stopLossTargets: localPlans.stopLossTargets.filter(p => p.price && p.quantity),
      }
      const data = await api.patch(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}/plans`, payload)
      if (data) setPosition(data)
      else fetchPosition()
      addToast({ type: 'SUCCESS', title: '매매계획 저장', body: '저장되었습니다.' })
      setPlanMemo('')
    } catch (e) {
      handleApiError(e, '매매계획 저장')
    } finally {
      setActionLoading(false)
    }
  }

  // Reset plans to saved state
  function handleResetPlans() {
    setLocalPlans(JSON.parse(JSON.stringify(savedPlans)))
    setEditingPlanItem(null)
    setEditPlanData({ price: '', quantity: '' })
  }

  // Plan item CRUD (local only)
  function addPlanItem(planType) {
    const key = planType === 'buy' ? 'buyPlan' : planType === 'take_profit' ? 'takeProfitTargets' : 'stopLossTargets'
    const current = localPlans[key] || []
    if (current.length >= MAX_TARGETS) {
      addToast({ type: 'INFO', title: `최대 ${MAX_TARGETS}개까지 추가 가능합니다.` })
      return
    }
    const newItem = { price: '', quantity: '', completed: false }
    setLocalPlans(prev => ({ ...prev, [key]: [...(prev[key] || []), newItem] }))
    const newIndex = current.length
    setEditingPlanItem({ planType, index: newIndex })
    setEditPlanData({ price: '', quantity: '' })
  }

  function removePlanItem(planType, index) {
    const key = planType === 'buy' ? 'buyPlan' : planType === 'take_profit' ? 'takeProfitTargets' : 'stopLossTargets'
    setLocalPlans(prev => ({ ...prev, [key]: (prev[key] || []).filter((_, i) => i !== index) }))
    setEditingPlanItem(null)
  }

  function savePlanItem() {
    if (!editingPlanItem) return
    const { planType, index } = editingPlanItem
    if (!editPlanData.price || !editPlanData.quantity) {
      addToast({ type: 'ERROR', title: '오류', body: '가격과 수량을 입력해주세요.' })
      return
    }
    const key = planType === 'buy' ? 'buyPlan' : planType === 'take_profit' ? 'takeProfitTargets' : 'stopLossTargets'
    setLocalPlans(prev => {
      const list = [...(prev[key] || [])]
      list[index] = { ...list[index], price: parseFloat(editPlanData.price), quantity: parseFloat(editPlanData.quantity) }
      return { ...prev, [key]: list }
    })
    setEditingPlanItem(null)
    setEditPlanData({ price: '', quantity: '' })
  }

  function startEditPlanItem(planType, index, item) {
    setEditingPlanItem({ planType, index })
    setEditPlanData({ price: cleanNumberInput(item.price), quantity: cleanNumberInput(item.quantity) })
  }

  function cancelEditPlanItem() {
    if (editingPlanItem) {
      const { planType, index } = editingPlanItem
      const key = planType === 'buy' ? 'buyPlan' : planType === 'take_profit' ? 'takeProfitTargets' : 'stopLossTargets'
      const item = (localPlans[key] || [])[index]
      if (item && !item.price && !item.quantity) {
        setLocalPlans(prev => ({ ...prev, [key]: (prev[key] || []).filter((_, i) => i !== index) }))
      }
    }
    setEditingPlanItem(null)
    setEditPlanData({ price: '', quantity: '' })
  }

  // Notes actions
  async function handleDeleteNote(noteId) {
    try {
      await api.delete(`/clubs/${clubId}/fund/teams/${tid}/notes/${noteId}`)
      addToast({ type: 'SUCCESS', title: '삭제 완료' })
      fetchNotes()
    } catch (e) {
      handleApiError(e, '노트 삭제')
    }
    setShowDeleteNoteConfirm(null)
  }

  async function handleToggleNoteExport(noteId, currentExported) {
    try {
      await api.patch(`/clubs/${clubId}/fund/teams/${tid}/notes/${noteId}/export`)
      setNotes(prev => prev.map(n => n.id === noteId ? { ...n, exportEnabled: !currentExported } : n))
      addToast({ type: 'SUCCESS', title: '내보내기 설정 변경' })
    } catch (e) {
      handleApiError(e, '내보내기 설정')
    }
  }

  function handleNoteClick(note) {
    openDocument(note, note.noteType === 'report' ? 'report' : 'decision-note')
  }

  // AI report generation
  async function handleGenerateReport() {
    setReportGenerating(true)
    try {
      const result = await api.post(`/clubs/${clubId}/fund/teams/${tid}/ai/operation-report`, {
        positionId: posId,
      })
      addToast({ type: 'SUCCESS', title: '운용보고서', body: '생성 및 저장되었습니다.' })
      fetchNotes()
      setShowAiReportModal(false)
    } catch (e) {
      handleApiError(e, '운용보고서 생성')
    } finally {
      setReportGenerating(false)
    }
  }

  function handleAiNoteSaved() {
    fetchNotes()
  }

  function handleRequestFormSuccess() {
    setShowBuyForm(false)
    setShowSellForm(false)
    fetchRequests()
    fetchPosition()
  }

  // Request helpers
  function requestTypeBadge(type) {
    if (type === 'BUY' || type === 'buy') return { label: '매수', cls: 'bg-rose-50 text-rose-700' }
    return { label: '매도', cls: 'bg-blue-50 text-blue-700' }
  }

  function requestStatusPill(status) {
    const map = {
      PENDING: { bg: 'bg-amber-50', text: 'text-amber-700', label: '대기' },
      APPROVED: { bg: 'bg-emerald-50', text: 'text-emerald-700', label: '승인' },
      REJECTED: { bg: 'bg-rose-50', text: 'text-rose-700', label: '거절' },
      EXECUTED: { bg: 'bg-slate-100', text: 'text-slate-600', label: '체결' },
      DISCUSSION: { bg: 'bg-blue-50', text: 'text-blue-700', label: '토론' },
    }
    const s = map[status] || { bg: 'bg-slate-100', text: 'text-slate-600', label: status }
    return <span className={`${s.bg} ${s.text} rounded-full px-2 py-0.5 text-xs font-medium`}>{s.label}</span>
  }

  // ===== RENDER HELPERS =====

  // Plan item row
  function renderPlanItem(item, index, planType, accentColor) {
    const isEditing = editingPlanItem?.planType === planType && editingPlanItem?.index === index
    const hasValues = item.price && item.quantity
    const isClosed = !isOpen

    if (isEditing && !isClosed) {
      return (
        <div key={index} className="flex items-center gap-2 p-2 rounded-lg bg-slate-50 border border-slate-200">
          <input
            type="number" step="any" placeholder="가격"
            value={editPlanData.price}
            onChange={(e) => setEditPlanData(prev => ({ ...prev, price: e.target.value }))}
            autoFocus
            className="flex-1 min-w-0 px-2 py-1.5 border border-slate-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-indigo-400"
          />
          <span className="text-slate-400 text-xs shrink-0">&times;</span>
          <input
            type="number" step="any" placeholder="수량"
            value={editPlanData.quantity}
            onChange={(e) => setEditPlanData(prev => ({ ...prev, quantity: e.target.value }))}
            className="w-24 shrink-0 px-2 py-1.5 border border-slate-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-indigo-400"
          />
          <button onClick={savePlanItem} className="p-1.5 text-emerald-600 hover:text-emerald-700 shrink-0 cursor-pointer" title="저장">
            <Check size={14} />
          </button>
          <button onClick={cancelEditPlanItem} className="p-1.5 text-slate-400 hover:text-slate-600 shrink-0 cursor-pointer" title="취소">
            <X size={14} />
          </button>
          <button onClick={() => removePlanItem(planType, index)} className="p-1.5 text-rose-500 hover:text-rose-600 shrink-0 cursor-pointer" title="삭제">
            <Trash2 size={14} />
          </button>
        </div>
      )
    }

    if (!hasValues) return null

    return (
      <div key={index} className={`flex items-center justify-between text-sm p-2 rounded-lg ${
        item.completed ? 'bg-slate-100' : 'bg-slate-50 hover:bg-slate-100'
      } transition-colors`}>
        <div className="flex items-center gap-2 flex-1 min-w-0">
          {/* Toggle checkbox (manager only, open only) */}
          {canManage && !isClosed && (
            <button
              onClick={() => handleTogglePlan(planType, index)}
              disabled={actionLoading}
              className={`w-5 h-5 rounded border flex items-center justify-center shrink-0 cursor-pointer transition-colors
                ${item.completed
                  ? 'bg-emerald-500 border-emerald-500 text-white'
                  : 'border-slate-300 hover:border-slate-400'}`}
              title={item.completed ? '체결 취소' : '체결 확인'}
            >
              {item.completed && <Check size={12} />}
            </button>
          )}
          {/* Price x Qty */}
          <span
            className={`tabular-nums ${
              item.completed ? 'text-slate-400 line-through' : 'text-slate-700'
            } ${!isClosed && canManage ? 'cursor-pointer hover:underline' : ''}`}
            onDoubleClick={() => !isClosed && canManage && startEditPlanItem(planType, index, item)}
          >
            {formatPrice(item.price, position?.market)} &times; {formatQuantity(item.quantity)}
          </span>
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${
            item.completed ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
          }`}>
            {item.completed ? '체결' : '대기'}
          </span>
          {canManage && !isClosed && !item.completed && (
            <button
              onClick={() => removePlanItem(planType, index)}
              className="p-1 text-slate-300 hover:text-rose-500 cursor-pointer"
              title="삭제"
            >
              <X size={12} />
            </button>
          )}
        </div>
      </div>
    )
  }

  // ===== LOADING STATES =====

  if (loading) {
    return (
      <FundPageWrapper teamId={tid}>
        <div className="flex items-center justify-center py-24">
          <Loader2 size={24} className="animate-spin text-slate-400" />
        </div>
      </FundPageWrapper>
    )
  }

  if (!position) {
    return (
      <FundPageWrapper teamId={tid}>
        <div className="text-center py-24 text-slate-400">
          <p className="text-sm">포지션을 찾을 수 없습니다</p>
          <button onClick={() => navigate(`/club/fund/${tid}/positions`)}
            className="mt-3 text-xs text-slate-500 hover:text-slate-700 cursor-pointer">
            목록으로 돌아가기
          </button>
        </div>
      </FundPageWrapper>
    )
  }

  // ===== MAIN RENDER =====

  const investmentAmount = position.totalBuyAmount != null
    ? position.totalBuyAmount
    : (position.averageBuyPrice && position.totalQuantity
      ? parseFloat(position.averageBuyPrice) * parseFloat(position.totalQuantity)
      : null)

  return (
    <FundPageWrapper teamId={tid}>
      <div className="space-y-6">

        {/* ════════════════════════════════════════════
            HEADER
            ════════════════════════════════════════════ */}
        <div className="bg-white rounded-2xl border border-slate-200 px-5 py-4">
          <div className="flex items-center gap-3 mb-3">
            <button onClick={() => navigate(`/club/fund/${tid}/positions`)}
              className="p-1.5 rounded-lg hover:bg-slate-100 transition-colors cursor-pointer">
              <ArrowLeft size={16} className="text-slate-500" />
            </button>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="fund-serif text-lg font-bold text-slate-800">
                  {position.tickerName || position.ticker}
                </h1>
                <span className="text-sm text-slate-500">{position.ticker}</span>
                <MarketBadge market={position.market} />
                <StatusBadge status={position.status} />
              </div>
              {position.openerName && (
                <div className="flex items-center gap-1.5 mt-1">
                  <div className="w-5 h-5 rounded-full bg-gradient-to-br from-emerald-400 to-emerald-600 flex items-center justify-center">
                    <span className="text-xs text-white font-bold">
                      {position.openerName.charAt(0)}
                    </span>
                  </div>
                  <span className="text-xs text-slate-500">{position.openerName}</span>
                </div>
              )}
            </div>

            {/* Header action buttons */}
            {canManage && isOpen && (
              <div className="flex items-center gap-2 shrink-0 flex-wrap">
                <button onClick={handleTogglePublish} disabled={actionLoading}
                  className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg
                    border border-slate-200 hover:bg-slate-50 transition-colors cursor-pointer
                    disabled:opacity-50">
                  {position.published ? <EyeOff size={13} /> : <Eye size={13} />}
                  {position.published ? '비공개' : '공개'}
                </button>
                {position.status === 'OPEN' && (
                  <button onClick={() => setShowCloseModal(true)} disabled={actionLoading}
                    className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg
                      border border-amber-200 text-amber-700 hover:bg-amber-50 transition-colors cursor-pointer
                      disabled:opacity-50">
                    <AlertTriangle size={13} />
                    종료
                  </button>
                )}
                <button onClick={() => setShowDeleteConfirm(true)} disabled={actionLoading}
                  className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg
                    border border-rose-200 text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer
                    disabled:opacity-50">
                  <Trash2 size={13} />
                  삭제
                </button>
              </div>
            )}

            {/* 팀원용 요청 버튼 (매니저가 아닌 팀원만) */}
            {!canManage && isOpen && (
              <div className="flex items-center gap-2 shrink-0">
                <button onClick={handleRequestDiscussion} disabled={actionLoading}
                  className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg
                    border border-indigo-200 text-indigo-600 hover:bg-indigo-50 transition-colors cursor-pointer
                    disabled:opacity-50">
                  <MessageCircle size={13} />
                  토론 요청
                </button>
                <button onClick={handleRequestEarlyClose} disabled={actionLoading}
                  className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium rounded-lg
                    border border-amber-200 text-amber-700 hover:bg-amber-50 transition-colors cursor-pointer
                    disabled:opacity-50">
                  <AlertTriangle size={13} />
                  조기종료 요청
                </button>
              </div>
            )}
          </div>

          {/* ProfitProgressBar (full width) */}
          <ProfitProgressBar
            currentPrice={currentPrice}
            averagePrice={position.averageBuyPrice}
            takeProfitTargets={localPlans.takeProfitTargets}
            stopLossTargets={localPlans.stopLossTargets}
            market={position.market}
            size="md"
          />
        </div>

        {/* ════════════════════════════════════════════
            3-COLUMN GRID: Info / Chart / Right Panel
            ════════════════════════════════════════════ */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

          {/* ── Col 1: Basic Info ── */}
          <div className="bg-white rounded-2xl border border-slate-200 px-5 py-4">
            <h3 className="fund-serif text-base font-semibold text-slate-700 mb-3">기본 정보</h3>
            <div className="grid grid-cols-2 gap-2">
              <div onDoubleClick={() => startEdit('avgPrice')}>
                <EditableInfoItem
                  label="평균 매입가"
                  value={formatPrice(position.averageBuyPrice, position.market)}
                  editing={editField === 'avgPrice'}
                  editValue={editValue}
                  onEditChange={setEditValue}
                  onSave={saveEdit}
                  onCancel={() => setEditField(null)}
                />
              </div>
              <div onDoubleClick={() => startEdit('quantity')}>
                <EditableInfoItem
                  label="보유수량"
                  value={formatQuantity(position.totalQuantity)}
                  editing={editField === 'quantity'}
                  editValue={editValue}
                  onEditChange={setEditValue}
                  onSave={saveEdit}
                  onCancel={() => setEditField(null)}
                />
              </div>
              <InfoItem label="투자금액" value={investmentAmount != null ? formatCurrency(investmentAmount, position.market) : '-'} />
              <InfoItem label="현재가"
                value={currentPrice != null ? formatPrice(currentPrice, position.market) : '-'}
                className={currentPrice != null && position.averageBuyPrice
                  ? getProfitLossColor(currentPrice - parseFloat(position.averageBuyPrice))
                  : 'text-slate-400'} />
              <InfoItem label="평가금액"
                value={currentPrice != null && position.totalQuantity
                  ? formatCurrency(currentPrice * parseFloat(position.totalQuantity), position.market)
                  : '-'} />
              <InfoItem label="미실현 손익"
                value={profitInfo
                  ? `${profitInfo.unrealized >= 0 ? '+' : ''}${formatCurrency(profitInfo.unrealized, position.market)}`
                  : '-'}
                className={profitInfo ? getProfitLossColor(profitInfo.unrealized) : 'text-slate-400'} />
            </div>
          </div>

          {/* ── Col 2: Chart ── */}
          <div className="bg-white rounded-2xl border border-slate-200 px-5 py-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="fund-serif text-base font-semibold text-slate-700">차트</h3>
              <div className="flex items-center gap-1">
                {TIMEFRAMES.map(tf => (
                  <button key={tf.value}
                    onClick={() => setChartTimeframe(tf.value)}
                    className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors cursor-pointer
                      ${chartTimeframe === tf.value
                        ? 'bg-slate-800 text-white'
                        : 'text-slate-500 hover:bg-slate-100'}`}>
                    {tf.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="rounded-lg overflow-hidden border border-slate-200 shadow-sm">
              <StockChart
                candles={candles}
                loading={chartLoading}
                height={256}
                hasMore={hasMore}
                onLoadMore={handleLoadMore}
                loadingMore={loadingMore}
              />
            </div>
          </div>

          {/* ── Col 3: Right Info Panel ── */}
          <div className="bg-white rounded-2xl border border-slate-200 px-5 py-4 space-y-4">
            <h3 className="fund-serif text-base font-semibold text-slate-700">상세 정보</h3>

            <div className="space-y-3">
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">보유기간</span>
                <span className="font-medium text-slate-800 tabular-nums">{formatHours(holdingHrs)}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">수익률</span>
                <span className={`font-bold tabular-nums ${
                  profitInfo ? getProfitLossColor(profitInfo.profitRate) :
                  profitRatePercent != null ? getProfitLossColor(profitRatePercent) : 'text-slate-400'
                }`}>
                  {profitInfo ? formatProfitRate(profitInfo.profitRate)
                    : profitRatePercent != null ? formatProfitRate(profitRatePercent) : '-'}
                </span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">실현 손익</span>
                <span className={`font-medium tabular-nums ${
                  position.realizedProfitLoss != null
                    ? getProfitLossColor(position.realizedProfitLoss)
                    : 'text-slate-400'
                }`}>
                  {position.realizedProfitLoss != null
                    ? formatCurrency(position.realizedProfitLoss, position.market)
                    : '-'}
                </span>
              </div>

              <div className="border-t border-slate-100 pt-3 space-y-2">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">잔여 매수 계획</span>
                  <span className="text-slate-600 tabular-nums">{remainingBuy}건</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">잔여 익절 계획</span>
                  <span className="text-red-600 tabular-nums">{remainingTp}건</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">잔여 손절 계획</span>
                  <span className="text-blue-600 tabular-nums">{remainingSl}건</span>
                </div>
              </div>

              <div className="border-t border-slate-100 pt-3">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">체결 확인</span>
                  <span className={`font-medium ${position.infoConfirmed ? 'text-emerald-600' : 'text-amber-600'}`}>
                    {position.infoConfirmed ? '확인됨' : '미확인'}
                  </span>
                </div>
                {canManage && isOpen && !position.infoConfirmed && (
                  <button
                    onClick={async () => {
                      try {
                        await api.post(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}/confirm`)
                        addToast({ type: 'SUCCESS', title: '정보 확인 완료' })
                        fetchPosition()
                      } catch (e) { handleApiError(e, '정보 확인') }
                    }}
                    className="mt-2 w-full text-xs py-1.5 bg-emerald-50 text-emerald-700 rounded-lg hover:bg-emerald-100 transition-colors cursor-pointer font-medium"
                  >
                    정보 확인 완료
                  </button>
                )}
              </div>

              <div className="border-t border-slate-100 pt-3 space-y-2">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-400">개시일</span>
                  <span className="text-slate-600">{formatDate(position.openedAt, { hour: undefined, minute: undefined })}</span>
                </div>
                {position.closedAt && (
                  <div className="flex justify-between text-xs">
                    <span className="text-slate-400">종료일</span>
                    <span className="text-slate-600">{formatDate(position.closedAt, { hour: undefined, minute: undefined })}</span>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* ════════════════════════════════════════════
            6-TAB BAR
            ════════════════════════════════════════════ */}
        <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
          {/* Tab headers */}
          <div className="flex border-b border-slate-200 overflow-x-auto">
            {TABS.map(tab => {
              const Icon = tab.icon
              return (
                <button key={tab.key}
                  onClick={() => setActiveTab(tab.key)}
                  className={`flex items-center gap-1.5 px-4 py-3 text-sm font-medium whitespace-nowrap border-b-2 transition-colors cursor-pointer
                    ${activeTab === tab.key
                      ? 'border-slate-800 text-slate-800'
                      : 'border-transparent text-slate-400 hover:text-slate-600'}`}>
                  <Icon size={14} />
                  {tab.label}
                </button>
              )
            })}
          </div>

          {/* Tab content */}
          <div className="p-5">

            {/* ── Tab 1: 매매계획 ── */}
            {activeTab === 'plan' && (
              <div className="space-y-6">
                {/* 매수 계획 */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <ShoppingCart size={14} className="text-slate-600" />
                      <span className="text-sm font-semibold text-slate-700">매수 계획</span>
                      <span className="text-xs text-slate-400">{(localPlans.buyPlan || []).filter(p => p.price).length}건</span>
                    </div>
                    {canManage && isOpen && (
                      <button onClick={() => addPlanItem('buy')}
                        className="flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-700 cursor-pointer font-medium">
                        <Plus size={12} /> 추가
                      </button>
                    )}
                  </div>
                  <div className="space-y-1">
                    {(localPlans.buyPlan || []).length === 0 ? (
                      <p className="text-xs text-slate-400 pl-5">설정된 항목이 없습니다</p>
                    ) : (
                      localPlans.buyPlan.map((item, i) => renderPlanItem(item, i, 'buy', 'bg-blue-50'))
                    )}
                  </div>
                </div>

                {/* 익절 계획 */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <TrendingUp size={14} className="text-red-500" />
                      <span className="text-sm font-semibold text-slate-700">익절 계획</span>
                      <span className="text-xs text-slate-400">{(localPlans.takeProfitTargets || []).filter(p => p.price).length}건</span>
                    </div>
                    {canManage && isOpen && (
                      <button onClick={() => addPlanItem('take_profit')}
                        className="flex items-center gap-1 text-xs text-red-600 hover:text-red-700 cursor-pointer font-medium">
                        <Plus size={12} /> 추가
                      </button>
                    )}
                  </div>
                  {/* TargetProgressBar inline */}
                  {currentPrice && position.averageBuyPrice && (localPlans.takeProfitTargets || []).some(t => t.price && !t.completed) && (
                    <div className="mb-2">
                      <ProfitProgressBar
                        currentPrice={currentPrice}
                        averagePrice={position.averageBuyPrice}
                        takeProfitTargets={localPlans.takeProfitTargets}
                        stopLossTargets={[]}
                        market={position.market}
                        size="sm"
                      />
                    </div>
                  )}
                  <div className="space-y-1">
                    {(localPlans.takeProfitTargets || []).length === 0 ? (
                      <p className="text-xs text-slate-400 pl-5">설정된 항목이 없습니다</p>
                    ) : (
                      localPlans.takeProfitTargets.map((item, i) => renderPlanItem(item, i, 'take_profit', 'bg-red-50'))
                    )}
                  </div>
                </div>

                {/* 손절 계획 */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <ShieldAlert size={14} className="text-blue-500" />
                      <span className="text-sm font-semibold text-slate-700">손절 계획</span>
                      <span className="text-xs text-slate-400">{(localPlans.stopLossTargets || []).filter(p => p.price).length}건</span>
                    </div>
                    {canManage && isOpen && (
                      <button onClick={() => addPlanItem('stop_loss')}
                        className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700 cursor-pointer font-medium">
                        <Plus size={12} /> 추가
                      </button>
                    )}
                  </div>
                  {/* TargetProgressBar inline */}
                  {currentPrice && position.averageBuyPrice && (localPlans.stopLossTargets || []).some(t => t.price && !t.completed) && (
                    <div className="mb-2">
                      <ProfitProgressBar
                        currentPrice={currentPrice}
                        averagePrice={position.averageBuyPrice}
                        takeProfitTargets={[]}
                        stopLossTargets={localPlans.stopLossTargets}
                        market={position.market}
                        size="sm"
                      />
                    </div>
                  )}
                  <div className="space-y-1">
                    {(localPlans.stopLossTargets || []).length === 0 ? (
                      <p className="text-xs text-slate-400 pl-5">설정된 항목이 없습니다</p>
                    ) : (
                      localPlans.stopLossTargets.map((item, i) => renderPlanItem(item, i, 'stop_loss', 'bg-blue-50'))
                    )}
                  </div>
                </div>

                {/* Save / Reset buttons */}
                {canManage && isOpen && (
                  <div className="pt-4 border-t border-slate-100 space-y-3">
                    {pendingChanges && (
                      <div className="flex items-center gap-2 p-2 bg-amber-50 border border-amber-200 rounded-lg">
                        <AlertTriangle size={14} className="text-amber-600 shrink-0" />
                        <span className="text-xs text-amber-700">저장되지 않은 변경사항이 있습니다</span>
                      </div>
                    )}
                    {/* Memo */}
                    <textarea
                      placeholder="메모 (선택)"
                      value={planMemo}
                      onChange={(e) => setPlanMemo(e.target.value)}
                      rows={2}
                      className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-indigo-400 resize-none"
                    />
                    <div className="flex items-center gap-2">
                      <button
                        onClick={handleSavePlans}
                        disabled={actionLoading || !pendingChanges}
                        className="flex-1 py-2 text-sm font-semibold bg-slate-800 text-white rounded-lg hover:bg-slate-700 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {actionLoading ? '저장 중...' : '현재 계획 저장'}
                      </button>
                      <button
                        onClick={handleResetPlans}
                        disabled={!pendingChanges}
                        className="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors cursor-pointer disabled:opacity-50"
                      >
                        초기화
                      </button>
                    </div>
                  </div>
                )}

                {/* ── 계획 이력 ── */}
                {planSnapshots.length > 0 && (
                  <div className="mt-4 pt-3 border-t border-slate-100">
                    <button
                      onClick={() => setShowPlanHistory(!showPlanHistory)}
                      className="flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-700 cursor-pointer"
                    >
                      <History size={13} />
                      계획 이력 ({planSnapshots.length}건)
                      {showPlanHistory ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                    </button>
                    {showPlanHistory && (
                      <div className="mt-2 space-y-2 max-h-48 overflow-y-auto">
                        {planSnapshots.map(snap => (
                          <div key={snap.id} className="p-2.5 bg-slate-50 rounded-lg border border-slate-200 shadow-sm text-xs">
                            <div className="flex items-center justify-between mb-1.5">
                              <span className="font-semibold text-slate-700">v{snap.version}</span>
                              <span className="text-slate-400 tabular-nums">{formatDate(snap.createdAt, { hour: '2-digit', minute: '2-digit' })}</span>
                            </div>
                            {snap.savedByName && <span className="text-slate-500">저장: {snap.savedByName}</span>}
                            <div className="flex gap-3 mt-1 text-xs text-slate-500">
                              <span>매수 {(snap.buyPlan || []).length}건</span>
                              <span>익절 {(snap.takeProfitTargets || []).length}건</span>
                              <span>손절 {(snap.stopLossTargets || []).length}건</span>
                            </div>
                            {snap.memo && <p className="mt-1 text-slate-500 italic">{snap.memo}</p>}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* ── Tab 2: 토론 ── */}
            {activeTab === 'discussion' && (
              <div className="space-y-4">
                {discussionLoading ? (
                  <div className="flex justify-center py-8">
                    <Loader2 size={16} className="animate-spin text-slate-300" />
                  </div>
                ) : discussionRoomId ? (
                  <div className="text-center py-8 space-y-3">
                    <MessageCircle size={32} className="mx-auto text-indigo-400" />
                    <p className="text-sm text-slate-600">이 종목의 토론방이 있습니다</p>
                    <button
                      onClick={() => openChatRoom(discussionRoomId)}
                      className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg transition-colors cursor-pointer"
                    >
                      토론방 열기
                    </button>
                  </div>
                ) : (
                  <div className="text-center py-8 space-y-3">
                    <MessageCircle size={32} className="mx-auto text-slate-300" />
                    <p className="text-sm text-slate-500">아직 토론방이 없습니다</p>
                    <p className="text-xs text-slate-400">매매요청에서 "토론" 버튼을 눌러 토론방을 시작하세요</p>
                  </div>
                )}
              </div>
            )}

            {/* ── Tab 3: 의사결정서 ── */}
            {activeTab === 'decision' && (
              <div className="space-y-4">
                {/* AI generate button */}
                {canManage && isOpen && (
                  <div className="flex items-center gap-2">
                    <button onClick={() => setShowAiModal(true)}
                      className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg
                        border border-indigo-200 text-indigo-600 hover:bg-indigo-50 transition-colors cursor-pointer">
                      <Sparkles size={13} />
                      AI 의사결정서 생성
                    </button>
                  </div>
                )}

                {/* Note list */}
                {notesLoading ? (
                  <div className="flex justify-center py-8">
                    <Loader2 size={16} className="animate-spin text-slate-300" />
                  </div>
                ) : decisionNotes.length === 0 ? (
                  <p className="text-sm text-slate-400 text-center py-8">작성된 의사결정서가 없습니다</p>
                ) : (
                  <div className="space-y-2">
                    {decisionNotes.map(note => (
                      <div key={note.id}
                        className="flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-slate-50 transition-colors
                          border border-transparent hover:border-slate-200 cursor-pointer"
                        onClick={() => handleNoteClick(note)}>
                        <FileText size={14} className="text-indigo-400 shrink-0" />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-slate-700 truncate">{note.title}</p>
                          <p className="text-xs text-slate-400">
                            {note.authorName || '-'} &middot; {formatDate(note.createdAt, { hour: undefined, minute: undefined })}
                          </p>
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0" onClick={e => e.stopPropagation()}>
                          {/* Export toggle */}
                          <button
                            onClick={() => handleToggleNoteExport(note.id, note.exportEnabled)}
                            className="p-1 cursor-pointer"
                            title={note.exportEnabled ? '공개' : '비공개'}>
                            <Circle size={10}
                              className={note.exportEnabled
                                ? 'fill-emerald-500 text-emerald-500'
                                : 'fill-slate-300 text-slate-300'} />
                          </button>
                          {/* Delete */}
                          {canManage && (
                            <button
                              onClick={() => setShowDeleteNoteConfirm(note.id)}
                              className="p-1 text-slate-300 hover:text-rose-500 cursor-pointer"
                              title="삭제">
                              <Trash2 size={12} />
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* ── Tab 4: 운용보고서 ── */}
            {activeTab === 'report' && (
              <div className="space-y-4">
                {/* AI generate button */}
                {canManage && isOpen && (
                  <div className="flex items-center gap-2">
                    <button onClick={() => setShowAiReportModal(true)}
                      className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg
                        border border-amber-200 text-amber-600 hover:bg-amber-50 transition-colors cursor-pointer">
                      <Sparkles size={13} />
                      AI 운용보고서 생성
                    </button>
                  </div>
                )}

                {/* Report list */}
                {notesLoading ? (
                  <div className="flex justify-center py-8">
                    <Loader2 size={16} className="animate-spin text-slate-300" />
                  </div>
                ) : reports.length === 0 ? (
                  <p className="text-sm text-slate-400 text-center py-8">작성된 운용보고서가 없습니다</p>
                ) : (
                  <div className="space-y-2">
                    {reports.map(note => (
                      <div key={note.id}
                        className="flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-amber-50 transition-colors
                          border border-amber-100 cursor-pointer"
                        onClick={() => handleNoteClick(note)}>
                        <BarChart3 size={14} className="text-amber-500 shrink-0" />
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-slate-700 truncate">{note.title}</p>
                          <p className="text-xs text-slate-400">
                            {note.authorName || '-'} &middot; {formatDate(note.createdAt, { hour: undefined, minute: undefined })}
                          </p>
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0" onClick={e => e.stopPropagation()}>
                          <button
                            onClick={() => handleToggleNoteExport(note.id, note.exportEnabled)}
                            className="p-1 cursor-pointer"
                            title={note.exportEnabled ? '공개' : '비공개'}>
                            <Circle size={10}
                              className={note.exportEnabled
                                ? 'fill-emerald-500 text-emerald-500'
                                : 'fill-slate-300 text-slate-300'} />
                          </button>
                          {canManage && (
                            <button
                              onClick={() => setShowDeleteNoteConfirm(note.id)}
                              className="p-1 text-slate-300 hover:text-rose-500 cursor-pointer"
                              title="삭제">
                              <Trash2 size={12} />
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* ── Tab 5: 요청 ── */}
            {activeTab === 'requests' && (
              <div className="space-y-4">
                {/* Buy / Sell request forms */}
                {canManage && isOpen && (
                  <div className="flex items-center gap-2 mb-3">
                    <button
                      onClick={() => { setShowBuyForm(p => !p); setShowSellForm(false) }}
                      className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg
                        border transition-colors cursor-pointer
                        ${showBuyForm
                          ? 'bg-rose-50 border-rose-300 text-rose-700'
                          : 'border-slate-200 text-slate-600 hover:bg-slate-50'}`}>
                      <ShoppingCart size={13} />
                      매수 요청
                      {showBuyForm ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                    </button>
                    <button
                      onClick={() => { setShowSellForm(p => !p); setShowBuyForm(false) }}
                      className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg
                        border transition-colors cursor-pointer
                        ${showSellForm
                          ? 'bg-blue-50 border-blue-300 text-blue-700'
                          : 'border-slate-200 text-slate-600 hover:bg-slate-50'}`}>
                      <TrendingDown size={13} />
                      매도 요청
                      {showSellForm ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                    </button>
                  </div>
                )}

                {showBuyForm && (
                  <div className="border border-slate-200 rounded-lg p-4 mb-4">
                    <BuyRequestForm clubId={clubId} teamId={tid} onSuccess={handleRequestFormSuccess} />
                  </div>
                )}

                {showSellForm && (
                  <div className="border border-slate-200 rounded-lg p-4 mb-4">
                    <SellRequestForm
                      clubId={clubId} teamId={tid} positionId={posId}
                      position={{
                        ticker: position.ticker,
                        tickerName: position.tickerName,
                        market: position.market,
                        averagePrice: position.averageBuyPrice,
                        quantity: position.totalQuantity,
                      }}
                      onSuccess={handleRequestFormSuccess}
                    />
                  </div>
                )}

                {/* Request list table */}
                {requestsLoading ? (
                  <div className="flex justify-center py-8">
                    <Loader2 size={16} className="animate-spin text-slate-300" />
                  </div>
                ) : requests.length === 0 ? (
                  <p className="text-sm text-slate-400 text-center py-8">매매 요청 내역이 없습니다</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="border-b border-slate-100 text-slate-400">
                          <th className="text-left py-1.5 px-2 font-medium">유형</th>
                          <th className="text-right py-1.5 px-2 font-medium">가격</th>
                          <th className="text-right py-1.5 px-2 font-medium">수량</th>
                          <th className="text-right py-1.5 px-2 font-medium">금액</th>
                          <th className="text-center py-1.5 px-2 font-medium">상태</th>
                          <th className="text-left py-1.5 px-2 font-medium">요청자</th>
                          <th className="text-left py-1.5 px-2 font-medium">일시</th>
                        </tr>
                      </thead>
                      <tbody>
                        {requests.map(req => {
                          const badge = requestTypeBadge(req.type || req.requestType)
                          return (
                            <tr key={req.id} className="border-b border-slate-50 hover:bg-slate-50">
                              <td className="py-1.5 px-2">
                                <span className={`${badge.cls} rounded-full px-2 py-0.5 text-xs font-medium`}>{badge.label}</span>
                              </td>
                              <td className="py-1.5 px-2 text-right tabular-nums text-slate-700">
                                {formatPrice(req.price || req.requestPrice, position.market)}
                              </td>
                              <td className="py-1.5 px-2 text-right tabular-nums text-slate-700">
                                {formatQuantity(req.quantity || req.requestQuantity)}
                              </td>
                              <td className="py-1.5 px-2 text-right tabular-nums text-slate-500">
                                {req.totalAmount != null ? formatNumber(req.totalAmount) : '-'}
                              </td>
                              <td className="py-1.5 px-2 text-center">
                                {requestStatusPill(req.status)}
                              </td>
                              <td className="py-1.5 px-2 text-slate-500">{req.requesterName || req.authorName || '-'}</td>
                              <td className="py-1.5 px-2 text-slate-400">
                                {formatDate(req.createdAt, { year: undefined })}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

            {/* ── Tab 6: 거래이력 ── */}
            {activeTab === 'history' && (
              <div>
                {executedItems.length === 0 ? (
                  <p className="text-sm text-slate-400 text-center py-8">체결 이력이 없습니다</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="border-b border-slate-100 text-slate-400">
                          <th className="text-left py-1.5 px-2 font-medium">유형</th>
                          <th className="text-center py-1.5 px-2 font-medium">차수</th>
                          <th className="text-right py-1.5 px-2 font-medium">체결가</th>
                          <th className="text-right py-1.5 px-2 font-medium">수량</th>
                          <th className="text-right py-1.5 px-2 font-medium">체결금액</th>
                          <th className="text-right py-1.5 px-2 font-medium">손익</th>
                        </tr>
                      </thead>
                      <tbody>
                        {executedItems.map((item, idx) => {
                          const amount = item.price && item.quantity
                            ? parseFloat(item.price) * parseFloat(item.quantity)
                            : null
                          const avgBuyPrice = parseFloat(position.averageBuyPrice) || 0
                          let pnl = null
                          if ((item.type === '익절' || item.type === '손절') && avgBuyPrice && item.price && item.quantity) {
                            pnl = (parseFloat(item.price) - avgBuyPrice) * parseFloat(item.quantity)
                          }
                          const typeCls = item.type === '매수'
                            ? 'bg-slate-100 text-slate-600'
                            : item.type === '익절'
                              ? 'bg-red-50 text-red-700'
                              : 'bg-blue-50 text-blue-700'
                          return (
                            <tr key={idx} className="border-b border-slate-50">
                              <td className="py-1.5 px-2">
                                <span className={`${typeCls} rounded-full px-2 py-0.5 text-xs font-medium`}>{item.type}</span>
                              </td>
                              <td className="py-1.5 px-2 text-center tabular-nums text-slate-600">{item.index}차</td>
                              <td className="py-1.5 px-2 text-right tabular-nums text-slate-700">
                                {formatPrice(item.price, position.market)}
                              </td>
                              <td className="py-1.5 px-2 text-right tabular-nums text-slate-700">
                                {formatQuantity(item.quantity)}
                              </td>
                              <td className="py-1.5 px-2 text-right tabular-nums text-slate-500">
                                {amount != null ? formatCurrency(amount, position.market) : '-'}
                              </td>
                              <td className={`py-1.5 px-2 text-right tabular-nums font-medium ${
                                pnl != null ? getProfitLossColor(pnl) : 'text-slate-400'
                              }`}>
                                {pnl != null
                                  ? `${pnl >= 0 ? '+' : ''}${formatCurrency(pnl, position.market)}`
                                  : '-'}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                )}

                {/* ── 활동 이력 (감사 로그) ── */}
                <div className="mt-6 pt-4 border-t border-slate-100">
                  <h4 className="text-xs font-semibold text-slate-500 mb-3">활동 이력</h4>
                  {auditLoading ? (
                    <div className="flex justify-center py-4"><Loader2 size={14} className="animate-spin text-slate-300" /></div>
                  ) : auditLogs.length === 0 ? (
                    <p className="text-xs text-slate-400 text-center py-4">활동 이력이 없습니다</p>
                  ) : (
                    <div className="space-y-2 max-h-60 overflow-y-auto">
                      {auditLogs.map(log => {
                        const icons = {
                          FIELD_UPDATE: '✏️', PLAN_TOGGLE: '✅', POSITION_CLOSE: '🔒',
                          PLAN_SAVE: '💾', DISCUSSION_REQUEST: '💬', EARLY_CLOSE_REQUEST: '⚠️',
                        }
                        let desc = log.action
                        try {
                          const d = JSON.parse(log.detail || '{}')
                          if (log.action === 'FIELD_UPDATE') desc = `${d.field}: ${d.before} → ${d.after}`
                          else if (log.action === 'PLAN_TOGGLE') desc = `${d.planType} ${d.index + 1}차 체결`
                          else if (log.action === 'POSITION_CLOSE') desc = `포지션 종료 (P&L: ${formatCurrency(d.pnl, position.market)})`
                          else if (log.action === 'PLAN_SAVE') desc = '매매계획 저장'
                          else if (log.action === 'DISCUSSION_REQUEST') desc = '토론 요청'
                          else if (log.action === 'EARLY_CLOSE_REQUEST') desc = '조기종료 요청'
                        } catch {}
                        return (
                          <div key={log.id} className="flex items-start gap-2 text-xs">
                            <span className="shrink-0 mt-0.5">{icons[log.action] || '📝'}</span>
                            <div className="flex-1 min-w-0">
                              <span className="text-slate-700">{desc}</span>
                              {log.actorName && (
                                <span className="text-slate-400 ml-1.5">— {log.actorName}</span>
                              )}
                            </div>
                            <span className="text-slate-400 shrink-0 tabular-nums">{formatDate(log.createdAt, { hour: '2-digit', minute: '2-digit' })}</span>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>

      </div>

      {/* ════════════════════════════════════════════
          MODALS
          ════════════════════════════════════════════ */}

      {/* AI Decision Note Modal */}
      <AIDecisionNoteModal
        isOpen={showAiModal}
        onClose={() => setShowAiModal(false)}
        clubId={clubId}
        teamId={tid}
        position={position}
        onSaved={handleAiNoteSaved}
      />

      {/* AI Operation Report Modal */}
      {showAiReportModal && (
        <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center">
          <div className="absolute inset-0 bg-black/40" onClick={() => !reportGenerating && setShowAiReportModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 p-6">
            <h2 className="text-lg font-bold text-slate-900 mb-3">AI 운용보고서 생성</h2>
            <p className="text-sm text-slate-500 mb-4">
              이 포지션의 모든 정보(매매계획, 의사결정서, 요청 등)를 AI가 분석하여 구조화된 운용보고서를 생성합니다.
            </p>
            <p className="text-xs text-slate-400 mb-6">
              * 생성된 보고서는 자동으로 저장됩니다.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setShowAiReportModal(false)}
                disabled={reportGenerating}
                className="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer disabled:opacity-50"
              >
                취소
              </button>
              <button
                onClick={handleGenerateReport}
                disabled={reportGenerating}
                className="px-4 py-2 text-sm font-semibold text-white bg-amber-600 hover:bg-amber-700 rounded-lg transition-colors cursor-pointer disabled:opacity-50"
              >
                {reportGenerating ? (
                  <span className="flex items-center gap-2">
                    <Loader2 size={14} className="animate-spin" />
                    생성 중...
                  </span>
                ) : '보고서 생성'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete position confirm */}
      {showDeleteConfirm && (
        <ConfirmModal
          title="포지션 삭제"
          message={`"${position.tickerName || position.ticker}" 포지션을 삭제하시겠습니까? 연관된 모든 데이터가 함께 삭제됩니다.`}
          confirmLabel="삭제"
          danger
          onConfirm={handleDeletePosition}
          onClose={() => setShowDeleteConfirm(false)}
        />
      )}

      {/* Delete note confirm */}
      {showDeleteNoteConfirm && (
        <ConfirmModal
          title="문서 삭제"
          message="이 문서를 삭제하시겠습니까?"
          confirmLabel="삭제"
          danger
          onConfirm={() => handleDeleteNote(showDeleteNoteConfirm)}
          onClose={() => setShowDeleteNoteConfirm(null)}
        />
      )}

      {/* Execution confirm modal */}
      {executeConfirm && (
        <ExecutionConfirmModal
          isOpen
          onClose={() => setExecuteConfirm(null)}
          onConfirm={handleExecuteConfirm}
          planItem={executeConfirm.item}
          planType={executeConfirm.planType}
          market={position?.market}
          averageBuyPrice={position?.averageBuyPrice}
          loading={actionLoading}
        />
      )}

      {/* Close position modal */}
      {showCloseModal && position && (
        <ClosePositionModal
          isOpen
          onClose={() => setShowCloseModal(false)}
          onConfirm={handleClosePosition}
          position={position}
          loading={actionLoading}
        />
      )}
      {confirmModal}
    </FundPageWrapper>
  )
}
