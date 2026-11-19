import { useState, useEffect, useRef } from 'react'
import EmojiPicker from 'emoji-picker-react'
import api from '../lib/api'
import useClubStore from '../store/clubStore'

/**
 * 이모지 + 스티커 통합 피커 (2탭)
 * @param {Function} onEmojiSelect - 이모지 선택 콜백 (emoji string)
 * @param {Function} onStickerSelect - 스티커 선택 콜백 ({ id, imageUrl, packId })
 * @param {Function} onClose - 닫기 콜백
 */
export default function StickerPicker({ onEmojiSelect, onStickerSelect, onClose }) {
  const ref = useRef(null)
  const [tab, setTab] = useState('emoji')
  const [packs, setPacks] = useState([])
  const [selectedPackId, setSelectedPackId] = useState(null)
  const [stickers, setStickers] = useState([])
  const [loading, setLoading] = useState(false)
  const clubId = useClubStore(s => s.clubInfo?.clubId)

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        onClose()
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [onClose])

  // 스티커 탭 진입 시 구매한 팩 로드
  useEffect(() => {
    if (tab === 'sticker' && packs.length === 0 && clubId) {
      loadPacks()
    }
  }, [tab, clubId])

  useEffect(() => {
    if (selectedPackId && clubId) {
      loadStickers(selectedPackId)
    }
  }, [selectedPackId])

  const loadPacks = async () => {
    setLoading(true)
    try {
      const data = await api.get(`/clubs/${clubId}/stickers/purchased`)
      setPacks(data)
      if (data.length > 0) setSelectedPackId(data[0].id)
    } catch {
      // 스티커 미구매 시 빈 목록
    } finally {
      setLoading(false)
    }
  }

  const loadStickers = async (packId) => {
    try {
      const data = await api.get(`/clubs/${clubId}/stickers/purchased/${packId}`)
      setStickers(data)
    } catch {
      setStickers([])
    }
  }

  return (
    <div ref={ref} className="absolute bottom-full mb-2 right-0 z-50 bg-white border border-slate-200 rounded-xl shadow-xl overflow-hidden" style={{ width: 320 }}>
      {/* 탭 헤더 */}
      <div className="flex border-b">
        <button
          onClick={() => setTab('emoji')}
          className={`flex-1 py-2 text-xs font-medium cursor-pointer ${tab === 'emoji' ? 'text-primary border-b-2 border-primary' : 'text-slate-400'}`}
        >
          😊 이모지
        </button>
        <button
          onClick={() => setTab('sticker')}
          className={`flex-1 py-2 text-xs font-medium cursor-pointer ${tab === 'sticker' ? 'text-primary border-b-2 border-primary' : 'text-slate-400'}`}
        >
          🎨 스티커
        </button>
      </div>

      {/* 이모지 탭 */}
      {tab === 'emoji' && (
        <EmojiPicker
          onEmojiClick={(emojiData) => onEmojiSelect(emojiData.emoji)}
          searchPlaceholder="이모지 검색..."
          width={320}
          height={350}
          previewConfig={{ showPreview: false }}
          skinTonesDisabled
          lazyLoadEmojis
        />
      )}

      {/* 스티커 탭 */}
      {tab === 'sticker' && (
        <div style={{ height: 350 }}>
          {loading ? (
            <div className="flex items-center justify-center h-full text-slate-400 text-sm">불러오는 중...</div>
          ) : packs.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-slate-400 text-sm gap-2">
              <p>구매한 스티커 팩이 없습니다</p>
              <p className="text-xs">동아리 설정에서 스티커 스토어를 확인하세요</p>
            </div>
          ) : (
            <div className="flex flex-col h-full">
              {/* 팩 선택 탭 */}
              <div className="flex gap-1 px-2 py-1.5 border-b overflow-x-auto">
                {packs.map(p => (
                  <button
                    key={p.id}
                    onClick={() => setSelectedPackId(p.id)}
                    className={`shrink-0 px-2 py-1 text-xs rounded cursor-pointer ${
                      selectedPackId === p.id
                        ? 'bg-primary/10 text-primary font-medium'
                        : 'text-slate-500 hover:bg-slate-50'
                    }`}
                  >
                    {p.name}
                  </button>
                ))}
              </div>

              {/* 스티커 그리드 */}
              <div className="flex-1 overflow-y-auto p-2 grid grid-cols-4 gap-2">
                {stickers.map(s => (
                  <button
                    key={s.id}
                    onClick={() => onStickerSelect({ id: s.id, imageUrl: s.imageUrl, packId: s.packId })}
                    className="aspect-square rounded-lg hover:bg-slate-100 p-1 cursor-pointer transition-colors"
                  >
                    <img src={s.imageUrl} alt="" className="w-full h-full object-contain" />
                  </button>
                ))}
                {stickers.length === 0 && (
                  <p className="col-span-4 text-center text-xs text-slate-400 py-8">스티커가 없습니다</p>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
