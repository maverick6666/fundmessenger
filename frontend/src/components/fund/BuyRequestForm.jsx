import { useState, useEffect, useRef } from 'react'
import api from '../../lib/api'
import { useStockSearch } from '../../hooks/useStockSearch'
import { MARKETS, MAX_TARGETS } from '../../lib/fundConstants'
import { formatNumber, formatPrice } from '../../lib/format'
import handleApiError from '../../lib/handleApiError'
import useToastStore from '../../store/toastStore'

/* ─────────────────────────────────────────────
   BuyRequestForm — 매수 요청 생성 폼
   ───────────────────────────────────────────── */

export function BuyRequestForm({ clubId, teamId, onSuccess }) {
  const addToast = useToastStore((s) => s.addToast)

  const [market, setMarket] = useState('KOSPI')
  const [selectedStock, setSelectedStock] = useState(null) // { ticker, name, market }
  const [orderAmount, setOrderAmount] = useState('')
  const [buyPrice, setBuyPrice] = useState('')
  const [orderQuantity, setOrderQuantity] = useState('')
  const [targetRatio, setTargetRatio] = useState('')
  const [takeProfitTargets, setTakeProfitTargets] = useState([{ price: '', quantity: '' }])
  const [stopLossTargets, setStopLossTargets] = useState([{ price: '', quantity: '' }])
  const [memo, setMemo] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const {
    searchQuery, setSearchQuery,
    searchResults, searchLoading,
    showDropdown, setShowDropdown,
    dropdownRef, clearSearch,
  } = useStockSearch(clubId, market)

  const wrapperRef = useRef(null)

  // 드롭다운 외부 클릭 닫기
  useEffect(() => {
    const handler = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) {
        setShowDropdown(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [setShowDropdown])

  // 시장 변경 시 선택 초기화
  const handleMarketChange = (m) => {
    setMarket(m)
    setSelectedStock(null)
    clearSearch()
  }

  const handleSelectStock = (stock) => {
    setSelectedStock({ ticker: stock.ticker, name: stock.name, market: stock.market })
    setSearchQuery(stock.ticker + ' ' + stock.name)
    setShowDropdown(false)
  }

  // 예상 거래대금
  const totalAmount =
    orderQuantity && buyPrice
      ? parseFloat(orderQuantity) * parseFloat(buyPrice)
      : null

  // ── 타겟 행 관리 ──
  const updateTarget = (list, setList, idx, field, value) => {
    const next = [...list]
    next[idx] = { ...next[idx], [field]: value }
    setList(next)
  }
  const addTarget = (list, setList) => {
    if (list.length >= MAX_TARGETS) return
    setList([...list, { price: '', quantity: orderQuantity || '' }])
  }
  const removeTarget = (list, setList, idx) => {
    setList(list.filter((_, i) => i !== idx))
  }

  // ── 제출 ──
  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!selectedStock) {
      addToast({ type: 'ERROR', title: '오류', body: '종목을 검색하여 선택해주세요.' })
      return
    }
    if (!orderAmount && !orderQuantity) {
      addToast({ type: 'ERROR', title: '오류', body: '주문금액 또는 주문수량을 입력해주세요.' })
      return
    }

    setSubmitting(true)
    try {
      const cleanTargets = (arr) => {
        const filtered = arr
          .filter((t) => t.price && !isNaN(parseFloat(t.price)))
          .map((t) => ({
            price: parseFloat(t.price),
            quantity: t.quantity ? parseFloat(t.quantity) : null,
          }))
        return filtered.length > 0 ? filtered : null
      }

      await api.post(`/clubs/${clubId}/fund/teams/${teamId}/requests/buy`, {
        ticker: selectedStock.ticker,
        tickerName: selectedStock.name,
        market: selectedStock.market || market,
        orderAmount: orderAmount ? parseFloat(orderAmount) : null,
        buyPrice: buyPrice ? parseFloat(buyPrice) : null,
        orderQuantity: orderQuantity ? parseFloat(orderQuantity) : null,
        targetRatio: targetRatio ? parseFloat(targetRatio) / 100 : null,
        takeProfitTargets: cleanTargets(takeProfitTargets),
        stopLossTargets: cleanTargets(stopLossTargets),
        memo: memo || null,
      })
      addToast({ type: 'SUCCESS', title: '매수 요청', body: '매수 요청이 등록되었습니다.' })
      onSuccess?.()
    } catch (err) {
      handleApiError(err, '매수 요청')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      {/* 시장 선택 */}
      <div>
        <label className="block text-sm font-medium text-slate-700 mb-2">시장</label>
        <div className="flex flex-wrap gap-2">
          {MARKETS.map((m) => (
            <button
              key={m.value}
              type="button"
              onClick={() => handleMarketChange(m.value)}
              className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                market === m.value
                  ? 'bg-indigo-600 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {m.label}
            </button>
          ))}
        </div>
      </div>

      {/* 종목 검색 */}
      <div ref={wrapperRef} className="relative">
        <label className="block text-sm font-medium text-slate-700 mb-1">종목 검색</label>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => { setSearchQuery(e.target.value); setSelectedStock(null) }}
          placeholder="종목명 또는 코드 입력"
          className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        {searchLoading && (
          <span className="absolute right-3 top-8 text-xs text-slate-400">검색중...</span>
        )}
        {selectedStock && (
          <p className="mt-1 text-xs text-indigo-600">
            {selectedStock.ticker} &middot; {selectedStock.name} ({selectedStock.market})
          </p>
        )}
        {showDropdown && searchResults.length > 0 && (
          <ul
            ref={dropdownRef}
            className="absolute z-20 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg max-h-48 overflow-y-auto"
          >
            {searchResults.map((s) => (
              <li
                key={s.id || s.ticker}
                onClick={() => handleSelectStock(s)}
                className="px-3 py-2 hover:bg-indigo-50 cursor-pointer text-sm flex justify-between"
              >
                <span className="font-medium text-slate-900">{s.name}</span>
                <span className="text-slate-400">{s.ticker} &middot; {s.market}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* 주문 금액 / 수량 / 매수가 */}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs text-slate-500 mb-1">주문금액</label>
          <input type="number" step="any" placeholder="총 금액"
            value={orderAmount} onChange={(e) => setOrderAmount(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">매수가 (선택)</label>
          <input type="number" step="any" placeholder="희망 매수가"
            value={buyPrice} onChange={(e) => setBuyPrice(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">주문수량 (선택)</label>
          <input type="number" step="any" placeholder="수량"
            value={orderQuantity} onChange={(e) => setOrderQuantity(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">목표비중 % (선택)</label>
          <input type="number" step="any" placeholder="예: 5"
            value={targetRatio} onChange={(e) => setTargetRatio(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
        </div>
      </div>

      {/* 예상 거래대금 */}
      {totalAmount != null && (
        <div className="p-3 bg-slate-50 rounded-lg flex justify-between items-center">
          <span className="text-sm text-slate-500">예상 거래대금</span>
          <span className="text-base font-bold text-slate-900">
            {formatPrice(totalAmount, selectedStock?.market || market)}
          </span>
        </div>
      )}

      {/* 익절 목표 */}
      <TargetSection
        label="익절 목표"
        targets={takeProfitTargets}
        setTargets={setTakeProfitTargets}
        orderQuantity={orderQuantity}
        updateTarget={updateTarget}
        addTarget={addTarget}
        removeTarget={removeTarget}
        addLabel="+ 익절 추가"
      />

      {/* 손절 목표 */}
      <TargetSection
        label="손절 목표"
        targets={stopLossTargets}
        setTargets={setStopLossTargets}
        orderQuantity={orderQuantity}
        updateTarget={updateTarget}
        addTarget={addTarget}
        removeTarget={removeTarget}
        addLabel="+ 손절 추가"
      />

      {/* 메모 */}
      <div>
        <label className="block text-xs text-slate-500 mb-1">메모 (선택)</label>
        <textarea
          rows={2}
          placeholder="매수 이유, 차트 분석 등..."
          value={memo}
          onChange={(e) => setMemo(e.target.value)}
          className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
        />
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="w-full py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-lg text-sm font-semibold disabled:opacity-50 transition-colors"
      >
        {submitting ? '요청 중...' : '매수 요청'}
      </button>
    </form>
  )
}

/* ─────────────────────────────────────────────
   TargetSection — 익절/손절 행 공용 컴포넌트
   ───────────────────────────────────────────── */

function TargetSection({ label, targets, setTargets, orderQuantity, updateTarget, addTarget, removeTarget, addLabel }) {
  return (
    <div>
      <label className="block text-sm font-medium text-slate-700 mb-2">{label}</label>
      {targets.map((t, idx) => (
        <div key={idx} className="flex gap-2 mb-2 items-center">
          <input
            type="number" step="any" placeholder="목표가"
            value={t.price}
            onChange={(e) => updateTarget(targets, setTargets, idx, 'price', e.target.value)}
            className="flex-1 px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <input
            type="number" step="any" placeholder="수량"
            value={t.quantity}
            onChange={(e) => updateTarget(targets, setTargets, idx, 'quantity', e.target.value)}
            className="w-24 px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          {targets.length > 1 && (
            <button
              type="button"
              onClick={() => removeTarget(targets, setTargets, idx)}
              className="text-red-400 hover:text-red-600 px-1.5 flex-shrink-0 text-lg leading-none"
            >
              &times;
            </button>
          )}
        </div>
      ))}
      {targets.length < MAX_TARGETS && (
        <button
          type="button"
          onClick={() => addTarget(targets, setTargets)}
          className="text-sm text-indigo-600 hover:text-indigo-700 font-medium"
        >
          {addLabel}
        </button>
      )}
    </div>
  )
}

/* ─────────────────────────────────────────────
   SellRequestForm — 매도 요청 생성 폼
   ───────────────────────────────────────────── */

export function SellRequestForm({ clubId, teamId, positionId, position, onSuccess }) {
  const addToast = useToastStore((s) => s.addToast)

  const [sellQuantity, setSellQuantity] = useState('')
  const [sellPrice, setSellPrice] = useState('')
  const [sellReason, setSellReason] = useState('')
  const [memo, setMemo] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!sellQuantity) {
      addToast({ type: 'ERROR', title: '오류', body: '매도 수량을 입력해주세요.' })
      return
    }

    setSubmitting(true)
    try {
      await api.post(`/clubs/${clubId}/fund/teams/${teamId}/requests/sell`, {
        positionId,
        sellQuantity: parseFloat(sellQuantity),
        sellPrice: sellPrice ? parseFloat(sellPrice) : null,
        sellReason: sellReason || null,
        memo: memo || null,
      })
      addToast({ type: 'SUCCESS', title: '매도 요청', body: '매도 요청이 등록되었습니다.' })
      onSuccess?.()
    } catch (err) {
      handleApiError(err, '매도 요청')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      {/* 포지션 정보 */}
      {position && (
        <div className="p-3 bg-slate-50 rounded-lg text-sm space-y-1">
          <div className="flex justify-between">
            <span className="text-slate-500">종목</span>
            <span className="font-medium text-slate-900">{position.tickerName || position.ticker}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">보유수량</span>
            <span className="font-medium text-slate-900">{formatNumber(position.quantity)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">평단가</span>
            <span className="font-medium text-slate-900">
              {formatPrice(position.averagePrice, position.market)}
            </span>
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs text-slate-500 mb-1">매도 수량</label>
          <input
            type="number" step="any" required
            placeholder={position ? `최대 ${formatNumber(position.quantity)}` : '수량'}
            value={sellQuantity}
            onChange={(e) => setSellQuantity(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
        <div>
          <label className="block text-xs text-slate-500 mb-1">매도가 (선택)</label>
          <input
            type="number" step="any"
            placeholder="희망 매도가"
            value={sellPrice}
            onChange={(e) => setSellPrice(e.target.value)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
      </div>

      <div>
        <label className="block text-xs text-slate-500 mb-1">매도 사유</label>
        <input
          type="text"
          placeholder="예: 목표가 도달, 손절"
          value={sellReason}
          onChange={(e) => setSellReason(e.target.value)}
          className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>

      <div>
        <label className="block text-xs text-slate-500 mb-1">메모 (선택)</label>
        <textarea
          rows={2}
          placeholder="추가 메모..."
          value={memo}
          onChange={(e) => setMemo(e.target.value)}
          className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
        />
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="w-full py-2.5 bg-blue-600 hover:bg-primary-hover text-white rounded-lg text-sm font-semibold disabled:opacity-50 transition-colors"
      >
        {submitting ? '요청 중...' : '매도 요청'}
      </button>
    </form>
  )
}
