import { useEffect } from 'react'
import { useMobileSidebar } from '../layouts/ClubLayout'

/**
 * 모바일 사이드바 오버레이 래퍼
 * - 데스크톱: children을 w-72 인라인으로 렌더 (기존 그대로)
 * - 모바일: 햄버거 클릭 시 화면 왼쪽에서 슬라이드인 오버레이
 * - 사이드바 내부 클릭 시 자동 닫힘 (링크/버튼 선택)
 */
export default function MobileSidebarOverlay({ children, className = '' }) {
  const { open, setOpen } = useMobileSidebar()

  // 오버레이 열렸을 때 스크롤 잠금
  useEffect(() => {
    if (open) {
      document.body.style.overflow = 'hidden'
      return () => { document.body.style.overflow = '' }
    }
  }, [open])

  // 사이드바 내부 클릭 시 닫기 (버튼/링크 선택 감지)
  const handleContentClick = (e) => {
    const target = e.target.closest('button, a, [role="button"]')
    if (target) setOpen(false)
  }

  return (
    <>
      {/* 데스크톱: 인라인 사이드바 */}
      <div className={`hidden md:block w-72 shrink-0 ${className}`}>
        {children}
      </div>

      {/* 모바일: 오버레이 사이드바 */}
      {open && (
        <div className="md:hidden fixed inset-0 z-40">
          {/* 배경 딤 */}
          <div className="absolute inset-0 bg-black/30" onClick={() => setOpen(false)} />
          {/* 사이드바 */}
          <div
            className="absolute left-0 top-0 bottom-0 w-72 bg-white shadow-xl animate-slide-in-left overflow-y-auto mobile-sidebar-flat"
            onClick={handleContentClick}
          >
            {children}
          </div>
        </div>
      )}
    </>
  )
}
