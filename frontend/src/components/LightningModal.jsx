import { useState } from 'react'
import { X, Zap } from 'lucide-react'
import useMessagingStore from '../store/messagingStore'
import useClubStore from '../store/clubStore'
import handleApiError from '../lib/handleApiError'

export default function LightningModal({ onClose }) {
  const createRoom = useMessagingStore(s => s.createRoom)
  const enterChat = useMessagingStore(s => s.enterChat)
  const clubInfo = useClubStore(s => s.clubInfo)
  const clubId = clubInfo?.clubId

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [maxMembers, setMaxMembers] = useState('')
  const [joinMode, setJoinMode] = useState('FREE')
  const [saving, setSaving] = useState(false)

  const handleCreate = async () => {
    if (!name.trim() || !clubId) return
    setSaving(true)
    try {
      const room = await createRoom(clubId, {
        type: 'LIGHTNING',
        name: name.trim(),
        description: description.trim() || null,
        maxMembers: maxMembers ? parseInt(maxMembers) : null,
        joinMode,
      })
      if (room?.id) enterChat(room.id)
      onClose()
    } catch (e) {
      handleApiError(e, '오픈채팅방 생성')
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center bg-black/30" onClick={onClose}>
      <div
        className="bg-white rounded-2xl w-[340px] p-5 shadow-xl"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Zap className="w-5 h-5 text-yellow-500" />
            <h3 className="text-base font-semibold text-slate-900">오픈채팅 만들기</h3>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-slate-100 rounded cursor-pointer">
            <X className="w-4 h-4 text-slate-400" />
          </button>
        </div>

        <div className="space-y-3">
          <div>
            <label className="block text-xs text-slate-500 mb-1">방 이름 *</label>
            <input
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="예: 금요일 치킨 모임"
              className="w-full text-sm bg-slate-50 rounded-lg px-3 py-2 outline-none focus:ring-1 focus:ring-primary/30"
            />
          </div>

          <div>
            <label className="block text-xs text-slate-500 mb-1">멘트 (선택)</label>
            <input
              value={description}
              onChange={e => setDescription(e.target.value)}
              placeholder="간단한 설명"
              className="w-full text-sm bg-slate-50 rounded-lg px-3 py-2 outline-none focus:ring-1 focus:ring-primary/30"
            />
          </div>

          <div>
            <label className="block text-xs text-slate-500 mb-1">최대 인원 (선택)</label>
            <select
              value={maxMembers}
              onChange={e => setMaxMembers(e.target.value)}
              className="w-full text-sm bg-slate-50 rounded-lg px-3 py-2 outline-none focus:ring-1 focus:ring-primary/30"
            >
              <option value="">제한 없음</option>
              {[3, 4, 5, 6, 8, 10, 15, 20].map(n => (
                <option key={n} value={n}>{n}명</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs text-slate-500 mb-1">입장 방식</label>
            <div className="flex gap-3">
              <label className="flex items-center gap-1.5 text-sm text-slate-700 cursor-pointer">
                <input type="radio" value="FREE" checked={joinMode === 'FREE'} onChange={() => setJoinMode('FREE')} />
                자유 입장
              </label>
              <label className="flex items-center gap-1.5 text-sm text-slate-700 cursor-pointer">
                <input type="radio" value="APPROVAL" checked={joinMode === 'APPROVAL'} onChange={() => setJoinMode('APPROVAL')} />
                승인 필요
              </label>
            </div>
          </div>
        </div>

        <button
          onClick={handleCreate}
          disabled={!name.trim() || saving}
          className="w-full mt-4 py-2.5 bg-primary text-white text-sm font-medium rounded-lg
                     cursor-pointer disabled:opacity-50 hover:bg-primary/90 transition-colors"
        >
          {saving ? '생성중...' : '오픈채팅 생성'}
        </button>
      </div>
    </div>
  )
}
