import { useEffect, useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import useNotificationStore from '../store/notificationStore'
import useToastStore from '../store/toastStore'
import useChatStore from '../store/chatStore'
import useMessagingStore from '../store/messagingStore'
import { getNotificationRoomId, isChatNotification } from '../lib/notificationTargets'
import { BASE_URL } from '../lib/api'

const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws'

let stompClient = null
const chatSubscriptions = {}

export function subscribeChatRoom(roomId) {
  if (!stompClient?.connected || chatSubscriptions[roomId]) return

  chatSubscriptions[roomId] = stompClient.subscribe(`/topic/chat/${roomId}`, (message) => {
    try {
      const msg = JSON.parse(message.body)

      if (msg.type === 'POLL_UPDATE') {
        useChatStore.getState().updatePollInMessages(roomId, msg.pollId, msg.pollData)
        return
      }

      useChatStore.getState().addMessage(roomId, msg)

      const { activeChatRoomId, incrementRoomUnread } = useMessagingStore.getState()
      if (activeChatRoomId !== roomId) {
        incrementRoomUnread(roomId)
      }
    } catch (error) {
      console.error('채팅 메시지 파싱 실패:', error)
    }
  })
}

export function unsubscribeChatRoom(roomId) {
  const subscription = chatSubscriptions[roomId]
  if (!subscription) return

  subscription.unsubscribe()
  delete chatSubscriptions[roomId]
}

export default function useWebSocket(clubId) {
  const clientRef = useRef(null)
  const reconnectAttempts = useRef(0)
  const maxReconnectAttempts = 10

  const addNotification = useNotificationStore((s) => s.addNotification)
  const fetchUnreadCount = useNotificationStore((s) => s.fetchUnreadCount)
  const addToast = useToastStore((s) => s.addToast)

  const connect = useCallback(() => {
    const token = localStorage.getItem('fm_token')
    if (!token || !clubId) return

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 0,
      onConnect: () => {
        reconnectAttempts.current = 0
        stompClient = client

        client.subscribe('/user/queue/notifications', (message) => {
          try {
            const notification = JSON.parse(message.body)

            if (isChatNotification(notification)) {
              const roomId = getNotificationRoomId(notification)
              const messagingState = useMessagingStore.getState()

              if (typeof roomId === 'number') {
                if (messagingState.isOpen && messagingState.activeChatRoomId === roomId) {
                  messagingState.markRead(clubId, roomId)
                } else {
                  const roomExists = messagingState.chatRooms.some((room) => room.id === roomId)
                  if (roomExists) {
                    messagingState.incrementRoomUnread(roomId)
                  } else {
                    messagingState.fetchRooms(clubId)
                  }
                }
              }

              if (!messagingState.isOpen) {
                addToast(notification)
              }
              return
            }

            addNotification(notification)
            addToast(notification)
          } catch (error) {
            console.error('알림 파싱 실패:', error)
          }
        })

        fetchUnreadCount(clubId)
        useMessagingStore.getState().fetchRooms(clubId)
      },
      onDisconnect: () => {
        stompClient = null
        scheduleReconnect()
      },
      onStompError: (frame) => {
        console.error('STOMP 오류:', frame.headers?.message)
        scheduleReconnect()
      },
      onWebSocketError: () => {
        scheduleReconnect()
      },
    })

    client.activate()
    clientRef.current = client
  }, [clubId, addNotification, addToast, fetchUnreadCount])

  const scheduleReconnect = useCallback(() => {
    if (reconnectAttempts.current >= maxReconnectAttempts) return

    reconnectAttempts.current += 1
    const delay = Math.min(5000 * Math.pow(1.5, reconnectAttempts.current - 1), 30000)

    setTimeout(() => {
      if (clientRef.current && !clientRef.current.connected) {
        connect()
      }
    }, delay)
  }, [connect])

  const disconnect = useCallback(() => {
    Object.keys(chatSubscriptions).forEach((roomId) => {
      chatSubscriptions[roomId]?.unsubscribe()
      delete chatSubscriptions[roomId]
    })

    stompClient = null
    if (clientRef.current) {
      clientRef.current.deactivate()
      clientRef.current = null
    }
  }, [])

  useEffect(() => {
    connect()
    return () => disconnect()
  }, [connect, disconnect])

  return { disconnect }
}
