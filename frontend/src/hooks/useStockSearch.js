import { useState, useEffect, useRef } from 'react'
import api from '../lib/api'
import { SEARCH_DEBOUNCE_MS } from '../lib/fundConstants'

/**
 * 종목 검색 훅 — v2 종토방 stocks API 재활용
 *
 * @param {number} clubId - 동아리 ID
 * @param {string} market - 시장 필터 (KOSPI, KOSDAQ, NASDAQ, NYSE, CRYPTO)
 * @param {number} debounceMs - 디바운스 지연 (ms)
 */
export function useStockSearch(clubId, market, debounceMs = SEARCH_DEBOUNCE_MS) {
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [showDropdown, setShowDropdown] = useState(false)
  const dropdownRef = useRef(null)

  useEffect(() => {
    if (!searchQuery || searchQuery.length < 1) {
      setSearchResults([])
      setShowDropdown(false)
      return
    }

    setSearchLoading(true)
    const timer = setTimeout(async () => {
      try {
        // KOSPI/KOSDAQ는 합쳐서 검색 후 필터, 나머지는 시장 직접 필터
        let marketFilter = null
        if (market !== 'KOSPI' && market !== 'KOSDAQ') {
          marketFilter = market
        }

        const params = new URLSearchParams({ search: searchQuery, size: '15' })
        if (marketFilter) params.set('market', marketFilter)

        const data = await api.get(`/clubs/${clubId}/jongtobang/stocks?${params}`)
        let results = data.content || data || []

        // KOSPI/KOSDAQ 중 선택된 시장만 필터
        if (market === 'KOSPI' || market === 'KOSDAQ') {
          results = results.filter(s => s.market === 'KOSPI' || s.market === 'KOSDAQ')
        }

        setSearchResults(results)
        setShowDropdown(results.length > 0)
      } catch (err) {
        console.error('Stock search failed:', err)
        setSearchResults([])
      } finally {
        setSearchLoading(false)
      }
    }, debounceMs)

    return () => clearTimeout(timer)
  }, [searchQuery, market, clubId, debounceMs])

  const clearSearch = () => {
    setSearchQuery('')
    setSearchResults([])
    setShowDropdown(false)
  }

  return {
    searchQuery, setSearchQuery,
    searchResults, searchLoading,
    showDropdown, setShowDropdown,
    dropdownRef, clearSearch,
  }
}
