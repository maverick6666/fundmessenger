import { useEffect, useRef, useState } from 'react'
import { Share2 } from 'lucide-react'
import api from '../lib/api'
import NewsCommentSection from './NewsCommentSection'
import NewsMarkdownRenderer from './NewsMarkdownRenderer'
import handleApiError from '../lib/handleApiError'
import useMessagingStore from '../store/messagingStore'

const DEFAULT_TAG_STYLE = 'bg-blue-50 text-blue-700 border-blue-200'

function TagBadge({ tag }) {
  return (
    <span className={`inline-block text-xs font-semibold px-1.5 py-0.5 border rounded ${DEFAULT_TAG_STYLE}`}>
      {tag}
    </span>
  )
}

// 감성 → 좌측 바 CSS 클래스
function getSentimentBarClass(sentimentType) {
  if (sentimentType === 'bullish') return 'sentiment-bar-bullish'
  if (sentimentType === 'bearish') return 'sentiment-bar-bearish'
  return null
}

// 감성 → 감성점수 뱃지 스타일
function getScoreBadgeClass(sentimentType) {
  if (sentimentType === 'bullish') return 'sentiment-badge-bullish'
  if (sentimentType === 'bearish') return 'sentiment-badge-bearish'
  return 'bg-slate-100 text-slate-500' // neutral / 기본
}

