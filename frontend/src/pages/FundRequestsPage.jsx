import { useState, useEffect, useMemo } from 'react'
import { useParams, useOutletContext } from 'react-router-dom'
import { ArrowUpDown, Check, X, Trash2, MessageSquare, ChevronDown, ChevronUp, Loader2 } from 'lucide-react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import useToastStore from '../store/toastStore'
import useConfirm from '../hooks/useConfirm'
import FundPageWrapper from '../components/fund/FundPageWrapper'
import { formatTime, formatQuantity, formatCurrency, formatPercent } from '../lib/format'

const STATUS_TABS = [
  { value: 'all', label: '전체' },
  { value: 'PENDING', label: '대기' },
  { value: 'APPROVED', label: '승인' },
  { value: 'REJECTED', label: '거절' },
]

const STATUS_PILL = {
  PENDING:    { bg: 'bg-amber-50',   text: 'text-amber-700',   label: '대기' },
  DISCUSSION: { bg: 'bg-blue-50',    text: 'text-blue-700',    label: '토론' },
  APPROVED:   { bg: 'bg-emerald-50', text: 'text-emerald-700', label: '승인' },
  REJECTED:   { bg: 'bg-rose-50',    text: 'text-rose-700',    label: '거절' },
  EXECUTED:   { bg: 'bg-slate-100',  text: 'text-slate-600',   label: '체결' },
}

