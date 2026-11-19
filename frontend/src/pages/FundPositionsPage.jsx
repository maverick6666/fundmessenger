import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams, useOutletContext } from 'react-router-dom'
import {
  ArrowUpDown, Plus, X, Search, Loader2, ChevronDown, ChevronUp,
  BarChart3, Clock,
} from 'lucide-react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import useToastStore from '../store/toastStore'
import { useGuardedNavigate } from '../components/UnsavedChangesProvider'
import FundPageWrapper from '../components/fund/FundPageWrapper'
import { MiniTargetProgressBar } from '../components/fund/ProfitProgressBar'
import { MiniChart } from '../components/fund/MiniChart'
import { useStockSearch } from '../hooks/useStockSearch'
import { MARKETS, PRICE_REFRESH_INTERVAL_MS, CHART_CANDLE_LIMIT } from '../lib/fundConstants'
import {
  formatNumber, formatPrice, formatQuantity, formatProfitRate, formatHours,
  calcHoldingHours, getProfitLossColor, formatCurrency, formatTime,
} from '../lib/format'

const STATUS_TABS = [
  { value: 'all', label: '전체' },
  { value: 'OPEN', label: '활성' },
  { value: 'CLOSED', label: '종료' },
]

const SORT_OPTIONS = [
  { value: 'newest', label: '최신순' },
  { value: 'profitRate', label: '수익률순' },
  { value: 'ticker', label: '종목명순' },
]

