import { MessageCircle } from 'lucide-react'
import useMessagingStore from '../store/messagingStore'
import useNotificationStore from '../store/notificationStore'

export default function FloatingChatIcon() {
  const togglePanel = useMessagingStore(s => s.togglePanel)
  const isOpen = useMessagingStore(s => s.isOpen)
  const chatRooms = useMessagingStore(s => s.chatRooms)
  const notifUnread = useNotificationStore(s => s.unreadCount)

  const chatUnread = chatRooms.reduce((sum, r) => sum + (r.unreadCount || 0), 0)
  const total = chatUnread + notifUnread

  return (
    <button
      onClick={togglePanel}
      className={`fixed bottom-6 right-6 z-50 w-14 h-14 text-white rounded-full shadow-lg
                 transition-all hidden md:flex items-center justify-center cursor-pointer
                 ${isOpen ? 'bg-slate-700 hover:bg-slate-800' : 'bg-primary hover:bg-primary/90'}`}
      aria-label={`메시지 패널 ${isOpen ? '닫기' : '열기'}${total > 0 ? ` (미읽음 ${total > 99 ? '99+' : total}건)` : ''}`}
      aria-pressed={isOpen}
    >
      <MessageCircle className="w-6 h-6" />
      {total > 0 && (
        <span className={`absolute -top-1 -right-1 min-w-[20px] h-5 px-1 bg-red-500 text-white
                          text-xs font-bold rounded-full flex items-center justify-center
                          ${total > 0 ? 'animate-pulse' : ''}`}>
          {total > 99 ? '99+' : total}
        </span>
      )}
    </button>
  )
}