export default function FundRequestsPage() {
  const { teamId } = useParams()
  const { clubInfo } = useOutletContext()
  const clubId = clubInfo?.clubId
  const tid = Number(teamId)
  const addToast = useToastStore(s => s.addToast)
  const { confirm: showConfirm, modal: confirmModal } = useConfirm()

  const [mainTab, setMainTab] = useState('team')  // 'team' | 'mine'
  const [statusFilter, setStatusFilter] = useState('all')
  const [sort, setSort] = useState('newest')
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [expandedId, setExpandedId] = useState(null)
  const [actionLoading, setActionLoading] = useState(null) // requestId being acted on

  const myTeamEntry = clubInfo?.myTeams?.find(t => t.teamId === tid)
  const canManage = myTeamEntry?.teamRole === 'LEADER' || myTeamEntry?.teamRole === 'MANAGER'
  const myUserId = clubInfo?.userId

  // Fetch requests
  useEffect(() => {
    if (!clubId) return
    setLoading(true)
    const endpoint = mainTab === 'mine'
      ? `/clubs/${clubId}/fund/teams/${tid}/requests/mine`
      : `/clubs/${clubId}/fund/teams/${tid}/requests`
    const params = statusFilter === 'all' ? '' : `?status=${statusFilter}`

    api.get(`${endpoint}${params}`)
      .then(data => setRequests(Array.isArray(data) ? data : []))
      .catch(e => handleApiError(e, '매매요청 조회'))
      .finally(() => setLoading(false))
  }, [clubId, tid, mainTab, statusFilter])

  // Count badges per status (computed from full list)
  const counts = useMemo(() => {
    const all = requests.length
    const pending = requests.filter(r => r.status === 'PENDING' || r.status === 'DISCUSSION').length
    const approved = requests.filter(r => r.status === 'APPROVED').length
    const rejected = requests.filter(r => r.status === 'REJECTED').length
    return { all, pending, approved, rejected }
  }, [requests])

  // Get count for a given status tab
  const getCountForStatus = (statusValue) => {
    if (statusValue === 'all') return counts.all
    if (statusValue === 'PENDING') return counts.pending
    if (statusValue === 'APPROVED') return counts.approved
    if (statusValue === 'REJECTED') return counts.rejected
    return 0
  }

  // Sort
  let items = [...requests]
  if (sort === 'oldest') items.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))

  const handleApprove = async (id) => {
    if (actionLoading) return
    // 토론 중인 요청 → 경고 후 진행
    const req = requests.find(r => r.id === id)
    if (req?.status === 'DISCUSSED' || req?.status === 'DISCUSSION') {
      const ok = await showConfirm('토론 중인 요청입니다. 승인하면 토론이 종료됩니다. 계속하시겠습니까?', { confirmLabel: '승인', danger: true })
      if (!ok) return
    }
    setActionLoading(id)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${tid}/requests/${id}/approve`, {})
      addToast({ type: 'SUCCESS', title: '요청이 승인되었습니다' })
      setRequests(prev => prev.map(r => r.id === id ? { ...r, status: 'APPROVED' } : r))
    } catch (e) { handleApiError(e, '요청 승인') }
    finally { setActionLoading(null) }
  }

  const handleReject = async (id) => {
    // 토론 중인 요청 → 경고 후 진행
    const req = requests.find(r => r.id === id)
    if (req?.status === 'DISCUSSED' || req?.status === 'DISCUSSION') {
      const ok = await showConfirm('토론 중인 요청입니다. 거절하면 토론이 종료됩니다. 계속하시겠습니까?', { confirmLabel: '거절', danger: true })
      if (!ok) return
    }
    const reason = prompt('거절 사유를 입력하세요')
    if (!reason) return
    if (actionLoading) return
    setActionLoading(id)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${tid}/requests/${id}/reject`, { rejectionReason: reason })
      addToast({ type: 'SUCCESS', title: '요청이 거절되었습니다' })
      setRequests(prev => prev.map(r => r.id === id ? { ...r, status: 'REJECTED' } : r))
    } catch (e) { handleApiError(e, '요청 거절') }
    finally { setActionLoading(null) }
  }

  const handleDiscuss = async (id) => {
    if (actionLoading) return
    setActionLoading(id)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${tid}/requests/${id}/discuss`, {})
      addToast({ type: 'SUCCESS', title: '토론이 시작되었습니다' })
      setRequests(prev => prev.map(r => r.id === id ? { ...r, status: 'DISCUSSION' } : r))
    } catch (e) { handleApiError(e, '토론 시작') }
    finally { setActionLoading(null) }
  }

  const handleDelete = async (id) => {
    const ok = await showConfirm('이 요청을 삭제하시겠습니까?', { confirmLabel: '삭제', danger: true })
    if (!ok) return
    try {
      await api.delete(`/clubs/${clubId}/fund/teams/${tid}/requests/${id}`)
      addToast({ type: 'SUCCESS', title: '요청이 삭제되었습니다' })
      setRequests(prev => prev.filter(r => r.id !== id))
    } catch (e) { handleApiError(e, '요청 삭제') }
  }

  const switchMainTab = (tab) => {
    setMainTab(tab)
    setStatusFilter('all')
    setExpandedId(null)
  }

  const isActionDisabled = (id) => actionLoading === id

  return (
    <FundPageWrapper teamId={tid}>
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
          <h2 className="text-sm font-bold text-slate-800 fund-serif">매매요청 관리</h2>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setSort(sort === 'newest' ? 'oldest' : 'newest')}
              className="flex items-center gap-1 px-2 py-1 text-xs text-slate-500 hover:text-slate-700 cursor-pointer"
            >
              <ArrowUpDown size={12} />
              {sort === 'newest' ? '최신순' : '오래된순'}
            </button>
          </div>
        </div>

        {/* Main Tabs: Team / Mine */}
        <div className="flex items-center px-5 border-b border-slate-100">
          {[
            { value: 'team', label: '팀 요청' },
            { value: 'mine', label: '내 요청' },
          ].map(tab => (
            <button
              key={tab.value}
              onClick={() => switchMainTab(tab.value)}
              className={`relative px-3 py-2.5 text-xs font-medium transition-colors cursor-pointer ${
                mainTab === tab.value
                  ? 'text-slate-800'
                  : 'text-slate-400 hover:text-slate-600'
              }`}
            >
              {tab.label}
              {/* Active indicator */}
              {mainTab === tab.value && (
                <span className="absolute bottom-0 left-1 right-1 h-0.5 bg-slate-700 rounded-full" />
              )}
            </button>
          ))}
        </div>

        {/* Status Filter Pills with count badges */}
        <div className="flex items-center px-5 py-2 border-b border-slate-100 gap-1">
          {STATUS_TABS.map(tab => {
            const count = getCountForStatus(tab.value)
            return (
              <button
                key={tab.value}
                onClick={() => setStatusFilter(tab.value)}
                className={`px-2.5 py-0.5 text-xs rounded-full font-medium transition-colors cursor-pointer flex items-center gap-1 ${
                  statusFilter === tab.value ? 'bg-slate-700 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                }`}
              >
                {tab.label}
                {/* Count badge */}
                <span className={`px-1 text-xs font-bold rounded-full min-w-[14px] text-center ${
                  statusFilter === tab.value
                    ? 'bg-white/20 text-white'
                    : tab.value === 'PENDING' && count > 0
                      ? 'bg-rose-500 text-white'
                      : 'bg-slate-200 text-slate-500'
                }`}>
                  {count}
                </span>
              </button>
            )
          })}
          <span className="ml-auto text-xs text-slate-400">{items.length}건</span>
        </div>

        {/* Table */}
        {loading ? (
          <div className="p-5 space-y-2">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="animate-pulse bg-slate-100 rounded h-10" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <p className="text-sm text-slate-400 py-12 text-center">
            {mainTab === 'mine' ? '내 매매요청이 없습니다' : '매매요청이 없습니다'}
          </p>
        ) : (
          <div className="divide-y divide-slate-50">
            {items.map(req => {
              const isBuy = req.requestType === 'BUY'
              const pill = STATUS_PILL[req.status] || STATUS_PILL.PENDING
              const isExpanded = expandedId === req.id
              const isOwn = req.requesterId === myUserId
              const isPending = req.status === 'PENDING'
              const isDiscussion = req.status === 'DISCUSSION'
              const isActing = isActionDisabled(req.id)

              return (
                <div key={req.id} className="hover:bg-slate-50/50 transition-colors">
                  {/* Main row */}
                  <div className="flex items-center px-4 py-2.5 gap-3 text-xs">
                    {/* Type badge */}
                    <span className={`shrink-0 rounded-md px-1.5 py-0.5 text-xs font-bold text-white ${isBuy ? 'bg-blue-500' : 'bg-rose-500'}`}>
                      {isBuy ? 'BUY' : 'SELL'}
                    </span>

                    {/* Ticker */}
                    <div className="min-w-0 flex-1">
                      <span className="font-semibold text-slate-800">{req.targetTickerName || req.targetTicker}</span>
                      <span className="ml-1 text-slate-400 text-xs">{req.targetTicker}</span>
                    </div>

                    {/* Requester */}
                    <span className="hidden sm:block text-slate-500 shrink-0">{req.requesterName || '-'}</span>

                    {/* Amount */}
                    <span className="text-right tabular-nums text-slate-500 shrink-0 w-20">
                      {isBuy
                        ? req.orderAmount != null ? `${Number(req.orderAmount).toLocaleString()}원` : '-'
                        : req.sellQuantity != null ? `${req.sellQuantity}주` : '-'
                      }
                    </span>

                    {/* Status */}
                    <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${pill.bg} ${pill.text}`}>
                      {pill.label}
                    </span>

                    {/* Date */}
                    <span className="hidden md:block text-slate-400 tabular-nums shrink-0 w-20 text-right">
                      {formatTime(req.createdAt)}
                    </span>

                    {/* Actions */}
                    <div className="flex items-center gap-0.5 shrink-0">
                      {/* Manager approve/reject — for PENDING and DISCUSSION */}
                      {canManage && (isPending || isDiscussion) && (
                        <>
                          <button
                            onClick={() => handleApprove(req.id)}
                            disabled={isActing}
                            className="p-1 text-emerald-600 hover:bg-emerald-50 rounded cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                            title="승인"
                          >
                            {isActing ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
                          </button>
                          <button
                            onClick={() => handleReject(req.id)}
                            disabled={isActing}
                            className="p-1 text-rose-600 hover:bg-rose-50 rounded cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                            title="거절"
                          >
                            <X size={14} />
                          </button>
                        </>
                      )}

                      {/* Discussion button — only for PENDING status */}
                      {canManage && isPending && (
                        <button
                          onClick={() => handleDiscuss(req.id)}
                          disabled={isActing}
                          className="p-1 text-blue-500 hover:bg-blue-50 rounded cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                          title="토론 시작"
                        >
                          <MessageSquare size={14} />
                        </button>
                      )}

                      {/* Delete own pending request */}
                      {isOwn && isPending && (
                        <button
                          onClick={() => handleDelete(req.id)}
                          className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded cursor-pointer"
                          title="요청 삭제"
                        >
                          <Trash2 size={13} />
                        </button>
                      )}

                      {/* Expand toggle */}
                      <button
                        onClick={() => setExpandedId(isExpanded ? null : req.id)}
                        className="p-1 text-slate-400 hover:text-slate-600 rounded cursor-pointer"
                        title="세부사항"
                      >
                        {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                      </button>
                    </div>
                  </div>

                  {/* Expanded detail */}
                  {isExpanded && (
                    <div className="px-4 pb-3 pt-0 text-xs text-slate-500 space-y-1.5 border-t border-slate-50 bg-slate-50/30">
                      {/* Buy details */}
                      {isBuy && (
                        <>
                          {req.buyPrice != null && (
                            <div>
                              <span className="text-slate-400 mr-1">희망 매수가:</span>
                              <span className="font-medium text-slate-700">
                                {formatCurrency(req.buyPrice, req.targetMarket)}
                              </span>
                            </div>
                          )}
                          {req.orderQuantity != null && (
                            <div>
                              <span className="text-slate-400 mr-1">희망 수량:</span>
                              <span className="font-medium text-slate-700">
                                {formatQuantity(req.orderQuantity)}
                              </span>
                            </div>
                          )}
                          {req.targetRatio != null && (
                            <div>
                              <span className="text-slate-400 mr-1">목표 비중:</span>
                              <span className="font-medium text-slate-700">
                                {formatPercent(req.targetRatio)}
                              </span>
                            </div>
                          )}

                          {/* Buy plan (매수 계획) */}
                          {req.buyPlan?.length > 0 && (
                            <div>
                              <span className="text-slate-400 mr-1">매수 계획:</span>
                              {req.buyPlan.map((o, i) => (
                                <span key={i} className="text-slate-700 mr-2">
                                  {formatCurrency(o.price, req.targetMarket)}
                                  {o.quantity != null ? ` x ${formatQuantity(o.quantity)}` : ''}
                                  {o.ratio != null ? ` (${formatPercent(o.ratio)})` : ''}
                                </span>
                              ))}
                            </div>
                          )}

                          {/* Take profit targets (익절) */}
                          {req.takeProfitTargets?.length > 0 && (
                            <div>
                              <span className="text-slate-400 mr-1">익절:</span>
                              {req.takeProfitTargets.map((t, i) => (
                                <span key={i} className="text-red-600 mr-2">
                                  {formatCurrency(t.price, req.targetMarket)}
                                  {t.quantity != null ? ` x ${formatQuantity(t.quantity)}` : ''}
                                  {t.ratio != null ? ` (${formatPercent(t.ratio)})` : ''}
                                </span>
                              ))}
                            </div>
                          )}

                          {/* Stop loss targets (손절) */}
                          {req.stopLossTargets?.length > 0 && (
                            <div>
                              <span className="text-slate-400 mr-1">손절:</span>
                              {req.stopLossTargets.map((t, i) => (
                                <span key={i} className="text-blue-600 mr-2">
                                  {formatCurrency(t.price, req.targetMarket)}
                                  {t.quantity != null ? ` x ${formatQuantity(t.quantity)}` : ''}
                                  {t.ratio != null ? ` (${formatPercent(t.ratio)})` : ''}
                                </span>
                              ))}
                            </div>
                          )}
                        </>
                      )}

                      {/* Sell details */}
                      {!isBuy && (
                        <>
                          {req.sellPrice != null && (
                            <div>
                              <span className="text-slate-400 mr-1">매도가:</span>
                              <span className="font-medium text-slate-700">
                                {formatCurrency(req.sellPrice, req.targetMarket)}
                              </span>
                            </div>
                          )}
                          {req.sellReason && (
                            <div>
                              <span className="text-slate-400 mr-1">매도 사유:</span>
                              <span className="text-slate-700">{req.sellReason}</span>
                            </div>
                          )}
                        </>
                      )}

                      {/* Rejection reason */}
                      {req.rejectionReason && (
                        <div className="mt-1 p-2 bg-rose-50 rounded text-rose-700 text-xs">
                          거절 사유: {req.rejectionReason}
                        </div>
                      )}

                      {/* Memo */}
                      {req.memo && (
                        <div className="mt-1 p-2 bg-slate-100 rounded text-slate-600 text-xs">
                          메모: {req.memo}
                        </div>
                      )}

                      {/* Market info */}
                      {req.targetMarket && (
                        <div className="text-xs text-slate-400">
                          시장: {req.targetMarket}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>
      {confirmModal}
    </FundPageWrapper>
  )
}
