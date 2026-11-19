import { create } from 'zustand'
import api from '../lib/api'
import useToastStore from './toastStore'

const useChatStore = create((set, get) => ({
  messagesByRoom: {},   // { [roomId]: ChatMessage[] }
  hasMore: {},          // { [roomId]: boolean }
  loading: false,
  sending: false,

  /**
   * cursor 기반 메시지 조회 (#9)
   * - reset=true → 최신 메시지 로드 (방 진입)
   * - reset=false → 기존 메시지 상단에 이전 메시지 추가 (스크롤 위로)
   */
  fetchMessages: async (clubId, roomId, reset = false) => {
    const { messagesByRoom, loading } = get()
    if (loading) return
    const existing = messagesByRoom[roomId] || []

    // cursor: 가장 오래된 메시지 ID (없으면 null → 최신 로드)
    const beforeId = reset ? null : (existing.length > 0 ? existing[0].id : null)

    set({ loading: true })
    try {
      const params = new URLSearchParams({ size: '30' })
      if (beforeId) params.set('beforeId', beforeId)
      const data = await api.get(`/clubs/${clubId}/chat/rooms/${roomId}/messages?${params}`)
      // 서버가 DESC 순으로 반환하므로 reverse하여 시간순
      const msgs = (Array.isArray(data) ? data : data.content || []).reverse()
      set(s => ({
        messagesByRoom: {
          ...s.messagesByRoom,
          [roomId]: reset ? msgs : [...msgs, ...(s.messagesByRoom[roomId] || [])],
        },
        hasMore: { ...s.hasMore, [roomId]: msgs.length >= 30 },
        loading: false,
      }))
    } catch (e) {
      set({ loading: false })
      useToastStore.getState().addToast({
        type: 'ERROR', title: '메시지 로드 실패',
        body: '채팅 메시지를 불러오지 못했습니다.',
      })
    }
  },

  sendMessage: async (clubId, roomId, request) => {
    set({ sending: true })
    try {
      const msg = await api.post(`/clubs/${clubId}/chat/rooms/${roomId}/messages`, request)
      set({ sending: false })
      return msg
    } catch (e) {
      set({ sending: false })
      useToastStore.getState().addToast({
        type: 'ERROR', title: '전송 실패',
        body: '메시지를 보내지 못했습니다. 다시 시도해주세요.',
      })
      throw e
    }
  },

  addMessage: (roomId, msg) => {
    set(s => {
      const existing = s.messagesByRoom[roomId] || []
      // 중복 방지
      if (existing.some(m => m.id === msg.id)) return s
      return {
        messagesByRoom: {
          ...s.messagesByRoom,
          [roomId]: [...existing, msg],
        },
      }
    })
  },

  clearRoom: (roomId) => {
    set(s => {
      const { [roomId]: _, ...rest } = s.messagesByRoom
      const { [roomId]: __, ...restHasMore } = s.hasMore
      return { messagesByRoom: rest, hasMore: restHasMore }
    })
  },

  // ========== Poll actions ==========

  /** 투표 실행 */
  votePoll: async (clubId, pollId, selectedOptions) => {
    const res = await api.post(`/clubs/${clubId}/polls/${pollId}/vote`, { selectedOptions })
    return res
  },

  /** 투표 마감 */
  closePoll: async (clubId, pollId) => {
    const res = await api.post(`/clubs/${clubId}/polls/${pollId}/close`)
    return res
  },

  /** WebSocket에서 수신한 poll 데이터로 메시지 내 pollData 갱신 */
  updatePollInMessages: (roomId, pollId, pollData) => {
    set(state => {
      const msgs = state.messagesByRoom[roomId]
      if (!msgs) return state
      return {
        messagesByRoom: {
          ...state.messagesByRoom,
          [roomId]: msgs.map(m =>
            m.pollId === pollId ? { ...m, pollData } : m
          ),
        },
      }
    })
  },

  reset: () => set({ messagesByRoom: {}, hasMore: {}, loading: false, sending: false }),
}))

export default useChatStore
