import { useState, useEffect } from 'react'
import { ArrowLeft, Search, Send, X } from 'lucide-react'
import useMessagingStore from '../store/messagingStore'
import useClubStore from '../store/clubStore'
import handleApiError from '../lib/handleApiError'

export default function ContentShareOverlay() {
  const closeOverlay = useMessagingStore(s => s.closeOverlay)
  const chatRooms = useMessagingStore(s => s.chatRooms)
  const members = useMessagingStore(s => s.members)
  const shareContent = useMessagingStore(s => s.shareContent)
  const sharePayload = useMessagingStore(s => s.sharePayload)
  const fetchMembers = useMessagingStore(s => s.fetchMembers)
  const clubInfo = useClubStore(s => s.clubInfo)
  const clubId = clubInfo?.clubId

  const [search, setSearch] = useState('')
  const [selectedRooms, setSelectedRooms] = useState([])
  const [selectedMembers, setSelectedMembers] = useState([])
  const [sending, setSending] = useState(false)

  // #10: 마운트 시 멤버 자동 로딩
  useEffect(() => {
    if (clubId && members.length === 0) {
      fetchMembers(clubId)
    }
  }, [clubId])

  const filteredRooms = chatRooms.filter(r =>
    (r.name || '').toLowerCase().includes(search.toLowerCase())
  )
  const filteredMembers = members.filter(m =>
    m.membershipId !== clubInfo?.membershipId &&
    (m.name || '').toLowerCase().includes(search.toLowerCase())
  )

  const toggleRoom = (id) => {
    setSelectedRooms(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    )
  }

  const toggleMember = (id) => {
    setSelectedMembers(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    )
  }

  const handleSend = async () => {
    if ((selectedRooms.length + selectedMembers.length) === 0 || !clubId) return
    setSending(true)
    try {
      await shareContent(clubId, {
        targetRoomIds: selectedRooms.length > 0 ? selectedRooms : null,
        targetMemberIds: selectedMembers.length > 0 ? selectedMembers : null,
        sharedPostId: sharePayload?.sharedPostId || null,
        sharedNewsId: sharePayload?.sharedNewsId || null,
      })
    } catch (e) {
      handleApiError(e, '콘텐츠 공유')
      setSending(false)
    }
  }

  const totalSelected = selectedRooms.length + selectedMembers.length

  return (
    <div className="flex-1 flex flex-col min-h-0" role="dialog" aria-label="콘텐츠 공유 대상 선택">
      {/* 헤더 */}
      <div className="flex items-center gap-2 px-3 py-2 border-b border-slate-100">
        <button onClick={closeOverlay} className="p-1 hover:bg-slate-100 rounded cursor-pointer"
                aria-label="공유 대상 선택 닫기">
          <ArrowLeft className="w-4 h-4 text-slate-500" />
        </button>
        <span className="text-sm font-semibold text-slate-900">공유 대상 선택</span>
      </div>

      {/* 검색 */}
      <div className="px-3 py-2">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" aria-hidden="true" />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="채팅방 또는 멤버 검색"
            className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 rounded-lg outline-none focus:ring-1 focus:ring-primary/30"
            aria-label="공유 대상 검색"
          />
        </div>
      </div>

      {/* 선택된 pill 태그 */}
      {totalSelected > 0 && (
        <div className="px-3 pb-2 flex flex-wrap gap-1" aria-label="선택된 대상">
          {selectedRooms.map(id => {
            const room = chatRooms.find(r => r.id === id)
            return (
              <span key={`r-${id}`} className="inline-flex items-center gap-1 bg-blue-100 text-blue-700 text-xs px-2 py-0.5 rounded-full">
                {room?.name || 'DM'}
                <button onClick={() => toggleRoom(id)} aria-label={`${room?.name || 'DM'} 선택 해제`}
                        className="cursor-pointer">
                  <X className="w-3 h-3" />
                </button>
              </span>
            )
          })}
          {selectedMembers.map(id => {
            const member = members.find(m => m.membershipId === id)
            return (
              <span key={`m-${id}`} className="inline-flex items-center gap-1 bg-green-100 text-green-700 text-xs px-2 py-0.5 rounded-full">
                {member?.name}
                <button onClick={() => toggleMember(id)} aria-label={`${member?.name} 선택 해제`}
                        className="cursor-pointer">
                  <X className="w-3 h-3" />
                </button>
              </span>
            )
          })}
        </div>
      )}

      {/* 목록 */}
      <div className="flex-1 overflow-y-auto" role="listbox" aria-label="공유 대상 목록">
        {filteredRooms.length > 0 && (
          <>
            <div className="px-3 py-1.5 text-xs font-semibold text-slate-400 bg-slate-50/50">채팅방</div>
            {filteredRooms.map(room => (
              <label
                key={room.id}
                className="px-3 py-2 flex items-center gap-3 hover:bg-slate-50 cursor-pointer"
                role="option"
                aria-selected={selectedRooms.includes(room.id)}
              >
                <input
                  type="checkbox"
                  checked={selectedRooms.includes(room.id)}
                  onChange={() => toggleRoom(room.id)}
                  className="w-4 h-4 rounded border-slate-300 text-primary"
                  aria-label={`${room.name || 'DM'} 선택`}
                />
                <span className="text-sm text-slate-700 truncate">{room.name || 'DM'}</span>
              </label>
            ))}
          </>
        )}

        {filteredMembers.length > 0 && (
          <>
            <div className="px-3 py-1.5 text-xs font-semibold text-slate-400 bg-slate-50/50">멤버 (DM)</div>
            {filteredMembers.map(m => (
              <label
                key={m.membershipId}
                className="px-3 py-2 flex items-center gap-3 hover:bg-slate-50 cursor-pointer"
                role="option"
                aria-selected={selectedMembers.includes(m.membershipId)}
              >
                <input
                  type="checkbox"
                  checked={selectedMembers.includes(m.membershipId)}
                  onChange={() => toggleMember(m.membershipId)}
                  className="w-4 h-4 rounded border-slate-300 text-primary"
                  aria-label={`${m.name} 선택`}
                />
                <span className="text-sm text-slate-700 truncate">{m.name}</span>
              </label>
            ))}
          </>
        )}

        {filteredRooms.length === 0 && filteredMembers.length === 0 && (
          <div className="px-3 py-8 text-center text-sm text-slate-400">
            검색 결과가 없습니다
          </div>
        )}
      </div>

      {/* 보내기 버튼 */}
      <div className="px-3 py-2 border-t border-slate-100">
        <button
          onClick={handleSend}
          disabled={totalSelected === 0 || sending}
          className="w-full flex items-center justify-center gap-2 py-2.5 bg-primary text-white text-sm font-medium
                     rounded-lg cursor-pointer disabled:opacity-50 hover:bg-primary/90 transition-colors"
          aria-label={`${totalSelected}곳에 콘텐츠 공유`}
        >
          <Send className="w-4 h-4" />
          {sending ? '보내는 중...' : `${totalSelected}곳에 보내기`}
        </button>
      </div>
    </div>
  )
}
