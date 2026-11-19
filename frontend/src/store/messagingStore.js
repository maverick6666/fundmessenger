import { create } from 'zustand'
import api from '../lib/api'
import useToastStore from './toastStore'
import handleApiError from '../lib/handleApiError'

const showError = (title, body) => {
  useToastStore.getState().addToast({ type: 'ERROR', title, body })
}

const mergeUnreadCounts = (nextRooms, currentRooms, activeChatRoomId) =>
  nextRooms.map((room) => {
    const existingRoom = currentRooms.find((current) => current.id === room.id)
    if (!existingRoom) return room

    return {
      ...room,
      unreadCount:
        activeChatRoomId === room.id
          ? room.unreadCount || 0
          : Math.max(room.unreadCount || 0, existingRoom.unreadCount || 0),
    }
  })

const useMessagingStore = create((set, get) => ({
  isOpen: false,
  activeTab: 'chat',
  activeOverlay: null,
  activeChatRoomId: null,
  sharePayload: null,

  chatRooms: [],
  announcements: [],
  announcementSeenAt: localStorage.getItem('announcementSeenAt') || null,
  members: [],
  loadingRooms: false,
  loadingAnnouncements: false,

  togglePanel: () =>
    set((state) => {
      const next = !state.isOpen
      return { isOpen: next, ...(next ? {} : { activeOverlay: null, activeChatRoomId: null }) }
    }),
  openPanel: () => set({ isOpen: true }),
  closePanel: () => set({ isOpen: false, activeOverlay: null, activeChatRoomId: null }),
  setTab: (tab) => set({ activeTab: tab, activeChatRoomId: null, activeOverlay: null }),
  openOverlay: (name) => {
    if (name === 'announcement') {
      const now = new Date().toISOString()
      localStorage.setItem('announcementSeenAt', now)
      set({ activeOverlay: name, announcementSeenAt: now })
    }
    else set({ activeOverlay: name })
  },
  closeOverlay: () => set({ activeOverlay: null }),
  enterChat: (roomId) => set({ activeChatRoomId: roomId, activeOverlay: null }),
  openChatRoom: (roomId) =>
    set({ isOpen: true, activeTab: 'chat', activeOverlay: null, activeChatRoomId: roomId }),
  exitChat: () => set({ activeChatRoomId: null }),

  openContentShare: (payload) =>
    set({
      isOpen: true,
      activeOverlay: 'contentShare',
      sharePayload: payload,
    }),

  fetchRooms: async (clubId) => {
    if (get().loadingRooms) return

    set({ loadingRooms: true })
    try {
      const data = await api.get(`/clubs/${clubId}/chat/rooms`)
      set((state) => ({
        chatRooms: mergeUnreadCounts(data || [], state.chatRooms, state.activeChatRoomId),
        loadingRooms: false,
      }))
    } catch {
      set({ loadingRooms: false })
      showError('채팅방 로드 실패', '채팅방 목록을 불러오지 못했습니다.')
    }
  },

  createRoom: async (clubId, request) => {
    try {
      const data = await api.post(`/clubs/${clubId}/chat/rooms`, request)
      get().fetchRooms(clubId)
      return data
    } catch {
      showError('채팅방 생성 실패', '채팅방을 만들지 못했습니다.')
    }
  },

  joinLightning: async (clubId, roomId) => {
    try {
      await api.post(`/clubs/${clubId}/chat/rooms/${roomId}/join`)
      get().fetchRooms(clubId)
    } catch (error) {
      const message = error?.response?.data?.message || '입장에 실패했습니다.'
      showError('입장 실패', message)
    }
  },

  leaveRoom: async (clubId, roomId) => {
    try {
      await api.post(`/clubs/${clubId}/chat/rooms/${roomId}/leave`)
      set({ activeChatRoomId: null })
      get().fetchRooms(clubId)
    } catch {
      showError('나가기 실패', '채팅방 나가기에 실패했습니다.')
    }
  },

  markRead: async (clubId, roomId) => {
    set((state) => ({
      chatRooms: state.chatRooms.map((room) =>
        room.id === roomId ? { ...room, unreadCount: 0 } : room
      ),
    }))

    try {
      await api.put(`/clubs/${clubId}/chat/rooms/${roomId}/read`)
    } catch {
      // Keep the UI responsive even if the read sync fails once.
    }
  },

  incrementRoomUnread: (roomId) => {
    set((state) => ({
      chatRooms: state.chatRooms.map((room) =>
        room.id === roomId
          ? { ...room, unreadCount: (room.unreadCount || 0) + 1 }
          : room
      ),
    }))
  },

  shareContent: async (clubId, request) => {
    try {
      await api.post(`/clubs/${clubId}/chat/share`, request)
      set({ activeOverlay: null, sharePayload: null })
    } catch {
      showError('공유 실패', '콘텐츠 공유에 실패했습니다.')
    }
  },

  fetchAnnouncements: async (clubId) => {
    set({ loadingAnnouncements: true })
    try {
      const data = await api.get(`/clubs/${clubId}/announcements`)
      set({ announcements: data || [], loadingAnnouncements: false })
    } catch {
      set({ loadingAnnouncements: false })
      showError('공지 로드 실패', '공지사항을 불러오지 못했습니다.')
    }
  },

  createAnnouncement: async (clubId, request) => {
    try {
      const data = await api.post(`/clubs/${clubId}/announcements`, request)
      get().fetchAnnouncements(clubId)
      return data
    } catch {
      showError('공지 작성 실패', '공지사항 작성에 실패했습니다.')
    }
  },

  updateAnnouncement: async (clubId, id, request) => {
    try {
      const data = await api.put(`/clubs/${clubId}/announcements/${id}`, request)
      get().fetchAnnouncements(clubId)
      return data
    } catch {
      showError('공지 수정 실패', '공지사항 수정에 실패했습니다.')
    }
  },

  deleteAnnouncement: async (clubId, id) => {
    try {
      await api.delete(`/clubs/${clubId}/announcements/${id}`)
      get().fetchAnnouncements(clubId)
    } catch {
      showError('공지 삭제 실패', '공지사항 삭제에 실패했습니다.')
    }
  },

  togglePin: async (clubId, id) => {
    try {
      await api.put(`/clubs/${clubId}/announcements/${id}/pin`)
      get().fetchAnnouncements(clubId)
    } catch {
      showError('고정 실패', '공지 고정 전환에 실패했습니다.')
    }
  },

  fetchMembers: async (clubId) => {
    try {
      const data = await api.get(`/clubs/${clubId}/members`)
      set({ members: data || [] })
    } catch {
      showError('멤버 로드 실패', '멤버 목록을 불러오지 못했습니다.')
    }
  },

  startDmWith: async (clubId, membershipId) => {
    const { createRoom } = get()
    try {
      const room = await createRoom(clubId, {
        type: 'DM',
        memberIds: [membershipId],
      })

      if (room?.id) {
        set({ isOpen: true, activeTab: 'chat', activeChatRoomId: room.id, activeOverlay: null })
      }
    } catch (error) {
      handleApiError(error, 'DM 생성')
    }
  },

  totalUnread: () => {
    const { chatRooms } = get()
    return chatRooms.reduce((sum, room) => sum + (room.unreadCount || 0), 0)
  },

  reset: () =>
    set({
      isOpen: false,
      activeTab: 'chat',
      activeOverlay: null,
      activeChatRoomId: null,
      chatRooms: [],
      announcements: [],
      members: [],
      sharePayload: null,
      loadingRooms: false,
      loadingAnnouncements: false,
    }),
}))

export default useMessagingStore
