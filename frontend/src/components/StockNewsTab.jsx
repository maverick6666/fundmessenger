import { useState, useEffect, useRef } from 'react'
import api from '../lib/api'
import NewsAccordionItem from './NewsAccordionItem'
import handleApiError from '../lib/handleApiError'

export default function StockNewsTab({ clubId, stockId, membershipId, isExecutive, highlightNewsId = null }) {
  const [news, setNews] = useState([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(true)
  const requestedHighlightRef = useRef(null)

  const fetchNews = async (p = 0, append = false) => {
    try {
      setLoading(true)
      const data = await api.get(`/clubs/${clubId}/jongtobang/stocks/${stockId}/news?page=${p}&size=5`)
      const items = data || []
      if (append) {
        setNews(prev => [...prev, ...items])
      } else {
        setNews(items)
      }
      setHasMore(items.length === 5)
    } catch (e) {
      handleApiError(e, '뉴스 조회')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    setPage(0)
    setNews([])
    setHasMore(true)
    requestedHighlightRef.current = null
    fetchNews(0)
  }, [stockId])

  useEffect(() => {
    if (!highlightNewsId || !clubId || !stockId) return
    if (news.some((item) => item.couplingId === highlightNewsId)) return
    if (requestedHighlightRef.current === highlightNewsId) return

    requestedHighlightRef.current = highlightNewsId

    api.get(`/clubs/${clubId}/jongtobang/stocks/${stockId}/news/${highlightNewsId}`)
      .then((detail) => {
        if (!detail) return
        setNews((prev) => (
          prev.some((item) => item.couplingId === highlightNewsId)
            ? prev
            : [detail, ...prev]
        ))
      })
      .catch(() => {
        requestedHighlightRef.current = null
      })
  }, [clubId, highlightNewsId, news, stockId])

  const handleLoadMore = () => {
    const nextPage = page + 1
    setPage(nextPage)
    fetchNews(nextPage, true)
  }

  if (loading && news.length === 0) {
    return (
      <div className="flex items-center justify-center h-32">
        <div className="animate-spin w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full" />
      </div>
    )
  }

  if (news.length === 0) {
    return (
      <div className="text-center text-sm text-slate-400 py-12">
        <p>관련 뉴스가 없습니다</p>
        <p className="text-xs mt-1">AI가 뉴스를 수집하면 여기에 표시됩니다</p>
      </div>
    )
  }

  return (
    <div className="p-4 space-y-2">
      {news.map(item => (
        <NewsAccordionItem
          key={item.couplingId}
          clubId={clubId}
          stockId={stockId}
          news={item}
          membershipId={membershipId}
          isExecutive={isExecutive}
          autoExpand={highlightNewsId === item.couplingId}
        />
      ))}
      {hasMore && (
        <button
          onClick={handleLoadMore}
          disabled={loading}
          className="w-full py-2 text-xs text-blue-500 hover:text-blue-600 font-medium cursor-pointer"
        >
          {loading ? '로딩...' : '더보기'}
        </button>
      )}
    </div>
  )
}
