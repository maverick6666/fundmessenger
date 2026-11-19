import { useEffect, useState } from 'react'
import { Search, MessageSquare, Users } from 'lucide-react'
import useMessagingStore from '../store/messagingStore'
import useClubStore from '../store/clubStore'
import handleApiError from '../lib/handleApiError'

export default function MemberListTab() {
  const members = useMessagingStore(s => s.members)
  const fetchMembers = useMessagingStore(s => s.fetchMembers)
  const createRoom = useMessagingStore(s => s.createRoom)
  const enterChat = useMessagingStore(s => s.enterChat)
  const clubInfo = useClubStore(s => s.clubInfo)
  const clubId = clubInfo?.clubId

  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState([]) // 그룹 채팅 다중 선택

  useEffect(() => {
    if (clubId) fetchMembers(clubId)
  }, [clubId])

  const filtered = members.filter(m =>
    m.name?.toLowerCase().includes(search.toLowerCase())
  )

  // 팀별 그룹핑 — tags 배열에서 팀 정보 추출
  const byTeam = {}
  filtered.forEach(m => {
    const teamTag = (m.tags || []).find(t => t.tagType === 'TEAM_MEMBER' || t.tagType === 'TEAM_LEADER')
    const teamName = teamTag?.teamName || '미배정'
    if (!byTeam[teamName]) byTeam[teamName] = []
    byTeam[teamName].push({ ...m, teamName, roleName: teamTag?.tagType === 'TEAM_LEADER' ? '팀장' : null })
  })

  const handleDm = async (membershipId) => {
    if (!clubId) return
    try {
      const room = await createRoom(clubId, {
        type: 'DM',
        memberIds: [membershipId],
      })
      if (room?.id) enterChat(room.id)
    } catch (e) { handleApiError(e, 'DM 생성') }
  }

  const toggleSelect = (id) => {
    setSelected(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    )
  }

  const handleCreateGroup = async () => {
    if (selected.length < 2 || !clubId) return
    try {
      const room = await createRoom(clubId, {
        type: 'GROUP',
        memberIds: selected,
        name: selected.length + '명 그룹 채팅',
      })
      setSelected([])
      if (room?.id) enterChat(room.id)
    } catch (e) { handleApiError(e, '그룹 채팅 생성') }
  }

  return (
    <div className="flex flex-col flex-1">
      {/* 검색 */}
      <div className="px-3 py-2">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="이름 검색"
            className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 rounded-lg outline-none focus:ring-1 focus:ring-primary/30"
          />
        </div>
      </div>

      {/* 그룹 생성 바 */}
      {selected.length > 0 && (
        <div className="px-3 py-2 bg-blue-50 border-b border-blue-100 flex items-center justify-between">
          <span className="text-xs text-blue-700">{selected.length}명 선택됨</span>
          <div className="flex items-center gap-2">
            <button onClick={() => setSelected([])} className="text-xs text-slate-500 cursor-pointer">취소</button>
            <button
              onClick={handleCreateGroup}
              className="flex items-center gap-1 text-xs text-white bg-primary px-3 py-1 rounded-full cursor-pointer"
            >
              <Users className="w-3 h-3" />
              채팅방 만들기
            </button>
          </div>
        </div>
      )}

      {/* 멤버 목록 */}
      <div className="flex-1 overflow-y-auto">
        {Object.entries(byTeam).map(([teamName, teamMembers]) => (
          <div key={teamName}>
            <div className="px-3 py-1.5 text-xs font-semibold text-slate-400 uppercase bg-slate-50/50">
              {teamName}
            </div>
            {teamMembers.map(m => {
              const isMe = m.membershipId === clubInfo?.membershipId
              const isSelected = selected.includes(m.membershipId)
              return (
                <div
                  key={m.membershipId}
                  className={`px-3 py-2 flex items-center gap-3 hover:bg-slate-50 transition-colors
                              ${isSelected ? 'bg-blue-50' : ''}`}
                >
                  {/* 다중 선택 체크박스 */}
                  {!isMe && (
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => toggleSelect(m.membershipId)}
                      className="w-4 h-4 rounded border-slate-300 text-primary cursor-pointer"
                    />
                  )}

                  {/* 아바타 */}
                  <div className="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center flex-shrink-0">
                    {m.avatarUrl ? (
                      <img src={m.avatarUrl} alt="" className="w-full h-full rounded-full object-cover" />
                    ) : (
                      <span className="text-xs text-slate-500">{m.name?.charAt(0)}</span>
                    )}
                  </div>

                  {/* 이름 */}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-slate-900 truncate">
                      {m.name}
                      {isMe && <span className="text-xs text-slate-400 ml-1">(나)</span>}
                    </p>
                    {m.roleName && <p className="text-xs text-slate-400">{m.roleName}</p>}
                  </div>

                  {/* DM 버튼 */}
                  {!isMe && (
                    <button
                      onClick={() => handleDm(m.membershipId)}
                      className="p-1.5 hover:bg-slate-100 rounded cursor-pointer"
                      title="DM 보내기"
                    >
                      <MessageSquare className="w-4 h-4 text-slate-400" />
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        ))}

        {filtered.length === 0 && (
          <div className="text-center text-sm text-slate-400 py-8">
            {search ? '검색 결과가 없습니다' : '멤버가 없습니다'}
          </div>
        )}
      </div>
    </div>
  )
}
