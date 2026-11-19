import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import { MARKET_COLOR, MARKET_GROUPS, MARKET_OPTIONS } from '../lib/jongtoMarkets'
import EmptyState from './EmptyState'

const SORT_OPTIONS = [
  { value: 'attention', label: '주목' },
  { value: 'gainers', label: '상승' },
  { value: 'losers', label: '하락' },
  { value: 'volume', label: '거래량' },
  { value: 'marketCap', label: '시총' },
]

const PAGE_SIZE = 5
const AUTO_REFRESH_MS = 30_000

export default function HomeJongtoWidget({ clubId }) {
  const navigate = useNavigate()
  const [stocks, setStocks] = useState([])
  const [loading, setLoading] = useState(true)
  const [sort, setSort] = useState('attention')
  const [marketGroup, setMarketGroup] = useState('DOMESTIC')
  const [market, setMarket] = useState('ALL')
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE)
  const [loadingMore, setLoadingMore] = useState(false)
  const abortRef = useRef(null)
  const markets = MARKET_OPTIONS[marketGroup] ?? MARKET_OPTIONS.DOMESTIC

  const fetchStocks = useCallback(async ({
    nextSort = sort,
    nextGroup = marketGroup,
    nextMarket = market,
    silent = false,
  } = {}) => {
    if (abortRef.current) abortRef.current.abort()
    const controller = new AbortController()
    abortRef.current = controller

    if (!silent) {
      setLoading(true)
      setVisibleCount(PAGE_SIZE)
    }

    try {
      let url = `/clubs/${clubId}/jongtobang/stocks?sort=${nextSort}&page=0&size=30`
      if (nextGroup) url += `&marketGroup=${encodeURIComponent(nextGroup)}`
      if (nextMarket && nextMarket !== 'ALL') url += `&market=${encodeURIComponent(nextMarket)}`

      const items = (await api.request(url, { signal: controller.signal })) || []
      setStocks(items)
    } catch (e) {
      if (e.name === 'AbortError') return
      handleApiError(e, '종토방 종목 조회')
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }, [clubId, market, marketGroup, sort])

  useEffect(() => {
    if (!clubId) return
    fetchStocks()
  }, [clubId, fetchStocks])

  useEffect(() => {
    if (!clubId) return

    const intervalId = window.setInterval(() => {
      if (document.visibilityState !== 'visible') return
      fetchStocks({ silent: true })
    }, AUTO_REFRESH_MS)

    return () => window.clearInterval(intervalId)
  }, [clubId, fetchStocks])

  const handleSortChange = (nextSort) => {
    setSort(nextSort)
  }

  const handleMarketChange = (nextMarket) => {
    setMarket(nextMarket)
  }

  const handleMarketGroupChange = (group) => {
    setMarketGroup(group)
    setMarket('ALL')
  }

  const handleLoadMore = () => {
    setLoadingMore(true)
    setTimeout(() => {
      setVisibleCount((prev) => prev + PAGE_SIZE)
      setLoadingMore(false)
    }, 150)
  }

  const visibleStocks = stocks.slice(0, visibleCount)
  const hasMore = visibleCount < stocks.length

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col overflow-hidden h-full">
      <div className="flex items-center justify-between px-5 py-4">
        <h3 className="text-base font-semibold text-slate-900 scandi-heading">종토방</h3>
        <button
          onClick={() => navigate('/club/jongtobang')}
          className="text-sm text-blue-500 font-medium hover:text-blue-600 transition-colors cursor-pointer"
        >
          전체보기
        </button>
      </div>

      <div className="h-px bg-slate-100" />

      <div className="flex items-center gap-1 px-4 py-1.5 border-b border-slate-50">
        {MARKET_GROUPS.map((group) => (
          <button
            key={group.value}
            onClick={() => handleMarketGroupChange(group.value)}
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

      <div className="flex flex-wrap gap-1 px-4 py-1.5 border-b border-slate-50">
        {markets.map((marketOption) => (
          <button
            key={marketOption.value}
            onClick={() => handleMarketChange(marketOption.value)}
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

      <div className="flex flex-wrap gap-1 px-4 py-1.5 border-b border-slate-50">
        {SORT_OPTIONS.map((option) => (
          <button
            key={option.value}
            onClick={() => handleSortChange(option.value)}
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

      <div className="relative flex-1 overflow-y-auto">
        {stocks.length === 0 ? (
          loading ? (
            <div className="py-4 flex justify-center">
              <div className="animate-spin w-4 h-4 border-2 border-slate-300 border-t-slate-600 rounded-full" />
            </div>
          ) : (
            <EmptyState icon="stock" message="종목이 없습니다" ctaLabel="종토방으로 이동 →" onCta={() => navigate('/club/jongtobang')} />
          )
        ) : (
          <div className="divide-y divide-slate-50">
            {visibleStocks.map((stock) => (
              <button
                key={stock.id ?? `${stock.market}-${stock.stockCode}`}
                onClick={() => navigate(`/club/jongtobang?stockId=${stock.id}`)}
                className="w-full text-left px-4 py-2.5 hover:bg-slate-50 transition-colors cursor-pointer"
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

        {hasMore && (
          <div className="px-4 py-2.5 border-t border-slate-50">
            <button
              onClick={handleLoadMore}
              disabled={loadingMore}
              className="w-full text-center text-xs text-slate-500 hover:text-slate-700 py-1.5 rounded hover:bg-slate-50 transition-colors cursor-pointer disabled:opacity-50"
            >
              {loadingMore ? '불러오는 중...' : `더보기 (${stocks.length - visibleCount}개 남음)`}
            </button>
          </div>
        )}

        {loading && stocks.length > 0 && (
          <div className="absolute inset-x-0 top-0 flex justify-center pt-3 pointer-events-none">
            <div className="rounded-full bg-white/90 border border-slate-200 shadow-sm px-3 py-1.5 flex items-center gap-2">
              <div className="animate-spin w-3.5 h-3.5 border-2 border-slate-400 border-t-transparent rounded-full" />
              <span className="text-xs text-slate-500">목록 갱신 중</span>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
