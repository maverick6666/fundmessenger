import { useState, useEffect } from 'react'
import { Bell, X } from 'lucide-react'
import usePushSubscription from '../hooks/usePushSubscription'

const DISMISSED_KEY = 'fm_push_banner_dismissed'

/**
 * 푸시 알림 권한 요청 배너
 * - PushManager 지원 + permission === 'default'(아직 물어본 적 없음) 일 때만 표시
 * - 사용자가 닫으면 30일간 다시 안 뜸
 * - 사용자가 "알림 받기"를 탭하면 브라우저 권한 팝업 → 허용 시 자동 구독
 */
export default function PushPermissionBanner({ clubId }) {
  const [show, setShow] = useState(false)
  const { permission, subscribe } = usePushSubscription(clubId)

  useEffect(() => {
    if (!('PushManager' in window)) return
    if (typeof Notification === 'undefined') return
    if (Notification.permission !== 'default') return

    const dismissed = localStorage.getItem(DISMISSED_KEY)
    if (dismissed) {
      const daysSince = (Date.now() - new Date(dismissed).getTime()) / (1000 * 60 * 60 * 24)
      if (daysSince < 30) return
    }

    const timer = setTimeout(() => setShow(true), 2000)
    return () => clearTimeout(timer)
  }, [])

  // 권한 상태가 바뀌면 배너 숨김
  useEffect(() => {
    if (permission !== 'default') setShow(false)
  }, [permission])

  const dismiss = () => {
    setShow(false)
    localStorage.setItem(DISMISSED_KEY, new Date().toISOString())
  }

  const handleAllow = async () => {
    await subscribe()
    setShow(false)
  }

  if (!show) return null

  return (
    <div className="fixed bottom-4 left-4 right-4 z-50 md:left-auto md:right-6 md:max-w-sm
                    bg-white border border-slate-200 rounded-2xl shadow-xl p-4
                    animate-slide-up">
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 bg-primary/10 rounded-xl flex items-center justify-center shrink-0">
          <Bell className="w-5 h-5 text-primary" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-slate-900">알림을 받으시겠어요?</p>
          <p className="text-xs text-slate-500 mt-0.5">
            새 댓글, 메시지, 공지 등을 앱을 닫아도 받을 수 있습니다
          </p>
          <div className="flex gap-2 mt-3">
            <button
              onClick={handleAllow}
              className="px-4 py-1.5 bg-primary text-white text-xs font-medium rounded-lg
                         hover:bg-primary/90 cursor-pointer transition-colors"
            >
              알림 받기
            </button>
            <button
              onClick={dismiss}
              className="px-4 py-1.5 text-xs text-slate-400 hover:text-slate-600
                         cursor-pointer transition-colors"
            >
              나중에
            </button>
          </div>
        </div>
        <button onClick={dismiss} className="p-0.5 hover:bg-slate-100 rounded cursor-pointer shrink-0">
          <X className="w-3.5 h-3.5 text-slate-400" />
        </button>
      </div>
    </div>
  )
}