export default function NewsAccordionItem({ clubId, stockId, news, membershipId, isExecutive, autoExpand = false }) {
  const openContentShare = useMessagingStore((s) => s.openContentShare)
  const [expanded, setExpanded] = useState(false)
  const [detail, setDetail] = useState(news.bodyMarkdown ? news : null)
  const [liked, setLiked] = useState(news.liked || false)
  const [likeCount, setLikeCount] = useState(news.likeCount || 0)
  const itemRef = useRef(null)

  const fetchDetail = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/jongtobang/stocks/${stockId}/news/${news.couplingId}`)
      setDetail(data)
    } catch (e) {
      handleApiError(e, '뉴스 상세 조회')
    }
  }

  const handleToggle = () => {
    if (!expanded && !detail) {
      fetchDetail()
    }
    setExpanded(!expanded)
  }

  useEffect(() => {
    if (!autoExpand) return undefined

    if (!expanded) {
      setExpanded(true)
    }

    if (!detail) {
      fetchDetail()
    }

    const timerId = window.setTimeout(() => {
      itemRef.current?.scrollIntoView({ block: 'start', behavior: 'smooth' })
    }, 80)

    return () => window.clearTimeout(timerId)
  }, [autoExpand, detail, expanded])

  const handleLike = async (e) => {
    e.stopPropagation()
    try {
      const data = await api.post(`/clubs/${clubId}/jongtobang/stocks/${stockId}/news/${news.couplingId}/like`)
      setLiked(data.liked)
      setLikeCount(data.likeCount)
    } catch (err) {
      handleApiError(err, '뉴스 공감')
    }
  }

  // 감성 태그(호재/악재/중립) 제외, 주제 태그만 표시
  const sentimentTags = ['호재', '악재', '중립']
  const tags = (news.tags || []).filter(t => !sentimentTags.includes(t))

  const barClass = getSentimentBarClass(news.sentimentType)
  const scoreBadgeClass = getScoreBadgeClass(news.sentimentType)
  const isNew = news.createdAt && (Date.now() - new Date(news.createdAt).getTime() < 60 * 60 * 1000)

  return (
    <div
      ref={itemRef}
      className={`border border-slate-200 shadow-sm rounded-lg overflow-hidden ${barClass || ''}`}
    >
      {/* 닫힌 상태 헤더 */}
      <button
        onClick={handleToggle}
        className="w-full text-left px-4 py-3 hover:bg-slate-50 transition-colors cursor-pointer"
      >
        {/* 주제 태그 뱃지 */}
        {tags.length > 0 && (
          <div className="flex items-center gap-1 mb-1.5">
            {tags.map((tag, i) => <TagBadge key={i} tag={tag} />)}
          </div>
        )}
        <div className="flex items-center justify-between gap-2">
          <h4 className="text-sm font-semibold text-slate-800 line-clamp-1 flex-1">
            {news.title}
            {isNew && <span className="ml-1.5 text-xs px-1.5 py-0.5 bg-red-50 text-red-500 rounded font-bold align-middle">N</span>}
          </h4>
          {news.sentimentScore != null && (
            <span className={`text-xs font-bold px-2 py-0.5 rounded flex-shrink-0 ${scoreBadgeClass}`}>
              {news.sentimentScore}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2 mt-1">
          {news.authorEmoji && news.authorName && (
            <span className="text-xs text-slate-500 font-medium">
              {news.authorEmoji} {news.authorName}
            </span>
          )}
          <span className="text-xs text-slate-400">
            {new Date(news.updatedAt).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })}
          </span>
          <span className="text-xs text-slate-400">원문 {news.rawNewsCount}건</span>
          <span className="text-xs text-slate-400">댓글 {news.commentCount}건</span>
          {likeCount > 0 && (
            <span className={`text-xs font-medium ${liked ? 'text-red-500' : 'text-slate-400'}`}>
              {liked ? '♥' : '♡'} {likeCount}
            </span>
          )}
          <svg className={`w-3.5 h-3.5 text-slate-400 ml-auto transition-transform ${expanded ? 'rotate-180' : ''}`}
            fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </button>

      {/* 펼친 상태 */}
      {expanded && (
        <div className="px-4 pb-4 border-t border-slate-50 transition-all duration-200">
          {detail ? (
            <>
              {/* AI 재작성 본문 (마크다운 + 차트 렌더링) */}
              <div className="mt-3 flex justify-end">
                <button
                  type="button"
                  onClick={() => openContentShare({ sharedNewsId: news.couplingId, sharedType: 'NEWS', title: news.title })}
                  className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 px-3 py-1 text-xs font-medium text-slate-600 hover:bg-slate-50 cursor-pointer"
                >
                  <Share2 className="h-3.5 w-3.5" />
                  채팅으로 보내기
                </button>
              </div>

              {detail.bodyMarkdown && (
                <div className="mt-3">
                  <NewsMarkdownRenderer content={detail.bodyMarkdown} />
                </div>
              )}

              {/* 업데이트 로그 */}
              {detail.updateLog && (
                <div className="mt-3 p-2 bg-slate-50 rounded-lg">
                  <h5 className="text-xs font-semibold text-slate-500 mb-1">업데이트 로그</h5>
                  <p className="text-xs text-slate-500 whitespace-pre-wrap">{detail.updateLog}</p>
                </div>
              )}

              {/* 좋아요 버튼 — 본문 아래 정중앙 */}
              <div className="flex justify-center py-4">
                <button
                  onClick={handleLike}
                  className={`flex items-center gap-1.5 px-5 py-2 rounded-full text-xs font-medium transition-all cursor-pointer ${
                    liked
                      ? 'bg-red-50 text-red-500 border border-red-200'
                      : 'bg-slate-100 text-slate-500 border border-slate-200 hover:bg-slate-200 hover:text-red-400'
                  }`}
                >
                  <span className="text-sm">{liked ? '♥' : '♡'}</span>
                  <span>추천 {likeCount}</span>
                </button>
              </div>

              {/* 댓글 */}
              <NewsCommentSection
                clubId={clubId}
                stockId={stockId}
                couplingId={news.couplingId}
                membershipId={membershipId}
                isExecutive={isExecutive}
              />
            </>
          ) : (
            <div className="py-4 flex justify-center">
              <div className="animate-spin w-4 h-4 border-2 border-slate-300 border-t-gray-600 rounded-full" />
            </div>
          )}
        </div>
      )}
    </div>
  )
}
