import { create } from 'zustand'
import { isChatNotification } from '../lib/notificationTargets'

let toastIdCounter = 0

const useToastStore = create((set, get) => ({
  toasts: [],

  addToast: (notification) => {
    const id = ++toastIdCounter
    const toast = {
      ...notification,
      content: notification?.content ?? notification?.body ?? '',
      toastId: id,
      createdAt: Date.now(),
    }

    set((s) => {
      const next = [toast, ...s.toasts]
      if (next.length > 4) next.pop()
      return { toasts: next }
    })

    setTimeout(() => {
      get().removeToast(id)
    }, 5000)

    return id
  },

  removeToast: (toastId) => {
    set((s) => ({
      toasts: s.toasts.filter((toast) => toast.toastId !== toastId),
    }))
  },

  clearChatToasts: () => {
    set((s) => ({
      toasts: s.toasts.filter((toast) => !isChatNotification(toast)),
    }))
  },

  pauseToast: () => {
    // Hover timing is handled inside the toast component.
  },
}))

export default useToastStore
