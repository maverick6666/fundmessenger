import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, BarChart3, Bell, BellOff, Send, Smile, Users } from 'lucide-react'
import StickerPicker from './StickerPicker'
import PollCard from './PollCard'
import CreatePollModal from './CreatePollModal'
import useMessagingStore from '../store/messagingStore'
import useChatStore from '../store/chatStore'
import useClubStore from '../store/clubStore'
import api from '../lib/api'
import { subscribeChatRoom, unsubscribeChatRoom } from '../hooks/useWebSocket'

const EMPTY_MESSAGES = []
const BOTTOM_THRESHOLD = 72

export default function ChatDetailView() {
  const navigate = useNavigate()
  const roomId = useMessagingStore((s) => s.activeChatRoomId)
  const exitChat = useMessagingStore((s) => s.exitChat)
  const closePanel = useMessagingStore((s) => s.closePanel)
  const chatRooms = useMessagingStore((s) => s.chatRooms)
  const markRead = useMessagingStore((s) => s.markRead)
  const clubInfo = useClubStore((s) => s.clubInfo)

  const messages = useChatStore((s) => (roomId ? s.messagesByRoom[roomId] : undefined)) ?? EMPTY_MESSAGES
  const hasMore = useChatStore((s) => (roomId ? s.hasMore[roomId] : undefined)) !== false
  const loading = useChatStore((s) => s.loading)
  const fetchMessages = useChatStore((s) => s.fetchMessages)
  const sendMessage = useChatStore((s) => s.sendMessage)
  const votePoll = useChatStore((s) => s.votePoll)
  const closePoll = useChatStore((s) => s.closePoll)
  const updatePollInMessages = useChatStore((s) => s.updatePollInMessages)

  const [input, setInput] = useState('')
  const [showEmoji, setShowEmoji] = useState(false)
  const [showPollModal, setShowPollModal] = useState(false)
  const [showMembers, setShowMembers] = useState(false)
  const [roomMembers, setRoomMembers] = useState([])
  const [muted, setMuted] = useState(false)

  const scrollRef = useRef(null)
  const contentRef = useRef(null)
  const inputRef = useRef(null)
  const focusTimerRef = useRef(null)
  const scrollTimerIdsRef = useRef([])
  const shouldStickToBottomRef = useRef(true)

  const clubId = clubInfo?.clubId
  const room = chatRooms.find((chatRoom) => chatRoom.id === roomId)

  useEffect(() => {
    if (room) setMuted(room.muted || false)
  }, [room?.id])

  const clearScheduledScrolls = useCallback(() => {
    scrollTimerIdsRef.current.forEach((timerId) => window.clearTimeout(timerId))
    scrollTimerIdsRef.current = []
  }, [])

  const scrollToBottom = useCallback((behavior = 'auto') => {
    const container = scrollRef.current
    if (!container) return

    container.scrollTo({
      top: container.scrollHeight,
      behavior,
    })
  }, [])

  const queueScrollToBottom = useCallback((behavior = 'auto') => {
    clearScheduledScrolls()

    const run = () => scrollToBottom(behavior)
    run()

    ;[0, 80, 180].forEach((delay) => {
      const timerId = window.setTimeout(run, delay)
      scrollTimerIdsRef.current.push(timerId)
    })
  }, [clearScheduledScrolls, scrollToBottom])

  useEffect(() => {
    shouldStickToBottomRef.current = true
  }, [roomId])

  useEffect(() => {
    if (!clubId || !roomId) return undefined

    shouldStickToBottomRef.current = true
    fetchMessages(clubId, roomId, true)
    markRead(clubId, roomId)
    subscribeChatRoom(roomId)

    focusTimerRef.current = window.setTimeout(() => {
      inputRef.current?.focus()
    }, 100)

    return () => {
      unsubscribeChatRoom(roomId)
      window.clearTimeout(focusTimerRef.current)
      clearScheduledScrolls()
    }
  }, [clubId, roomId, fetchMessages, markRead, clearScheduledScrolls])

  useLayoutEffect(() => {
    if (shouldStickToBottomRef.current) {
      queueScrollToBottom()
    }
  }, [messages.length, queueScrollToBottom])

  useEffect(() => {
    if (!clubId || !roomId || messages.length === 0) return
    markRead(clubId, roomId)
  }, [clubId, roomId, messages.length, markRead])

  useEffect(() => {
    const contentNode = contentRef.current
    if (!contentNode || typeof ResizeObserver === 'undefined') return undefined

    const observer = new ResizeObserver(() => {
      if (shouldStickToBottomRef.current) {
        scrollToBottom()
      }
    })

    observer.observe(contentNode)
    return () => observer.disconnect()
  }, [scrollToBottom, roomId])

  const loadMore = useCallback(() => {
    if (hasMore && !loading && clubId && roomId) {
      fetchMessages(clubId, roomId, false)
    }
  }, [hasMore, loading, clubId, roomId, fetchMessages])

  const handleScroll = useCallback(() => {
    const container = scrollRef.current
    if (!container) return

    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight
    shouldStickToBottomRef.current = distanceFromBottom < BOTTOM_THRESHOLD

    if (container.scrollTop < 50 && hasMore && !loading) {
      loadMore()
    }
  }, [hasMore, loading, loadMore])

  const handleSend = async () => {
    const content = input.trim()
    if (!content || !clubId || !roomId) return

    shouldStickToBottomRef.current = true
    setInput('')

    try {
      await sendMessage(clubId, roomId, { content })
      markRead(clubId, roomId)
    } catch {
      // The store already surfaces send errors.
    }
  }

  const handleKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleSend()
    }
  }

  const openSharedContent = useCallback((message) => {
    const targetUrl =
      message.sharedTargetUrl ||
      (message.sharedPostId ? `/club/boards?post=${message.sharedPostId}` : null)

    if (!targetUrl) return

    closePanel()
    navigate(targetUrl)
  }, [closePanel, navigate])

  return (
    <div className="flex min-h-0 flex-1 flex-col relative" role="region" aria-label="채팅 대화">
      <div className="flex items-center gap-2 border-b border-slate-100 px-3 py-2.5">
        <button
          onClick={exitChat}
          className="rounded p-1 hover:bg-slate-100 cursor-pointer"
          aria-label="채팅방 목록으로 돌아가기"
        >
          <ArrowLeft className="h-4 w-4 text-slate-500" />
        </button>
        <span className="truncate text-sm font-semibold text-slate-900 flex-1">{room?.name || 'DM'}</span>
        <button
          onClick={async () => {
            try {
              const data = await api.put(`/clubs/${clubId}/chat/rooms/${roomId}/mute`)
              setMuted(data.muted)
            } catch { /* ignore */ }
          }}
          className={`p-1.5 rounded hover:bg-slate-100 cursor-pointer transition-colors ${muted ? 'text-slate-300' : 'text-slate-500'}`}
          title={muted ? '알림 꺼짐' : '알림 켜짐'}
        >
          {muted ? <BellOff className="w-3.5 h-3.5" /> : <Bell className="w-3.5 h-3.5" />}
        </button>
        {room?.memberCount > 0 && (
          <button
            onClick={async () => {
              if (!showMembers && roomMembers.length === 0) {
                try {
                  const data = await api.get(`/clubs/${clubId}/chat/rooms/${roomId}/members`)
                  setRoomMembers(data || [])
                } catch { /* ignore */ }
              }
              setShowMembers(prev => !prev)
            }}
            className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600 cursor-pointer px-1.5 py-0.5 rounded hover:bg-slate-50"
          >
            <Users className="w-3.5 h-3.5" />
            <span>{room.memberCount}</span>
          </button>
        )}
      </div>

      {/* 멤버 목록 드롭다운 */}
      {showMembers && (
        <div className="border-b border-slate-100 bg-slate-50 px-3 py-2 max-h-[150px] overflow-y-auto">
          {roomMembers.length === 0 ? (
            <p className="text-xs text-slate-400 py-1">로딩 중...</p>
          ) : (
            <div className="space-y-1">
              {roomMembers.map(m => (
                <div key={m.id || m.membershipId} className="flex items-center gap-2 py-1">
                  <div className="w-6 h-6 rounded-full bg-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                    {(m.name || m.memberName || '?')[0]}
                  </div>
                  <span className="text-xs text-slate-700">{m.name || m.memberName}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto px-3 py-2"
        role="log"
        aria-label="메시지 목록"
        aria-live="polite"
      >
        <div ref={contentRef} className="space-y-3">
          {loading && messages.length === 0 && (
            <div className="flex items-center justify-center py-8" role="status">
              <div className="h-5 w-5 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              <span className="sr-only">메시지 로딩 중</span>
            </div>
          )}

          {hasMore && messages.length > 0 && (
            <button
              onClick={loadMore}
              disabled={loading}
              className="w-full py-2 text-xs text-slate-400 hover:text-slate-600 cursor-pointer disabled:opacity-50"
            >
              {loading ? '불러오는 중...' : '이전 메시지 불러오기'}
            </button>
          )}

          {messages.map((message, index) => {
            const isMine = message.senderId === clubInfo?.membershipId
            const showDate =
              index === 0 ||
              new Date(message.createdAt).toDateString() !== new Date(messages[index - 1]?.createdAt).toDateString()
            const time = new Date(message.createdAt).toLocaleTimeString('ko-KR', {
              hour: '2-digit',
              minute: '2-digit',
            })

            return (
              <div key={message.id}>
                {showDate && (
                  <div className="my-3 text-center text-xs text-slate-400" role="separator">
                    {new Date(message.createdAt).toLocaleDateString('ko-KR', {
                      month: 'long',
                      day: 'numeric',
                      weekday: 'short',
                    })}
                  </div>
                )}

                <div className={`flex gap-2 ${isMine ? 'justify-end' : 'justify-start'}`}>
                  {!isMine && (
                    <div
                      className="mt-0.5 flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-slate-200"
                      aria-hidden="true"
                    >
                      <span className="text-xs text-slate-500">{message.senderName?.charAt(0)}</span>
                    </div>
                  )}

                  <div className={`max-w-[70%] ${isMine ? 'order-first' : ''}`}>
                    {!isMine && (
                      <p className="mb-0.5 text-xs text-slate-500">{message.senderName}</p>
                    )}

                    {message.messageType === 'STICKER' && message.stickerUrl ? (
                      <img src={message.stickerUrl} alt="스티커" className="h-32 w-32 object-contain" />
                    ) : message.pollId ? (
                      <PollCard
                        poll={message.pollData || { id: message.pollId }}
                        clubId={clubId}
                        membershipId={clubInfo?.membershipId}
                        onVote={async (selectedOptions) => {
                          const updated = await votePoll(clubId, message.pollId, selectedOptions)
                          if (updated) updatePollInMessages(roomId, message.pollId, updated)
                          shouldStickToBottomRef.current = true
                          return updated
                        }}
                        onClose={async () => {
                          const updated = await closePoll(clubId, message.pollId)
                          if (updated) updatePollInMessages(roomId, message.pollId, updated)
                          shouldStickToBottomRef.current = true
                          return updated
                        }}
                      />
                    ) : message.sharedPostId || message.sharedNewsId || message.sharedTargetUrl ? (
                      <button
                        type="button"
                        onClick={() => openSharedContent(message)}
                        className={`w-full rounded-2xl px-3 py-2 text-left text-sm break-words transition-colors ${
                          isMine
                            ? 'rounded-tr-md bg-primary text-white hover:bg-primary/90'
                            : 'rounded-tl-md bg-slate-100 text-slate-900 hover:bg-slate-200'
                        }`}
                      >
                        <p className={`text-xs font-semibold uppercase tracking-[0.16em] ${
                          isMine ? 'text-white/70' : 'text-slate-500'
                        }`}>
                          {message.sharedType === 'NEWS' ? 'NEWS' : 'POST'}
                        </p>
                        <p className="mt-1 font-semibold leading-5">
                          {message.sharedTitle || (message.sharedType === 'NEWS' ? '뉴스로 이동' : '게시글로 이동')}
                        </p>
                        <p className={`mt-2 text-xs ${
                          isMine ? 'text-white/80' : 'text-slate-500'
                        }`}>
                          눌러서 바로 보기
                        </p>
                      </button>
                    ) : (
                      <div
                        className={`rounded-2xl px-3 py-2 text-sm break-words whitespace-pre-wrap ${
                          isMine
                            ? 'rounded-tr-md bg-primary text-white'
                            : 'rounded-tl-md bg-slate-100 text-slate-900'
                        }`}
                      >
                        {message.content}
                        {message.imageUrl && (
                          <img
                            src={message.imageUrl}
                            alt="첨부 이미지"
                            className="mt-1 max-w-full rounded-lg"
                          />
                        )}
                      </div>
                    )}

                    <p
                      className={`mt-0.5 text-xs text-slate-400 ${isMine ? 'text-right' : ''}`}
                      aria-label={`${time}에 전송`}
                    >
                      {time}
                    </p>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      </div>

      <div className="relative flex items-center gap-2 border-t border-slate-100 bg-white px-3 py-2">
        <label className="sr-only" htmlFor="chat-input">
          메시지 입력
        </label>
        <input
          ref={inputRef}
          id="chat-input"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="메시지 입력..."
          autoComplete="off"
          className="flex-1 rounded-full bg-slate-50 px-4 py-2 text-sm outline-none focus:ring-1 focus:ring-primary/30"
          aria-label="메시지 입력"
        />

        <button
          onClick={() => setShowPollModal(true)}
          className="rounded-full p-2 text-slate-400 transition-colors hover:text-primary cursor-pointer"
          title="투표 만들기"
          aria-label="투표 만들기"
        >
          <BarChart3 className="h-4 w-4" />
        </button>

        <div className="relative">
          <button
            onClick={() => setShowEmoji((prev) => !prev)}
            className="rounded-full p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-600 cursor-pointer"
            aria-label="이모지 선택"
          >
            <Smile className="h-4 w-4" />
          </button>

          {showEmoji && (
            <StickerPicker
              onEmojiSelect={(emoji) => {
                setInput((prev) => prev + emoji)
                setShowEmoji(false)
                inputRef.current?.focus()
              }}
              onStickerSelect={async (sticker) => {
                if (!clubId || !roomId) return

                shouldStickToBottomRef.current = true
                try {
                  await sendMessage(clubId, roomId, {
                    messageType: 'STICKER',
                    stickerUrl: sticker.imageUrl,
                    stickerId: sticker.id,
                  })
                  markRead(clubId, roomId)
                } catch {
                  // The store already surfaces send errors.
                }
                setShowEmoji(false)
              }}
              onClose={() => setShowEmoji(false)}
            />
          )}
        </div>

        <button
          onClick={handleSend}
          disabled={!input.trim()}
          className="rounded-full p-2 text-primary hover:bg-primary/10 cursor-pointer disabled:cursor-not-allowed disabled:opacity-30"
          aria-label="메시지 보내기"
        >
          <Send className="h-4 w-4" />
        </button>
      </div>

      {/* 투표 만들기 — 패널 전체를 대체하는 오버레이 */}
      {showPollModal && (
        <div className="absolute inset-0 z-10 bg-white flex flex-col">
          <CreatePollModal
            clubId={clubId}
            chatRoomId={roomId}
            onClose={() => setShowPollModal(false)}
            onCreated={() => {
              shouldStickToBottomRef.current = true
              setShowPollModal(false)
            }}
          />
        </div>
      )}
    </div>
  )
}
