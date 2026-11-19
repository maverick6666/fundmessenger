import { useCallback, useEffect, useRef, useState } from 'react'
import { useOutletContext, useSearchParams } from 'react-router-dom'
import api from '../lib/api'
import StockListPanel from '../components/StockListPanel'
import StockDetailPanel from '../components/StockDetailPanel'
import handleApiError from '../lib/handleApiError'

const LIST_SIZE = 30
const AUTO_REFRESH_MS = 30_000

export default function JongtobangPage() {
  const { clubInfo } = useOutletContext()
  const clubId = clubInfo.clubId
  const [searchParams, setSearchParams] = useSearchParams()
  const highlightedNewsId = searchParams.get('newsId')
  const urlStockId = searchParams.get('stockId')

  const [stocks, setStocks] = useState([])
  const [selectedStock, setSelectedStock] = useState(null)
  const [sort, setSort] = useState('attention')
  const [loading, setLoading] = useState(true)
  const [mobileShowDetail, setMobileShowDetail] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [marketGroup, setMarketGroup] = useState('DOMESTIC')
  const [market, setMarket] = useState('ALL')
  const abortRef = useRef(null)
  const selectedStockIdRef = useRef(null)

  useEffect(() => {
    selectedStockIdRef.current = selectedStock?.id ?? null
  }, [selectedStock])

  const fetchStocks = useCallback(async ({
    nextSort = sort,
    nextQuery = searchQuery,
    nextGroup = marketGroup,
    nextMarket = market,
    silent = false,
  } = {}) => {
    if (abortRef.current) abortRef.current.abort()
    const controller = new AbortController()
    abortRef.current = controller

    if (!silent) {
      setLoading(true)
    }

    try {
      let url = `/clubs/${clubId}/jongtobang/stocks?sort=${nextSort}&page=0&size=${LIST_SIZE}`
      if (nextQuery) url += `&search=${encodeURIComponent(nextQuery)}`
      if (nextGroup) url += `&marketGroup=${encodeURIComponent(nextGroup)}`
      if (nextMarket && nextMarket !== 'ALL') url += `&market=${encodeURIComponent(nextMarket)}`

      const items = (await api.request(url, { signal: controller.signal })) || []
      setStocks(items)

      const currentSelectedStockId = selectedStockIdRef.current
      const activeStockId = currentSelectedStockId ?? (urlStockId ? Number(urlStockId) : null)
      if (!activeStockId) return

      const found = items.find((stock) => stock.id === activeStockId)
      if (found) {
        selectedStockIdRef.current = found.id
        setSelectedStock(found)
        setMobileShowDetail(true)
        return
      }

      if (!urlStockId || currentSelectedStockId) return

      try {
        const stock = await api.get(`/clubs/${clubId}/jongtobang/stocks/${urlStockId}`)
        if (stock) {
          selectedStockIdRef.current = stock.id
          setSelectedStock(stock)
          setMobileShowDetail(true)
        }
      } catch (_) {
        // ignore invalid stock id in query
      }
    } catch (e) {
      if (e.name === 'AbortError') return
      handleApiError(e, '종목 조회')
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }, [clubId, market, marketGroup, searchQuery, sort, urlStockId])

  useEffect(() => {
    fetchStocks()
  }, [fetchStocks])

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      if (document.visibilityState !== 'visible') return
      fetchStocks({ silent: true })
    }, AUTO_REFRESH_MS)

    return () => window.clearInterval(intervalId)
  }, [fetchStocks])

  const handleSortChange = useCallback((nextSort) => {
    setSort(nextSort)
  }, [])

  const handleSearch = useCallback((query) => {
    setSearchQuery(query)
  }, [])

  const handleMarketChange = useCallback((nextMarket) => {
    setMarket(nextMarket)
  }, [])

  const handleMarketGroupChange = useCallback((group) => {
    setMarketGroup(group)
    setMarket('ALL')
  }, [])

  const handleListRefresh = useCallback(() => {
    fetchStocks({ silent: true })
  }, [fetchStocks])

  const handleSelectStock = useCallback((stock) => {
    selectedStockIdRef.current = stock.id
    setSelectedStock(stock)
    setSearchParams({ stockId: stock.id })
    setMobileShowDetail(true)
  }, [setSearchParams])

  return (
    <div className="flex gap-4 -mx-3 md:-mx-6 -mt-4 md:-mt-6 -mb-24 md:-mb-40 px-3 md:px-6 pt-4 md:pt-6 min-h-[calc(100vh-var(--header-h))] animate-fade-in-up">
      {/* 데스크톱: 사이드바 */}
      <div className="hidden md:block w-72 shrink-0">
        <StockListPanel
          stocks={stocks}
          selectedStockId={selectedStock?.id}
          onSelect={handleSelectStock}
          sort={sort}
          onSortChange={handleSortChange}
          loading={loading}
          onSearch={handleSearch}
          marketGroup={marketGroup}
          onMarketGroupChange={handleMarketGroupChange}
          market={market}
          onMarketChange={handleMarketChange}
          scrollResetKey={`${sort}:${marketGroup}:${market}:${searchQuery}`}
        />
      </div>

      {/* 모바일: 종목 미선택 시 전체 너비 목록 */}
      {!mobileShowDetail && (
        <div className="md:hidden flex-1 min-w-0 overflow-y-auto">
          <StockListPanel
            stocks={stocks}
            selectedStockId={selectedStock?.id}
            onSelect={handleSelectStock}
            sort={sort}
            onSortChange={handleSortChange}
            loading={loading}
            onSearch={handleSearch}
            marketGroup={marketGroup}
            onMarketGroupChange={handleMarketGroupChange}
            market={market}
            onMarketChange={handleMarketChange}
            scrollResetKey={`${sort}:${marketGroup}:${market}:${searchQuery}`}
          />
        </div>
      )}

      <div className={`flex-1 min-w-0 flex flex-col pb-48 md:pb-64 ${mobileShowDetail ? '' : 'hidden md:flex'}`}>
        {mobileShowDetail && selectedStock && (
          <button
            onClick={() => setMobileShowDetail(false)}
            className="md:hidden flex items-center gap-1 text-sm text-blue-500 mb-2 cursor-pointer"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            종목 목록
          </button>
        )}

        <StockDetailPanel
          clubId={clubId}
          stock={selectedStock}
          membershipId={clubInfo.membershipId}
          isExecutive={clubInfo.isExecutive}
          highlightNewsId={highlightedNewsId ? Number(highlightedNewsId) : null}
          onListRefresh={handleListRefresh}
        />
      </div>
    </div>
  )
}
