import { useState, useEffect } from 'react'
import { X, Share, PlusSquare } from 'lucide-react'

/**
 * iOS Safari에서 "홈 화면에 추가" 안내 바텀시트
 * - iOS Safari + standalone 아닌 경우에만 표시
 * - localStorage로 7일간 닫기 기억
 */
export default function InstallPrompt() {
  const [show, setShow] = useState(false)

  useEffect(() => {
    // iOS Safari 판별
    const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent)
    const isSafari = /Safari/.test(navigator.userAgent) && !/CriOS|FxiOS|Chrome/.test(navigator.userAgent)
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone

    if (!isIOS || !isSafari || isStandalone) return

    // 닫기 기록 확인 (7일)
    const dismissed = localStorage.getItem('fm_install_dismissed')
    if (dismissed) {
      const dismissedAt = new Date(dismissed)
      const daysSince = (Date.now() - dismissedAt.getTime()) / (1000 * 60 * 60 * 24)
      if (daysSince < 7) return
    }

    // 3초 후 표시 (페이지 로딩 완료 후)
    const timer = setTimeout(() => setShow(true), 3000)
    return () => clearTimeout(timer)
  }, [])

  const dismiss = () => {
    setShow(false)
    localStorage.setItem('fm_install_dismissed', new Date().toISOString())
  }

  if (!show) return null

  return (
    <div className="fixed bottom-0 left-0 right-0 z-50 p-4 bg-white border-t border-slate-200 shadow-2xl rounded-t-2xl animate-slide-up">
      <div className="flex items-start justify-between mb-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-900">펀드메신저 앱 설치</h3>
          <p className="text-xs text-slate-500 mt-0.5">홈 화면에 추가하면 푸시 알림을 받을 수 있어요</p>
        </div>
        <button onClick={dismiss} className="p-1 hover:bg-slate-100 rounded cursor-pointer">
          <X className="w-4 h-4 text-slate-400" />
        </button>
      </div>

      <div className="space-y-2.5 text-sm text-slate-600">
        <div className="flex items-center gap-3">
          <div className="w-7 h-7 bg-blue-50 rounded-lg flex items-center justify-center shrink-0">
            <Share className="w-4 h-4 text-blue-500" />
          </div>
          <span>하단의 <strong>공유</strong> 버튼을 탭하세요</span>
        </div>
        <div className="flex items-center gap-3">
          <div className="w-7 h-7 bg-blue-50 rounded-lg flex items-center justify-center shrink-0">
            <PlusSquare className="w-4 h-4 text-blue-500" />
          </div>
          <span><strong>홈 화면에 추가</strong>를 선택하세요</span>
        </div>
      </div>
    </div>
  )
}
