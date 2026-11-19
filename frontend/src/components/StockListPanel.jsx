import { useEffect, useRef, useState } from 'react'
import { MARKET_COLOR, MARKET_GROUPS, MARKET_OPTIONS } from '../lib/jongtoMarkets'

const SORT_OPTIONS = [
  { value: 'attention', label: '주목' },
  { value: 'gainers', label: '상승' },
  { value: 'losers', label: '하락' },
  { value: 'volume', label: '거래량' },
  { value: 'marketCap', label: '시총' },
]

export default function StockListPanel({
  stocks,
  selectedStockId,
  onSelect,
  sort,
  onSortChange,
  loading,
  onSearch,
  marketGroup,
  onMarketGroupChange,
  market,
  onMarketChange,
  scrollResetKey,
}) {
  const [search, setSearch] = useState('')
  const debounceRef = useRef(null)
  const listScrollRef = useRef(null)
  const markets = MARKET_OPTIONS[marketGroup] ?? MARKET_OPTIONS.DOMESTIC

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      onSearch(search.trim())
    }, 300)
    return () => clearTimeout(debounceRef.current)
  }, [search])

  useEffect(() => {
    if (!listScrollRef.current) return
    listScrollRef.current.scrollTop = 0
  }, [scrollResetKey])

  return (
    <div className="flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden sticky top-[calc(var(--header-h)+1rem)] md:top-[calc(var(--header-h)+1.5rem)] h-[calc(100vh-var(--header-h)-2rem)] md:h-[calc(100vh-var(--header-h)-3rem)]">
      <div className="p-3 border-b border-slate-50">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="종목명 또는 종목코드 검색"
          className="w-full px-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/30"
        />
      </div>

      <div className="flex items-center gap-1 px-2 py-1.5 border-b border-slate-50">
        {MARKET_GROUPS.map((group) => (
          <button
            key={group.value}
            onClick={() => onMarketGroupChange(group.value)}
            className={`text-xs px-2.5 py-1 rounded cursor-pointer transition-colors ${
              marketGroup === group.value
                ? 'text-white bg-slate-700 font-semibold'
                : 'text-slate-500 hover:text-slate-700 hover:bg-slate-100'
            }`}
          >
            {group.label}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap gap-1 px-2 py-1.5 border-b border-slate-50">
        {markets.map((marketOption) => (
          <button
            key={marketOption.value}
            onClick={() => onMarketChange(marketOption.value)}
            className={`text-xs px-2 py-0.5 rounded cursor-pointer transition-colors ${
              market === marketOption.value
                ? 'text-white bg-slate-700 font-semibold'
                : 'text-slate-500 hover:text-slate-700 hover:bg-slate-100'
            }`}
          >
            {marketOption.label}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap gap-1 px-2 py-1.5 border-b border-slate-50">
        {SORT_OPTIONS.map((option) => (
          <button
            key={option.value}
            onClick={() => onSortChange(option.value)}
            className={`text-xs px-2.5 py-1 rounded cursor-pointer transition-colors ${
              sort === option.value
                ? 'text-blue-600 font-semibold bg-blue-50'
                : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>

      <div className="px-3 py-1.5 border-b border-slate-50 text-xs text-slate-400">
        {sort === 'gainers' && '상승률 상위 30'}
        {sort === 'losers' && '하락률 상위 30'}
        {sort === 'volume' && '거래량 상위 30'}
        {sort === 'marketCap' && '시가총액 상위 30'}
        {sort === 'attention' && '주목 종목 상위 30'}
      </div>

      <div ref={listScrollRef} className="relative flex-1 overflow-y-auto">
        {stocks.length === 0 ? (
          loading ? (
            <div className="flex items-center justify-center h-32">
              <div className="animate-spin w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full" />
            </div>
          ) : (
            <div className="text-center text-xs text-slate-400 py-12">
              {search ? '검색 결과가 없습니다' : '등록된 종목이 없습니다'}
            </div>
          )
        ) : (
          <div className="divide-y divide-slate-50">
            {stocks.map((stock) => (
              <button
                key={stock.id ?? `${stock.market}-${stock.stockCode}`}
                onClick={() => onSelect(stock)}
                className={`w-full text-left px-3 py-2.5 hover:bg-slate-50 transition-colors cursor-pointer ${
                  selectedStockId === stock.id ? 'bg-blue-50 border-l-2 border-blue-500' : ''
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <span className="text-sm font-medium text-slate-800 truncate">{stock.stockName}</span>
                    <span className={`text-xs px-1.5 py-0.5 rounded font-medium flex-shrink-0 ${MARKET_COLOR[stock.market] || 'bg-slate-100 text-slate-700'}`}>
                      {stock.stockCode}
                    </span>
                  </div>
                  {stock.changeRate != null && (
                    <span
                      className={`text-xs font-medium flex-shrink-0 ml-2 ${
                        stock.changeRate > 0 ? 'text-red-500' : stock.changeRate < 0 ? 'text-blue-500' : 'text-slate-500'
                      }`}
                    >
                      {stock.changeRate > 0 ? '+' : ''}
                      {stock.changeRate?.toFixed(2)}%
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-2 mt-0.5">
                  {stock.currentPrice != null && (
                    <span className="text-xs text-slate-600 font-medium">
                      {Number(stock.currentPrice).toLocaleString()}원
                    </span>
                  )}
                  <span className="text-xs text-slate-400">뉴스 {stock.newsCount}건</span>
                  {stock.hasNewNews && <span className="text-xs px-1 py-0.5 bg-red-50 text-red-500 rounded font-bold">N</span>}
                  <span className="text-xs text-slate-400">스레드 {stock.threadCount}건</span>
                </div>
              </button>
            ))}
          </div>
        )}
        {loading && stocks.length > 0 && (
          <div className="absolute inset-x-0 top-0 flex justify-center pt-3 pointer-events-none">
            <div className="rounded-full bg-white/90 border border-slate-200 shadow-sm px-3 py-1.5 flex items-center gap-2">
              <div className="animate-spin w-3.5 h-3.5 border-2 border-blue-500 border-t-transparent rounded-full" />
              <span className="text-xs text-slate-500">목록 갱신 중</span>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
