import { useState, useMemo } from 'react'
import { formatPrice, formatQuantity, formatCurrency, formatProfitRate, getProfitLossColor } from '../../lib/format'

export default function ClosePositionModal({
  isOpen, onClose, onConfirm, position, loading,
}) {
  const [sellPrice, setSellPrice] = useState('')
  const [sellQuantity, setSellQuantity] = useState('')

  // Reset when position changes
  const posKey = position ? `${position.ticker}-${position.totalQuantity}` : ''
  const [prevKey, setPrevKey] = useState('')
  if (posKey && posKey !== prevKey) {
    setPrevKey(posKey)
    setSellPrice('')
    setSellQuantity(position?.totalQuantity ?? '')
  }

  const price = parseFloat(sellPrice) || 0
  const qty = parseFloat(sellQuantity) || 0
  const market = position?.market || 'KRX'
  const avgBuy = position?.averageBuyPrice || 0
  const totalQty = position?.totalQuantity || 0
  const totalBuyAmt = position?.totalBuyAmount || 0
  const existingPnl = position?.realizedProfitLoss || 0

  const sellAmount = useMemo(() => price * qty, [price, qty])
  const newPnl = useMemo(() => (price - avgBuy) * qty, [price, avgBuy, qty])
  const totalPnl = useMemo(() => existingPnl + newPnl, [existingPnl, newPnl])
  const pnlRate = useMemo(() => {
    if (totalBuyAmt <= 0) return 0
    return (newPnl / totalBuyAmt) * 100
  }, [newPnl, totalBuyAmt])

  if (!isOpen || !position) return null

  const canConfirm = price > 0 && qty > 0 && qty <= totalQty

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full mx-4 p-6" onClick={e => e.stopPropagation()}>
        <h3 className="text-lg font-bold text-slate-900 mb-4">포지션 종료</h3>

        {/* Ticker info */}
        <p className="text-sm text-slate-600 mb-4">
          종목: <span className="font-semibold text-slate-900">{position.tickerName}</span>
          <span className="text-slate-400 ml-1">({position.ticker})</span>
        </p>

        {/* Buy info */}
        <div className="bg-blue-50 rounded-lg p-4 mb-4 space-y-1.5 text-sm">
          <div className="flex justify-between">
            <span className="text-slate-500">평균매입가</span>
            <span className="text-slate-700 font-medium">{formatPrice(avgBuy, market)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">보유수량</span>
            <span className="text-slate-700 font-medium">{formatQuantity(totalQty)}주</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">투자금액</span>
            <span className="text-slate-700 font-medium">{formatCurrency(totalBuyAmt, market)}</span>
          </div>
        </div>

        {/* Sell inputs */}
        <div className="bg-red-50 rounded-lg p-4 mb-4 space-y-3">
          <div>
            <label className="block text-xs text-slate-500 mb-1">매도 가격</label>
            <input
              type="number"
              value={sellPrice}
              onChange={e => setSellPrice(e.target.value)}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-400"
              placeholder="매도 가격 입력"
            />
          </div>
          <div>
            <label className="block text-xs text-slate-500 mb-1">
              매도 수량 <span className="text-slate-400">(최대: {formatQuantity(totalQty)}주)</span>
            </label>
            <input
              type="number"
              value={sellQuantity}
              onChange={e => setSellQuantity(e.target.value)}
              max={totalQty}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-400"
              placeholder="0"
            />
            {qty > totalQty && (
              <p className="text-xs text-red-500 mt-1">보유수량을 초과할 수 없습니다</p>
            )}
          </div>
        </div>

        {/* P&L preview */}
        <div className="bg-slate-100 rounded-lg p-4 mb-5 space-y-1.5 text-sm">
          <div className="flex justify-between">
            <span className="text-slate-500">매도 금액</span>
            <span className="text-slate-700 font-medium">{formatCurrency(sellAmount, market)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">실현 손익</span>
            <span className={`font-medium ${getProfitLossColor(newPnl)}`}>
              {newPnl >= 0 ? '+' : ''}{formatCurrency(newPnl, market)} ({formatProfitRate(pnlRate)})
            </span>
          </div>
          {existingPnl !== 0 && (
            <div className="flex justify-between pt-1 border-t border-slate-200">
              <span className="text-slate-400 text-xs">기존 실현손익 포함</span>
              <span className={`text-xs font-medium ${getProfitLossColor(totalPnl)}`}>
                {totalPnl >= 0 ? '+' : ''}{formatCurrency(totalPnl, market)}
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
            onClick={() => canConfirm && onConfirm(price, qty)}
            disabled={!canConfirm || loading}
            className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition-colors cursor-pointer"
          >
            {loading ? '처리 중...' : '포지션 종료'}
          </button>
        </div>
      </div>
    </div>
  )
}
