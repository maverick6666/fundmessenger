import { useEffect } from 'react'
import { Megaphone, Bell } from 'lucide-react'
import useMessagingStore from '../store/messagingStore'
import useNotificationStore from '../store/notificationStore'
import useClubStore from '../store/clubStore'
import useToastStore from '../store/toastStore'
import ChatListTab from './ChatListTab'
import MemberListTab from './MemberListTab'
import ChatDetailView from './ChatDetailView'
import NotificationOverlay from './NotificationOverlay'
import AnnouncementOverlay from './AnnouncementOverlay'
import ContentShareOverlay from './ContentShareOverlay'

/**
 * 메시징 콘텐츠 — 데스크톱 패널과 모바일 메인영역에서 공유
 * @param {object} props
 * @param {'chat'|'member'} props.initialTab - 모바일에서 어떤 탭으로 열지
 * @param {boolean} props.fullHeight - true이면 flex-1로 높이 채움 (모바일용)
 */
export function MessagingContent({ initialTab, fullHeight }) {
  const activeTab = useMessagingStore(s => s.activeTab)
  const setTab = useMessagingStore(s => s.setTab)
  const activeOverlay = useMessagingStore(s => s.activeOverlay)
  const openOverlay = useMessagingStore(s => s.openOverlay)
  const activeChatRoomId = useMessagingStore(s => s.activeChatRoomId)
  const fetchRooms = useMessagingStore(s => s.fetchRooms)
  const fetchAnnouncements = useMessagingStore(s => s.fetchAnnouncements)
  const announcements = useMessagingStore(s => s.announcements)
  const notifUnread = useNotificationStore(s => s.unreadCount)
  const clearChatToasts = useToastStore(s => s.clearChatToasts)
  const clubInfo = useClubStore(s => s.clubInfo)
  const announcementSeenAt = useMessagingStore(s => s.announcementSeenAt)

  const clubId = clubInfo?.clubId

  // initialTab이 바뀌면 탭 동기화
  useEffect(() => {
    if (initialTab) setTab(initialTab)
  }, [initialTab])

  useEffect(() => {
    if (clubId) {
      fetchRooms(clubId)
      fetchAnnouncements(clubId)
      clearChatToasts()
    }
  }, [clubId, fetchRooms, fetchAnnouncements, clearChatToasts])

  const announcementUnread = announcementSeenAt
    ? announcements.filter(a => new Date(a.createdAt) > new Date(announcementSeenAt)).length
    : announcements.length

  return (
    <div className={`flex flex-col ${fullHeight ? 'flex-1 min-h-0' : 'h-full'}`}>
      {/* 헤더 — 모바일에서는 메인 헤더에 편입되므로 데스크톱 패널에서만 표시 */}
      {!fullHeight && (
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100 bg-white">
          <span className="font-semibold text-slate-900">메시지</span>
          <div className="flex items-center gap-1">
            <button
              onClick={() => openOverlay('announcement')}
              className={`relative p-2 rounded-lg hover:bg-slate-100 cursor-pointer transition-colors
                          ${activeOverlay === 'announcement' ? 'bg-blue-50 text-blue-600' : 'text-slate-500'}`}
            >
              <Megaphone className="w-4 h-4" />
              {announcementUnread > 0 && (
                <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-orange-500 text-white text-xs font-bold
                                rounded-full flex items-center justify-center">
                  {announcementUnread > 9 ? '9+' : announcementUnread}
                </span>
              )}
            </button>
            <button
              onClick={() => openOverlay('notification')}
              className={`relative p-2 rounded-lg hover:bg-slate-100 cursor-pointer transition-colors
                          ${activeOverlay === 'notification' ? 'bg-blue-50 text-blue-600' : 'text-slate-500'}`}
            >
              <Bell className="w-4 h-4" />
              {notifUnread > 0 && (
                <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-red-500 text-white text-xs font-bold
                                rounded-full flex items-center justify-center">
                  {notifUnread > 9 ? '9+' : notifUnread}
                </span>
              )}
            </button>
          </div>
        </div>
      )}

      {/* 오버레이가 활성화되면 덮기 */}
      {activeOverlay === 'notification' && <NotificationOverlay />}
      {activeOverlay === 'announcement' && <AnnouncementOverlay />}
      {activeOverlay === 'contentShare' && <ContentShareOverlay />}

      {/* 채팅 상세 뷰 */}
      {!activeOverlay && activeChatRoomId && <ChatDetailView />}

      {/* 탭 콘텐츠 (오버레이/상세 없을 때만) */}
      {!activeOverlay && !activeChatRoomId && (
        <>
          {/* 탭 버튼 */}
          <div className="flex border-b border-slate-100">
            <button
              onClick={() => setTab('chat')}
              className={`flex-1 py-2.5 text-sm font-medium text-center cursor-pointer transition-colors
                          ${activeTab === 'chat' ? 'text-primary border-b-2 border-primary' : 'text-slate-500 hover:text-slate-700'}`}
            >
              💬 채팅
            </button>
            <button
              onClick={() => setTab('member')}
              className={`flex-1 py-2.5 text-sm font-medium text-center cursor-pointer transition-colors
                          ${activeTab === 'member' ? 'text-primary border-b-2 border-primary' : 'text-slate-500 hover:text-slate-700'}`}
            >
              👥 멤버
            </button>
          </div>

          {/* 탭 콘텐츠 */}
          <div className="flex-1 overflow-y-auto">
            {activeTab === 'chat' ? <ChatListTab /> : <MemberListTab />}
          </div>
        </>
      )}
    </div>
  )
}

/**
 * 데스크톱 플로팅 메시징 패널 (기존 그대로)
 */
export default function MessagingPanel() {
  const isOpen = useMessagingStore(s => s.isOpen)

  if (!isOpen) return null

  return (
    <div className="fixed bottom-24 right-6 z-40 w-[380px] h-[600px] bg-white rounded-2xl shadow-2xl border border-slate-200
                    hidden md:flex flex-col overflow-hidden transition-transform duration-300"
         style={{ transform: isOpen ? 'translateY(0)' : 'translateY(100%)' }}
         role="dialog"
         aria-label="메시징 패널"
         aria-modal="false">
      <MessagingContent />
    </div>
  )
}
