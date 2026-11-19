import { useState, useEffect } from 'react'
import { BarChart3, Check, ChevronDown, Clock, Users, MoreHorizontal, XCircle } from 'lucide-react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'

/**
 * 투표 카드 — 채팅 메시지 버블 내부에 렌더링
 *
 * Props:
 *   poll       — PollResponse (id 필수, 나머지는 fetch 후 채움)
 *   clubId     — 클럽 ID
 *   membershipId — 현재 사용자의 멤버십 ID
 *   onVote     — (selectedOptions: number[]) => Promise<PollResponse>
 *   onClose    — () => Promise<PollResponse>   (마감)
 */
export default function PollCard({ poll: initialPoll, clubId, membershipId, onVote, onClose }) {
  const [poll, setPoll] = useState(initialPoll)
  const [loading, setLoading] = useState(!initialPoll.results)
  const [selected, setSelected] = useState([])
  const [revoting, setRevoting] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [showMenu, setShowMenu] = useState(false)

  // poll prop이 외부에서 갱신되면 반영
  useEffect(() => {
    if (initialPoll.results) {
      setPoll(initialPoll)
      setLoading(false)
    }
  }, [initialPoll])

  // 데이터가 불완전하면 자체 fetch
  useEffect(() => {
    if (initialPoll.results || !initialPoll.id || !clubId) return
    let cancelled = false
    ;(async () => {
      try {
        const data = await api.get(`/clubs/${clubId}/polls/${initialPoll.id}`)
        if (!cancelled && data) {
          setPoll(data)
          setLoading(false)
        }
      } catch (e) {
        if (!cancelled) {
          handleApiError(e, '투표 로드')
          setLoading(false)
        }
      }
    })()
    return () => { cancelled = true }
  }, [initialPoll.id, clubId])

  if (loading) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 p-4 w-full max-w-[280px] animate-dash-fade-in">
        <div className="flex items-center gap-2 text-slate-400 text-sm">
          <BarChart3 size={16} />
          <span>투표 로딩 중...</span>
        </div>
      </div>
    )
  }

  if (!poll || !poll.question) return null

  const hasVoted = poll.myVote !== null && poll.myVote !== undefined && !revoting
  const isClosed = poll.closed
  const isCreator = poll.creatorId === membershipId
  const showResults = (hasVoted || isClosed) && !revoting

  // 결과에서 최대 득표 index
  const maxCount = poll.results ? Math.max(...poll.results.map(r => r.count), 0) : 0

  const toggleOption = (index) => {
    if (poll.multipleChoice) {
      setSelected(prev =>
        prev.includes(index) ? prev.filter(i => i !== index) : [...prev, index]
      )
    } else {
      setSelected([index])
    }
  }

  const handleVote = async () => {
    if (selected.length === 0 || submitting) return
    setSubmitting(true)
    try {
      const updated = await onVote(selected)
      if (updated) setPoll(updated)
      setSelected([])
      setRevoting(false)
    } catch (e) {
      handleApiError(e, '투표')
    } finally {
      setSubmitting(false)
    }
  }

  const handleClose = async () => {
    setShowMenu(false)
    setSubmitting(true)
    try {
      const updated = await onClose()
      if (updated) setPoll(updated)
    } catch (e) {
      handleApiError(e, '투표 마감')
    } finally {
      setSubmitting(false)
    }
  }

  const handleRevote = () => {
    setRevoting(true)
    setSelected([])
  }

  const formatDeadline = (deadline) => {
    if (!deadline) return null
    const d = new Date(deadline)
    return d.toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-4 w-full max-w-[280px] animate-dash-fade-in">
      {/* Header */}
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <BarChart3 size={16} className="text-primary flex-shrink-0" />
          <p className="text-sm font-semibold text-slate-900 break-words">{poll.question}</p>
        </div>
        {isCreator && !isClosed && (
          <div className="relative flex-shrink-0">
            <button
              onClick={() => setShowMenu(!showMenu)}
              className="p-1 text-slate-400 hover:text-slate-600 rounded cursor-pointer"
              aria-label="투표 메뉴"
            >
              <MoreHorizontal size={14} />
            </button>
            {showMenu && (
              <>
                <div className="fixed inset-0 z-40" onClick={() => setShowMenu(false)} />
                <div className="absolute right-0 top-6 bg-white border border-slate-200 rounded-lg shadow-lg z-50 py-1 min-w-[100px]">
                  <button
                    onClick={handleClose}
                    className="w-full text-left px-3 py-1.5 text-xs text-red-600 hover:bg-red-50 cursor-pointer"
                  >
                    마감하기
                  </button>
                </div>
              </>
            )}
          </div>
        )}
      </div>

      <div className="border-t border-slate-100 mb-3" />

      {/* Results View */}
      {showResults && (
        <div className="space-y-2.5">
          {poll.results?.map((opt) => {
            const isMyChoice = poll.myVote?.includes(opt.index)
            const isWinner = opt.count === maxCount && maxCount > 0
            return (
              <div key={opt.index} className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <span className={`flex items-center gap-1 ${isMyChoice ? 'font-semibold text-primary' : 'text-slate-700'}`}>
                    {isMyChoice && <Check size={12} className="text-primary" />}
                    {opt.text}
                  </span>
                  <span className="text-slate-500">{opt.count}표 ({opt.percentage}%)</span>
                </div>
                <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${isWinner ? 'bg-blue-500' : 'bg-slate-300'}`}
                    style={{ width: `${opt.percentage}%` }}
                  />
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Voting View */}
      {!showResults && !isClosed && (
        <div className="space-y-1.5">
          {(poll.options || poll.results?.map(r => r.text) || []).map((optionText, index) => {
            const text = typeof optionText === 'string' ? optionText : optionText?.text || ''
            const isSelected = selected.includes(index)
            return (
              <button
                key={index}
                onClick={() => toggleOption(index)}
                className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg border text-left text-xs transition-colors cursor-pointer
                  ${isSelected
                    ? 'border-primary bg-primary/5 text-primary'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
              >
                <span className={`flex-shrink-0 w-4 h-4 rounded-${poll.multipleChoice ? 'sm' : 'full'} border-2 flex items-center justify-center
                  ${isSelected ? 'border-primary bg-primary' : 'border-slate-300'}`}>
                  {isSelected && <Check size={10} className="text-white" />}
                </span>
                <span className="break-words">{text}</span>
              </button>
            )
          })}
          <button
            onClick={handleVote}
            disabled={selected.length === 0 || submitting}
            className="w-full mt-2 py-2 bg-primary text-white text-xs font-medium rounded-lg
              hover:bg-primary/90 transition-colors cursor-pointer
              disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {submitting ? '처리 중...' : '투표하기'}
          </button>
        </div>
      )}

      {/* Footer */}
      <div className="flex items-center justify-between mt-3 pt-2 border-t border-slate-100">
        <div className="flex items-center gap-3 text-xs text-slate-400">
          <span className="flex items-center gap-1">
            <Users size={10} />
            {poll.participantCount ?? 0}/{poll.totalMembers ?? 0} 참여
          </span>
          {isClosed && (
            <span className="flex items-center gap-1 text-red-400 font-medium">
              <XCircle size={10} />
              마감됨
            </span>
          )}
          {poll.deadline && !isClosed && (
            <span className="flex items-center gap-1">
              <Clock size={10} />
              {formatDeadline(poll.deadline)}
            </span>
          )}
        </div>
        {hasVoted && !isClosed && !revoting && (
          <button
            onClick={handleRevote}
            className="text-xs text-primary hover:underline cursor-pointer"
          >
            다시 투표
          </button>
        )}
      </div>
    </div>
  )
}
