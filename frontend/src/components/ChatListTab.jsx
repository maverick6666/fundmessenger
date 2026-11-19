import { useState } from 'react'
import { Plus, Zap, Hash, Users, MessageSquare, User } from 'lucide-react'
import useMessagingStore from '../store/messagingStore'
import useClubStore from '../store/clubStore'
import LightningModal from './LightningModal'

export default function ChatListTab() {
  const chatRooms = useMessagingStore(s => s.chatRooms)
  const loadingRooms = useMessagingStore(s => s.loadingRooms)
  const enterChat = useMessagingStore(s => s.enterChat)
  const [showLightning, setShowLightning] = useState(false)

  // 분류: 고정(CLUB, TEAM) vs 일반
  const pinned = chatRooms.filter(r => r.type === 'CLUB' || r.type === 'TEAM')
  const regular = chatRooms
    .filter(r => r.type !== 'CLUB' && r.type !== 'TEAM')
    .sort((a, b) => (b.unreadCount || 0) - (a.unreadCount || 0))

  const lightning = chatRooms.filter(r => r.type === 'LIGHTNING')

  return (
    <div className="flex flex-col">
      {/* 상단 액션 */}
      <div className="px-3 py-2 flex items-center justify-between">
        <span className="text-xs text-slate-400 font-medium">채팅방</span>
        <button
          onClick={() => setShowLightning(true)}
          className="flex items-center gap-1 text-xs text-primary hover:text-primary/80 cursor-pointer"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>오픈채팅</span>
        </button>
      </div>

      {loadingRooms ? (
        <div className="flex items-center justify-center py-8">
          <div className="animate-spin w-5 h-5 border-2 border-primary border-t-transparent rounded-full" />
        </div>
      ) : (
        <>
          {/* 고정 섹션 */}
          {pinned.length > 0 && (
            <div className="px-1">
              {pinned.map(room => (
                <RoomItem key={room.id} room={room} onClick={() => enterChat(room.id)} />
              ))}
            </div>
          )}

          {/* 구분선 */}
          {pinned.length > 0 && regular.length > 0 && (
            <div className="mx-3 my-1 border-t border-slate-100" />
          )}

          {/* 일반 섹션 */}
          {regular.map(room => (
            <RoomItem key={room.id} room={room} onClick={() => enterChat(room.id)} />
          ))}

          {chatRooms.length === 0 && (
            <div className="text-center text-sm text-slate-400 py-8">
              채팅방이 없습니다
            </div>
          )}
        </>
      )}

      {showLightning && <LightningModal onClose={() => setShowLightning(false)} />}
    </div>
  )
}

function RoomItem({ room, onClick }) {
  const icon = {
    CLUB: <Hash className="w-4 h-4 text-blue-500" />,
    TEAM: <Users className="w-4 h-4 text-green-500" />,
    DM: <User className="w-4 h-4 text-slate-500" />,
    GROUP: <MessageSquare className="w-4 h-4 text-purple-500" />,
    LIGHTNING: <Zap className="w-4 h-4 text-yellow-500" />,
    DISCUSSION: <MessageSquare className="w-4 h-4 text-indigo-500" />,
  }[room.type] || <MessageSquare className="w-4 h-4 text-slate-400" />

  const time = room.lastMessageAt
    ? new Date(room.lastMessageAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
    : ''
  const unreadBadge = room.unreadCount > 99 ? '99+' : room.unreadCount

  return (
    <button
      onClick={onClick}
      className="w-full px-3 py-2.5 flex items-center gap-3 hover:bg-slate-50 cursor-pointer transition-colors text-left"
    >
      <div className="w-9 h-9 rounded-full bg-slate-100 flex items-center justify-center flex-shrink-0">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-slate-900 truncate">{room.name || 'DM'}</span>
          <span className="text-xs text-slate-400 flex-shrink-0 ml-2">{time}</span>
        </div>
        <p className="text-xs text-slate-500 truncate mt-0.5">
          {room.lastMessage || (room.type === 'LIGHTNING' ? `${room.memberCount}/${room.maxMembers || '∞'}명` : '')}
        </p>
      </div>
      {room.unreadCount > 0 && (
        <span className="min-w-[20px] h-5 px-1.5 bg-red-500 text-white text-xs font-bold
                        rounded-full flex items-center justify-center flex-shrink-0">
          {unreadBadge}
        </span>
      )}
    </button>
  )
}
