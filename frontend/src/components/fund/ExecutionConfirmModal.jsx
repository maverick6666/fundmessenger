import { useState, useMemo } from 'react'
import { formatPrice, formatQuantity, formatCurrency, formatProfitRate, getProfitLossColor } from '../../lib/format'

const PLAN_TYPE_MAP = {
  BUY:         { label: '매수', badge: 'bg-blue-100 text-blue-800' },
  TAKE_PROFIT: { label: '익절', badge: 'bg-red-100 text-red-800' },
  STOP_LOSS:   { label: '손절', badge: 'bg-emerald-100 text-emerald-800' },
}

export default function ExecutionConfirmModal({
  isOpen, onClose, onConfirm,
  planItem, planType, market, averageBuyPrice, loading,
}) {
  const [executedPrice, setExecutedPrice] = useState('')
  const [executedQuantity, setExecutedQuantity] = useState('')

  // Reset fields when modal opens with new planItem
  const planKey = planItem ? `${planItem.price}-${planItem.quantity}` : ''
  const [prevKey, setPrevKey] = useState('')
  if (planKey && planKey !== prevKey) {
    setPrevKey(planKey)
    setExecutedPrice(planItem.price ?? '')
    setExecutedQuantity(planItem.quantity ?? '')
  }

  const typeInfo = PLAN_TYPE_MAP[planType] || PLAN_TYPE_MAP.BUY

  const price = parseFloat(executedPrice) || 0
  const quantity = parseFloat(executedQuantity) || 0

  const executedAmount = useMemo(() => price * quantity, [price, quantity])

  const showPnl = planType === 'TAKE_PROFIT' || planType === 'STOP_LOSS'
  const pnl = useMemo(() => {
    if (!showPnl || !averageBuyPrice) return 0
    return (price - averageBuyPrice) * quantity
  }, [showPnl, price, averageBuyPrice, quantity])

  const pnlRate = useMemo(() => {
    if (!showPnl || !averageBuyPrice || averageBuyPrice === 0) return 0
    return ((price - averageBuyPrice) / averageBuyPrice) * 100
  }, [showPnl, price, averageBuyPrice])

  if (!isOpen) return null

  const canConfirm = price > 0 && quantity > 0

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full mx-4 p-6" onClick={e => e.stopPropagation()}>
        <h3 className="text-lg font-bold text-slate-900 mb-4">체결 확인</h3>

        {/* Type badge */}
        <div className="mb-4">
          <span className="text-sm text-slate-500 mr-2">타입:</span>
          <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-semibold ${typeInfo.badge}`}>
            {typeInfo.label}
          </span>
        </div>

        {/* Plan info */}
        <div className="bg-slate-50 rounded-lg p-3 mb-4 space-y-1 text-sm">
          <div className="flex justify-between">
            <span className="text-slate-500">계획 가격</span>
            <span className="text-slate-700 font-medium">{formatPrice(planItem?.price, market)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">계획 수량</span>
            <span className="text-slate-700 font-medium">{formatQuantity(planItem?.quantity)}주</span>
          </div>
        </div>

        {/* Actual execution inputs */}
        <div className="grid grid-cols-2 gap-3 mb-4">
          <div>
            <label className="block text-xs text-slate-500 mb-1">체결 가격</label>
            <input
              type="number"
              value={executedPrice}
              onChange={e => setExecutedPrice(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
              placeholder="0"
            />
          </div>
          <div>
            <label className="block text-xs text-slate-500 mb-1">체결 수량</label>
            <input
              type="number"
              value={executedQuantity}
              onChange={e => setExecutedQuantity(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
              placeholder="0"
            />
          </div>
        </div>

        {/* Result preview */}
        <div className="bg-slate-50 rounded-lg p-3 mb-5 space-y-1 text-sm">
          <div className="flex justify-between">
            <span className="text-slate-500">체결 금액</span>
            <span className="text-slate-700 font-medium">{formatCurrency(executedAmount, market)}</span>
          </div>
          {showPnl && (
            <div className="flex justify-between">
              <span className="text-slate-500">실현 P&L</span>
              <span className={`font-medium ${getProfitLossColor(pnl)}`}>
                {pnl >= 0 ? '+' : ''}{formatCurrency(pnl, market)} ({formatProfitRate(pnlRate)})
              </span>
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3">
          <button
            onClick={onClose}
            disabled={loading}
            className="px-4 py-2 text-sm text-slate-500 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors cursor-pointer"
          >
            취소
          </button>
          <button
            onClick={() => canConfirm && onConfirm(price, quantity)}
            disabled={!canConfirm || loading}
            className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition-colors cursor-pointer"
          >
            {loading ? '처리 중...' : '체결 확인'}
          </button>
        </div>
      </div>
    </div>
  )
}
