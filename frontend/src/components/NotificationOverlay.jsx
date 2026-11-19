import { useEffect, useState } from 'react'
import { ArrowLeft, Settings, ArrowRight } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import useMessagingStore from '../store/messagingStore'
import useNotificationStore from '../store/notificationStore'
import useClubStore from '../store/clubStore'
import NotificationSettingsView from './NotificationSettingsView'
import { useUnsavedChanges } from './UnsavedChangesProvider'
import { isChatNotification, resolveNotificationTarget } from '../lib/notificationTargets'

const TYPE_ICONS = {
  POST_COMMENT: '💬',
  COMMENT_REPLY: '↩️',
  CONTENT_LIKED: '❤️',
  TEAM_CHANGE: '👥',
  ROLE_CHANGE: '🛡️',
  GENERATION_TRANSITION: '🔄',
  DRIVE_COMMENT: '🗂️',
  TEAM_ACTIVITY: '📌',
  NEW_NOTICE: '📢',
  DM_RECEIVED: '✉️',
  LIGHTNING_CREATED: '⚡',
}

export default function NotificationOverlay() {
  const closeOverlay = useMessagingStore((s) => s.closeOverlay)
  const openChatRoom = useMessagingStore((s) => s.openChatRoom)
  const clubInfo = useClubStore((s) => s.clubInfo)
  const {
    notifications,
    unreadCount,
    loading,
    hasMore,
    fetchNotifications,
    markAsRead,
    markAllAsRead,
  } = useNotificationStore()
  const navigate = useNavigate()
  const { confirmIfDirty } = useUnsavedChanges()
  const [showSettings, setShowSettings] = useState(false)
  const clubId = clubInfo?.clubId
  const visibleNotifications = notifications.filter((notification) => !isChatNotification(notification))

  useEffect(() => {
    if (clubId) {
      fetchNotifications(clubId, true)
    }
  }, [clubId, fetchNotifications])

  const handleClick = async (notification) => {
    if (!clubId) return

    if (!notification.isRead) {
      await markAsRead(clubId, notification.id)
    }

    const target = resolveNotificationTarget(notification.targetUrl)
    if (target.kind === 'route') {
      confirmIfDirty(() => {
        navigate(target.url)
        useMessagingStore.getState().closePanel()
      })
      return
    }

    if (target.kind === 'chat') {
      confirmIfDirty(() => {
        openChatRoom(target.roomId)
      })
    }
  }

  if (showSettings) {
    return <NotificationSettingsView onBack={() => setShowSettings(false)} />
  }

  return (
    <div className="flex-1 flex flex-col min-h-0">
      <div className="flex items-center justify-between px-3 py-2 border-b border-slate-100">
        <div className="flex items-center gap-2">
          <button onClick={closeOverlay} className="p-1 hover:bg-slate-100 rounded cursor-pointer">
            <ArrowLeft className="w-4 h-4 text-slate-500" />
          </button>
          <span className="text-sm font-semibold text-slate-900">알림</span>
          {unreadCount > 0 && (
            <span className="text-xs text-slate-400">{unreadCount}개 읽지 않음</span>
          )}
        </div>
        <div className="flex items-center gap-1">
          {unreadCount > 0 && (
            <button
              onClick={() => clubId && markAllAsRead(clubId)}
              className="text-xs text-primary hover:text-primary/80 cursor-pointer px-2 py-1"
            >
              모두 읽음
            </button>
          )}
          <button onClick={() => setShowSettings(true)} className="p-1.5 hover:bg-slate-100 rounded cursor-pointer">
            <Settings className="w-4 h-4 text-slate-400" />
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {visibleNotifications.length === 0 && !loading && (
          <div className="text-center text-sm text-slate-400 py-8">알림이 없습니다</div>
        )}

        {visibleNotifications.map((notification) => (
          <button
            key={notification.id}
            onClick={() => handleClick(notification)}
            className={`w-full text-left px-3 py-3 flex items-start gap-3 hover:bg-slate-50 cursor-pointer transition-colors border-b border-slate-50 ${
              !notification.isRead ? 'bg-blue-50/50' : ''
            }`}
          >
            <span className="text-lg flex-shrink-0 mt-0.5">
              {TYPE_ICONS[notification.type] || '🔔'}
            </span>
            <div className="flex-1 min-w-0">
              <p className={`text-sm ${!notification.isRead ? 'font-semibold text-slate-900' : 'text-slate-700'}`}>
                {notification.title}
              </p>
              {notification.content && (
                <p className="text-xs text-slate-500 mt-0.5 truncate">{notification.content}</p>
              )}
              <p className="text-xs text-slate-400 mt-1">
                {new Date(notification.createdAt).toLocaleString('ko-KR', {
                  month: 'short',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
            </div>
            {notification.targetUrl && (
              <ArrowRight className="w-3.5 h-3.5 text-slate-300 flex-shrink-0 mt-1" />
            )}
            {!notification.isRead && (
              <div className="w-2 h-2 bg-blue-500 rounded-full flex-shrink-0 mt-2" />
            )}
          </button>
        ))}

        {hasMore && (
          <button
            onClick={() => clubId && fetchNotifications(clubId)}
            className="w-full py-3 text-xs text-slate-400 hover:text-slate-600 cursor-pointer"
          >
            {loading ? '로딩중...' : '더보기'}
          </button>
        )}
      </div>
    </div>
  )
}
