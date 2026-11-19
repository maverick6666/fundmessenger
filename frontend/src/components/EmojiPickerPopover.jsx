import { useEffect, useRef } from 'react'
import EmojiPicker from 'emoji-picker-react'

/**
 * 이모지 피커 팝오버 — 버튼 위에 팝업으로 표시
 * @param {Function} onSelect - 이모지 선택 콜백 (emoji string)
 * @param {Function} onClose - 닫기 콜백
 */
export default function EmojiPickerPopover({ onSelect, onClose }) {
  const ref = useRef(null)

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        onClose()
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [onClose])

  return (
    <div ref={ref} className="absolute bottom-full mb-2 right-0 z-50">
      <EmojiPicker
        onEmojiClick={(emojiData) => onSelect(emojiData.emoji)}
        searchPlaceholder="이모지 검색..."
        width={320}
        height={400}
        previewConfig={{ showPreview: false }}
        skinTonesDisabled
        lazyLoadEmojis
      />
    </div>
  )
}