export default function FundPositionsPage() {
  const { teamId } = useParams()
  const { clubInfo } = useOutletContext()
  const navigate = useGuardedNavigate()
  const addToast = useToastStore(s => s.addToast)
  const clubId = clubInfo?.clubId
  const tid = Number(teamId)

  const myTeamEntry = clubInfo?.myTeams?.find(t => t.teamId === tid)
  const canManage = myTeamEntry?.teamRole === 'LEADER' || myTeamEntry?.teamRole === 'MANAGER'

  const [statusFilter, setStatusFilter] = useState('OPEN')
  const [sort, setSort] = useState('newest')
  const [positions, setPositions] = useState([])
  const [loading, setLoading] = useState(true)

  // Real-time prices
  const [priceMap, setPriceMap] = useState({})
  const priceTimerRef = useRef(null)

  // Inline editing
  const [editingField, setEditingField] = useState(null) // { posId, field }
  const [editValue, setEditValue] = useState('')

  // Inline chart
  const [chartPosId, setChartPosId] = useState(null)
  const [chartData, setChartData] = useState(null)
  const [chartLoading, setChartLoading] = useState(false)

  // --- Add form state ---
  const [showAddForm, setShowAddForm] = useState(false)
  const [market, setMarket] = useState('KOSPI')
  const [selectedStock, setSelectedStock] = useState(null)
  const [addBuyPrice, setAddBuyPrice] = useState('')
  const [addQuantity, setAddQuantity] = useState('')
  const [creating, setCreating] = useState(false)
  const [previewCandles, setPreviewCandles] = useState(null)
  const [previewPrice, setPreviewPrice] = useState(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const searchContainerRef = useRef(null)

  const {
    searchQuery, setSearchQuery,
    searchResults, searchLoading,
    showDropdown, setShowDropdown,
    dropdownRef, clearSearch,
  } = useStockSearch(clubId, market)

  // --- Fetch positions ---
  const fetchPositions = useCallback(() => {
    if (!clubId) return
    setLoading(true)
    const params = statusFilter === 'all' ? '' : `?status=${statusFilter}`
    api.get(`/clubs/${clubId}/fund/teams/${tid}/positions${params}`)
      .then(data => setPositions(Array.isArray(data) ? data : []))
      .catch(e => handleApiError(e, '포지션 조회'))
      .finally(() => setLoading(false))
  }, [clubId, tid, statusFilter])

  useEffect(() => { fetchPositions() }, [fetchPositions])

  // --- Real-time price fetch ---
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
        } catch { /* ignore */ }
      })
    )
    setPriceMap(prev => ({ ...prev, ...results }))
  }, [clubId, positions])

  useEffect(() => {
    if (statusFilter !== 'CLOSED' && positions.length > 0) {
      fetchPrices()
      priceTimerRef.current = setInterval(fetchPrices, PRICE_REFRESH_INTERVAL_MS)
      return () => clearInterval(priceTimerRef.current)
    }
  }, [fetchPrices, positions.length, statusFilter])

  // --- Sort ---
  let items = [...positions]
  if (sort === 'profitRate') {
    items.sort((a, b) => {
      const aRate = getUnrealizedRate(a)
      const bRate = getUnrealizedRate(b)
      return (bRate ?? -Infinity) - (aRate ?? -Infinity)
    })
  } else if (sort === 'ticker') {
    items.sort((a, b) => (a.tickerName || a.ticker).localeCompare(b.tickerName || b.ticker))
  }

  function getUnrealizedRate(pos) {
    const quote = priceMap[pos.id]
    if (quote?.price && pos.totalQuantity && pos.totalBuyAmount) {
      const evalAmt = quote.price * pos.totalQuantity
      return ((evalAmt - pos.totalBuyAmount) / pos.totalBuyAmount) * 100
    }
    return pos.profitRate != null ? Number(pos.profitRate) * 100 : null
  }

  const nextSort = () => {
    const order = ['newest', 'profitRate', 'ticker']
    setSort(order[(order.indexOf(sort) + 1) % order.length])
  }

  // --- Stock selection ---
  function handleSelectStock(stock) {
    setSelectedStock(stock)
    setSearchQuery(stock.stockName || stock.name || stock.stockCode || stock.tickerName)
    setShowDropdown(false)
    // 차트+현재가 미리보기 fetch
    fetchStockPreview(stock)
  }

  async function fetchStockPreview(stock) {
    const ticker = stock.stockCode || stock.ticker
    const mkt = stock.market || market
    setPreviewLoading(true)
    setPreviewCandles(null)
    setPreviewPrice(null)
    try {
      const [chartData, priceData] = await Promise.all([
        api.get(`/clubs/${clubId}/fund/prices/candles?ticker=${ticker}&market=${mkt}&period=daily`).catch(() => null),
        api.get(`/clubs/${clubId}/fund/prices/quote?ticker=${ticker}&market=${mkt}`).catch(() => null),
      ])
      if (chartData?.prices) {
        setPreviewCandles(chartData.prices.map(p => ({
          time: p.date, open: p.open, high: p.high, low: p.low, close: p.close, volume: p.volume,
        })))
      }
      if (priceData?.currentPrice) {
        setPreviewPrice(priceData)
        if (!addBuyPrice) setAddBuyPrice(String(priceData.currentPrice))
      }
    } catch {}
    finally { setPreviewLoading(false) }
  }

  function handleMarketChange(newMarket) {
    setMarket(newMarket)
    clearSearch()
    setSelectedStock(null)
  }

  function handleClearSearch() {
    clearSearch()
    setSelectedStock(null)
  }

  function toggleAddForm() {
    if (showAddForm) handleClearSearch()
    setShowAddForm(prev => !prev)
  }

  // --- Create position ---
  async function handleCreatePosition() {
    if (!selectedStock || creating) return
    if (!addBuyPrice || !addQuantity) {
      addToast({ type: 'WARNING', title: '매수가와 수량을 입력하세요' })
      return
    }
    setCreating(true)
    try {
      const ticker = selectedStock.stockCode || selectedStock.ticker
      const tickerName = selectedStock.stockName || selectedStock.name || selectedStock.tickerName
      await api.post(`/clubs/${clubId}/fund/teams/${tid}/positions`, {
        ticker,
        tickerName,
        market: selectedStock.market || market,
        buyPrice: parseFloat(addBuyPrice),
        quantity: parseFloat(addQuantity),
      })
      addToast({ type: 'SUCCESS', title: '포지션 생성', body: `${tickerName} 포지션이 생성되었습니다.` })
      handleClearSearch()
      setAddBuyPrice('')
      setAddQuantity('')
      setShowAddForm(false)
      fetchPositions()
    } catch (e) {
      handleApiError(e, '포지션 생성')
    } finally {
      setCreating(false)
    }
  }

  // --- Click outside to close dropdown ---
  useEffect(() => {
    function handleClickOutside(e) {
      if (searchContainerRef.current && !searchContainerRef.current.contains(e.target)) {
        setShowDropdown(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [setShowDropdown])

  // --- Inline editing ---
  function startEdit(posId, field, currentValue) {
    setEditingField({ posId, field })
    setEditValue(String(currentValue ?? ''))
  }

  async function commitEdit(posId) {
    if (!editingField) return
    const { field } = editingField
    const numVal = Number(editValue)
    if (isNaN(numVal) || numVal < 0) {
      addToast({ type: 'ERROR', title: '올바른 숫자를 입력해주세요' })
      setEditingField(null)
      return
    }
    try {
      await api.patch(`/clubs/${clubId}/fund/teams/${tid}/positions/${posId}`, { [field]: numVal })
      addToast({ type: 'SUCCESS', title: '수정 완료' })
      fetchPositions()
    } catch (e) {
      handleApiError(e, '포지션 수정')
    }
    setEditingField(null)
  }

  function handleEditKeyDown(e, posId) {
    if (e.key === 'Enter') commitEdit(posId)
    if (e.key === 'Escape') setEditingField(null)
  }

  // --- Inline chart toggle ---
  async function toggleChart(posId, ticker, posMarket) {
    if (chartPosId === posId) {
      setChartPosId(null)
      setChartData(null)
      return
    }
    setChartPosId(posId)
    setChartLoading(true)
    try {
      const data = await api.get(`/clubs/${clubId}/fund/prices/candles?ticker=${ticker}&market=${posMarket}&timeframe=1d&limit=${CHART_CANDLE_LIMIT}`)
      setChartData(data)
    } catch {
      setChartData(null)
    } finally {
      setChartLoading(false)
    }
  }

  // --- Market badge color ---
  function marketBadgeClass(m) {
    if (m === 'KOSPI' || m === 'KOSDAQ') return 'bg-blue-50 text-blue-700'
    if (m === 'CRYPTO') return 'bg-orange-50 text-orange-700'
    return 'bg-green-50 text-green-700'
  }

  const searchPlaceholder =
    market === 'CRYPTO' ? '비트코인, BTC 등'
    : market === 'NASDAQ' || market === 'NYSE' ? 'Apple, AAPL 등'
    : '삼성전자, 005930 등'

  return (
    <FundPageWrapper teamId={tid}>
      <div className="space-y-4">
        {/* Main card */}
        <div className="bg-white rounded-2xl border border-slate-200">
          {/* Header */}
          <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
            <h2 className="text-sm font-bold text-slate-800 fund-serif">포지션 관리</h2>
            <div className="flex items-center gap-2">
              <button onClick={nextSort}
                className="flex items-center gap-1 px-2 py-1 text-xs text-slate-500 hover:text-slate-700 cursor-pointer">
                <ArrowUpDown size={12} />
                {SORT_OPTIONS.find(s => s.value === sort)?.label}
              </button>
              {canManage && (
                <button onClick={toggleAddForm}
                  className={`flex items-center gap-1 px-3 py-1.5 text-xs font-medium rounded-lg transition-colors cursor-pointer
                    ${showAddForm
                      ? 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                      : 'bg-slate-800 text-white hover:bg-slate-700'}`}>
                  {showAddForm ? <ChevronUp size={14} /> : <Plus size={14} />}
                  {showAddForm ? '닫기' : '새 포지션'}
                </button>
              )}
            </div>
          </div>

          {/* Add Position Form */}
          {showAddForm && canManage && (
            <div className="px-5 py-4 border-b border-slate-100 bg-slate-50/60">
              <div className="flex flex-wrap gap-3 items-end">
                {/* Market selector */}
                <div className="min-w-[100px]">
                  <label className="block text-xs font-medium text-slate-500 mb-1">시장</label>
                  <select value={market} onChange={e => handleMarketChange(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg bg-white
                      focus:outline-none focus:ring-2 focus:ring-slate-300 cursor-pointer">
                    {MARKETS.map(m => (
                      <option key={m.value} value={m.value}>{m.label}</option>
                    ))}
                  </select>
                </div>

                {/* Stock search */}
                <div className="flex-1 min-w-[240px] relative" ref={searchContainerRef}>
                  <label className="block text-xs font-medium text-slate-500 mb-1">종목 검색</label>
                  <div className="relative">
                    <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input type="text" value={searchQuery}
                      onChange={e => { setSearchQuery(e.target.value); setSelectedStock(null) }}
                      onFocus={() => searchResults.length > 0 && setShowDropdown(true)}
                      placeholder={searchPlaceholder}
                      className="w-full pl-9 pr-10 py-2 text-sm border border-slate-200 rounded-lg bg-white
                        placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-slate-300" />
                    {searchLoading && (
                      <Loader2 size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 animate-spin" />
                    )}
                    {searchQuery && !searchLoading && (
                      <button onClick={handleClearSearch}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer">
                        <X size={14} />
                      </button>
                    )}
                  </div>

                  {/* Dropdown */}
                  {showDropdown && searchResults.length > 0 && (
                    <div ref={dropdownRef}
                      className="absolute z-50 w-full mt-1 bg-white border border-slate-200 rounded-lg shadow-lg max-h-64 overflow-y-auto">
                      {searchResults.map((stock, idx) => (
                        <button key={`${stock.stockCode || stock.ticker}-${idx}`} type="button"
                          onClick={() => handleSelectStock(stock)}
                          className="w-full px-4 py-2.5 text-left hover:bg-slate-50 border-b border-slate-50 last:border-0
                            transition-colors cursor-pointer">
                          <div className="flex items-center justify-between">
                            <div>
                              <span className="text-sm font-medium text-slate-800">{stock.stockName || stock.name || stock.stockCode || stock.tickerName}</span>
                              <span className="ml-2 text-xs text-slate-400">{stock.stockCode || stock.ticker}</span>
                            </div>
                            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${marketBadgeClass(stock.market)}`}>
                              {stock.market}
                            </span>
                          </div>
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                {/* Selected stock info + chart preview + price/qty inputs */}
                {selectedStock && (
                  <div className="space-y-3">
                    {/* Stock badge + price info */}
                    <div className="flex items-center gap-3 flex-wrap">
                      <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-white border border-slate-200">
                        <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${marketBadgeClass(selectedStock.market || market)}`}>
                          {selectedStock.market || market}
                        </span>
                        <span className="text-sm font-semibold text-slate-800">{selectedStock.stockCode || selectedStock.ticker}</span>
                        <span className="text-xs text-slate-500">{selectedStock.stockName || selectedStock.name || selectedStock.tickerName}</span>
                      </div>
                      {previewPrice && (
                        <div className="flex items-center gap-2 text-sm">
                          <span className="font-semibold tabular-nums">{formatPrice(previewPrice.currentPrice, selectedStock.market || market)}</span>
                          {previewPrice.changeRate != null && (
                            <span className={`text-xs font-medium ${previewPrice.changeRate >= 0 ? 'text-red-600' : 'text-blue-600'}`}>
                              {previewPrice.changeRate >= 0 ? '+' : ''}{Number(previewPrice.changeRate).toFixed(2)}%
                            </span>
                          )}
                        </div>
                      )}
                      {previewLoading && <Loader2 size={14} className="animate-spin text-slate-300" />}
                    </div>

                    {/* Chart preview */}
                    {previewCandles && previewCandles.length > 0 && (
                      <div className="h-40 rounded-lg border border-slate-200 shadow-sm overflow-hidden">
                        <MiniChart candles={previewCandles} height={160} />
                      </div>
                    )}

                    {/* Price + Qty inputs + Submit */}
                    <div className="flex items-center gap-3 flex-wrap">
                      <input type="number" step="any" placeholder="매수가" value={addBuyPrice}
                        onChange={e => setAddBuyPrice(e.target.value)}
                        className="w-32 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-indigo-400" />
                      <input type="number" step="any" placeholder="수량" value={addQuantity}
                        onChange={e => setAddQuantity(e.target.value)}
                        className="w-28 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-indigo-400" />
                      {addBuyPrice && addQuantity && (
                        <span className="text-xs text-slate-400 tabular-nums">
                          = {formatCurrency(parseFloat(addBuyPrice) * parseFloat(addQuantity), selectedStock.market || market)}
                        </span>
                      )}
                      <button onClick={handleCreatePosition} disabled={creating}
                        className="px-4 py-2 text-sm font-medium bg-slate-800 text-white rounded-lg
                          hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer
                          flex items-center gap-1.5 transition-colors">
                        {creating ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
                        추가
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Status filter tabs */}
          <div className="flex items-center px-5 py-2 border-b border-slate-100 gap-1">
            {STATUS_TABS.map(tab => (
              <button key={tab.value} onClick={() => setStatusFilter(tab.value)}
                className={`px-2.5 py-0.5 text-xs rounded-full font-medium transition-colors cursor-pointer
                  ${statusFilter === tab.value
                    ? 'bg-slate-700 text-white'
                    : 'bg-slate-100 text-slate-500 hover:bg-slate-200'}`}>
                {tab.label}
              </button>
            ))}
            <span className="ml-auto text-xs text-slate-400">{items.length}건</span>
          </div>

          {/* Position Cards */}
          {loading ? (
            <div className="p-5 space-y-3">
              {[...Array(4)].map((_, i) => <div key={i} className="animate-pulse bg-slate-100 rounded-xl h-32" />)}
            </div>
          ) : items.length === 0 ? (
            <p className="text-sm text-slate-400 py-12 text-center">포지션이 없습니다</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {items.map(pos => {
                const quote = priceMap[pos.id]
                const currentPrice = quote?.price
                const changeRate = quote?.changeRate != null ? Number(quote.changeRate) * 100 : null
                const buyAmount = pos.totalBuyAmount ?? 0
                const evalAmount = currentPrice && pos.totalQuantity ? currentPrice * pos.totalQuantity : null
                const unrealized = evalAmount != null ? evalAmount - buyAmount : null
                const unrealizedRate = buyAmount > 0 && unrealized != null ? (unrealized / buyAmount) * 100 : null
                const holdingHrs = pos.holdingPeriodHours ?? calcHoldingHours(pos.openedAt)
                const isOpen = pos.status === 'OPEN'

                // Remaining request counts
                const buyRemaining = pos.buyRequestsRemaining ?? pos.buyTargetsRemaining ?? null
                const tpRemaining = pos.takeProfitRemaining ?? (pos.takeProfitTargets?.filter(t => !t.completed)?.length ?? null)
                const slRemaining = pos.stopLossRemaining ?? (pos.stopLossTargets?.filter(t => !t.completed)?.length ?? null)

                const isEditingAvg = editingField?.posId === pos.id && editingField?.field === 'averageBuyPrice'
                const isEditingQty = editingField?.posId === pos.id && editingField?.field === 'totalQuantity'

                return (
                  <div key={pos.id}>
                    <div
                      className="px-5 py-3.5 hover:bg-slate-50/50 transition-colors cursor-pointer"
                      onClick={() => navigate(`/club/fund/${tid}/positions/${pos.id}`)}
                    >
                      {/* Row 1: Ticker + Market + Status */}
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-bold text-slate-800">{pos.tickerName || pos.ticker}</span>
                          <span className="text-xs text-slate-400">({pos.ticker})</span>
                          <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${marketBadgeClass(pos.market)}`}>
                            {pos.market}
                          </span>
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium
                            ${isOpen ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                            {isOpen ? '진행중' : '종료'}
                          </span>
                        </div>
                        <div className="flex items-center gap-1.5">
                          {/* Chart toggle button */}
                          <button
                            onClick={(e) => { e.stopPropagation(); toggleChart(pos.id, pos.ticker, pos.market) }}
                            className={`p-1 rounded transition-colors cursor-pointer ${
                              chartPosId === pos.id ? 'bg-slate-200 text-slate-700' : 'text-slate-400 hover:text-slate-600'
                            }`}
                            title="차트 보기"
                          >
                            <BarChart3 size={14} />
                          </button>
                        </div>
                      </div>

                      {/* Row 2: Buy info */}
                      <div className="flex flex-wrap gap-x-5 gap-y-1 text-xs text-slate-500 mb-1.5">
                        <span>
                          평균매입가:{' '}
                          {isEditingAvg ? (
                            <input
                              type="number"
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              onBlur={() => commitEdit(pos.id)}
                              onKeyDown={e => handleEditKeyDown(e, pos.id)}
                              onClick={e => e.stopPropagation()}
                              autoFocus
                              className="w-24 px-1 py-0.5 text-xs border border-slate-300 rounded focus:outline-none focus:ring-1 focus:ring-slate-400"
                            />
                          ) : (
                            <span
                              className="font-medium text-slate-700 cursor-text"
                              onDoubleClick={(e) => {
                                e.stopPropagation()
                                if (canManage) startEdit(pos.id, 'averageBuyPrice', pos.averageBuyPrice)
                              }}
                              title={canManage ? '더블클릭으로 수정' : undefined}
                            >
                              {formatPrice(pos.averageBuyPrice, pos.market)}
                            </span>
                          )}
                        </span>
                        <span>
                          수량:{' '}
                          {isEditingQty ? (
                            <input
                              type="number"
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              onBlur={() => commitEdit(pos.id)}
                              onKeyDown={e => handleEditKeyDown(e, pos.id)}
                              onClick={e => e.stopPropagation()}
                              autoFocus
                              className="w-20 px-1 py-0.5 text-xs border border-slate-300 rounded focus:outline-none focus:ring-1 focus:ring-slate-400"
                            />
                          ) : (
                            <span
                              className="font-medium text-slate-700 cursor-text"
                              onDoubleClick={(e) => {
                                e.stopPropagation()
                                if (canManage) startEdit(pos.id, 'totalQuantity', pos.totalQuantity)
                              }}
                              title={canManage ? '더블클릭으로 수정' : undefined}
                            >
                              {formatQuantity(pos.totalQuantity)}주
                            </span>
                          )}
                        </span>
                        <span>
                          투자금:{' '}
                          <span className="font-medium text-slate-700">
                            {buyAmount ? formatCurrency(buyAmount, pos.market) : '-'}
                          </span>
                        </span>
                      </div>

                      {/* Row 3: Current price (real-time) */}
                      {isOpen && currentPrice != null && (
                        <div className="flex items-center gap-3 mb-1.5">
                          <span className="text-xs text-slate-500">
                            현재가:{' '}
                            <span className="font-medium text-slate-700">{formatPrice(currentPrice, pos.market)}</span>
                            {changeRate != null && (
                              <span className={`ml-1 ${changeRate > 0 ? 'text-rose-500' : changeRate < 0 ? 'text-blue-500' : 'text-slate-400'}`}>
                                ({changeRate > 0 ? '\u25B2' : changeRate < 0 ? '\u25BC' : ''}{Math.abs(changeRate).toFixed(1)}%)
                              </span>
                            )}
                          </span>
                        </div>
                      )}

                      {/* Row 4: MiniTargetProgressBar */}
                      {isOpen && (
                        <div className="mb-1.5" onClick={e => e.stopPropagation()}>
                          <MiniTargetProgressBar
                            currentPrice={currentPrice}
                            averagePrice={pos.averageBuyPrice}
                            takeProfitTargets={pos.takeProfitTargets}
                            stopLossTargets={pos.stopLossTargets}
                            size="sm"
                          />
                        </div>
                      )}

                      {/* Row 5: Evaluation + Unrealized */}
                      {isOpen && (
                        <div className="flex flex-wrap gap-x-5 gap-y-1 text-xs mb-1.5">
                          {evalAmount != null && (
                            <span className="text-slate-500">
                              평가금액:{' '}
                              <span className="font-medium text-slate-700">{formatCurrency(evalAmount, pos.market)}</span>
                            </span>
                          )}
                          {unrealized != null && (
                            <span className={`font-semibold ${getProfitLossColor(unrealized)}`}>
                              미실현: {unrealized >= 0 ? '+' : ''}{formatCurrency(unrealized, pos.market)}
                              {unrealizedRate != null && ` (${formatProfitRate(unrealizedRate)})`}
                            </span>
                          )}
                        </div>
                      )}

                      {/* Row 6: Holding period + remaining targets + opener */}
                      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-400">
                        <span className="flex items-center gap-1">
                          <Clock size={10} />
                          보유기간: {formatHours(holdingHrs)}
                        </span>
                        {(buyRemaining != null || tpRemaining != null || slRemaining != null) && (
                          <span>
                            잔여:
                            {buyRemaining != null && ` 매수 ${buyRemaining}`}
                            {tpRemaining != null && ` | 익절 ${tpRemaining}`}
                            {slRemaining != null && ` | 손절 ${slRemaining}`}
                          </span>
                        )}
                        {pos.openerName && (
                          <span>개시: {pos.openerName}</span>
                        )}
                        {pos.openedAt && (
                          <span>{formatTime(pos.openedAt)}</span>
                        )}
                        {pos.published && (
                          <span className="flex items-center gap-0.5">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block" />
                            공개
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Inline Chart */}
                    {chartPosId === pos.id && (
                      <div className="px-5 pb-4 border-t border-slate-50">
                        {chartLoading ? (
                          <div className="flex items-center justify-center py-8">
                            <Loader2 size={20} className="text-slate-400 animate-spin" />
                          </div>
                        ) : chartData ? (
                          <div className="mt-2">
                            <MiniChart chartData={chartData} height={180} />
                          </div>
                        ) : (
                          <p className="text-xs text-slate-400 py-6 text-center">차트 데이터를 불러올 수 없습니다</p>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </FundPageWrapper>
  )
}
