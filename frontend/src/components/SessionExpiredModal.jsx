import { useEffect, useState } from 'react'

/**
 * 세션 만료 모달 — 401 수신 시 표시
 * 작성 중 데이터 손실을 방지하기 위해 즉시 리다이렉트하지 않고
 * 사용자에게 안내 후 로그인 페이지로 이동
 */
export default function SessionExpiredModal() {
  const [show, setShow] = useState(false)

  useEffect(() => {
    const handler = () => setShow(true)
    window.addEventListener('session-expired', handler)
    return () => window.removeEventListener('session-expired', handler)
  }, [])

  if (!show) return null

  const handleLogin = () => {
    // 현재 URL 저장 → 로그인 후 복원
    const current = window.location.pathname + window.location.search
    if (current && current !== '/login') {
      sessionStorage.setItem('fm_returnTo', current)
    }
    window.location.href = '/login'
  }

  return (
    <div className="fixed inset-0 z-[999] modal-overlay flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-2xl w-full max-w-sm shadow-xl animate-modal-in mx-4">
        <div className="px-6 py-5">
          <h3 className="text-base font-bold text-slate-900 mb-2">세션이 만료되었습니다</h3>
          <p className="text-sm text-slate-500 leading-relaxed">
            로그인 세션이 만료되었습니다. 작성 중인 내용이 있다면 별도로 복사해 두신 후 다시 로그인해 주세요.
          </p>
        </div>
        <div className="px-6 pb-5 flex justify-end">
          <button
            onClick={handleLogin}
            className="px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors cursor-pointer"
          >
            로그인 페이지로 이동
          </button>
        </div>
      </div>
    </div>
  )
}
