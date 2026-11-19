import { create } from 'zustand'
import api from '../lib/api'
import useToastStore from './toastStore'
import { isChatNotification } from '../lib/notificationTargets'

const normalizeNotification = (notification) => ({
  ...notification,
  content: notification?.content ?? notification?.body ?? '',
  targetUrl: notification?.targetUrl ?? notification?.linkUrl ?? null,
  isRead: notification?.isRead ?? notification?.read ?? false,
})

const useNotificationStore = create((set, get) => ({
  notifications: [],
  unreadCount: 0,
  hasMore: true,
  page: 0,
  loading: false,

  fetchNotifications: async (clubId, reset = false) => {
    const { page, loading, notifications } = get()
    if (loading) return

    const currentPage = reset ? 0 : page
    set({ loading: true })

    try {
      const data = await api.get(`/clubs/${clubId}/notifications?page=${currentPage}&size=20`)
      const nextItems = (data.content || [])
        .map(normalizeNotification)
        .filter((notification) => !isChatNotification(notification))

      set({
        notifications: reset ? nextItems : [...notifications, ...nextItems],
        hasMore: !data.last,
        page: currentPage + 1,
        loading: false,
      })
    } catch {
      set({ loading: false })
      useToastStore.getState().addToast({
        type: 'ERROR',
        title: '알림 로드 실패',
        body: '알림 목록을 불러오지 못했습니다.',
      })
    }
  },

  fetchUnreadCount: async (clubId) => {
    try {
      const data = await api.get(`/clubs/${clubId}/notifications/unread-count`)
      set({ unreadCount: data.count })
    } catch {
      // Keep quiet for badge polling failures.
    }
  },

  markAsRead: async (clubId, notificationId) => {
    try {
      await api.put(`/clubs/${clubId}/notifications/${notificationId}/read`)
      set((s) => ({
        notifications: s.notifications.map((notification) =>
          notification.id === notificationId
            ? { ...normalizeNotification(notification), isRead: true }
            : normalizeNotification(notification)
        ),
        unreadCount: Math.max(0, s.unreadCount - 1),
      }))
    } catch {
      // Quiet failure to avoid interrupting navigation.
    }
  },

  markAllAsRead: async (clubId) => {
    try {
      await api.put(`/clubs/${clubId}/notifications/read-all`)
      set((s) => ({
        notifications: s.notifications.map((notification) => ({
          ...normalizeNotification(notification),
          isRead: true,
        })),
        unreadCount: 0,
      }))
    } catch {
      // Quiet failure to avoid double toasts.
    }
  },

  addNotification: (notification) => {
    const normalized = normalizeNotification(notification)
    if (isChatNotification(normalized)) return

    set((s) => ({
      notifications: [normalized, ...s.notifications],
      unreadCount: s.unreadCount + 1,
    }))
  },

  reset: () => set({ notifications: [], unreadCount: 0, hasMore: true, page: 0, loading: false }),
}))

export default useNotificationStore
