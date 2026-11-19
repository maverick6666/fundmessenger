import { useState, useEffect, useRef } from 'react'

/**
 * 스크롤 방향 감지 — 모바일 헤더 숨김용
 * @returns {boolean} hidden - true이면 헤더를 숨겨야 함
 */
export default function useScrollDirection() {
  const [hidden, setHidden] = useState(false)
  const lastY = useRef(0)

  useEffect(() => {
    // md(768px) 이상에서는 항상 고정
    const mql = window.matchMedia('(min-width: 768px)')
    if (mql.matches) return

    const handler = () => {
      const y = window.scrollY
      setHidden(y > lastY.current && y > 60)
      lastY.current = y
    }

    window.addEventListener('scroll', handler, { passive: true })

    const onResize = () => {
      if (mql.matches) setHidden(false)
    }
    mql.addEventListener('change', onResize)

    return () => {
      window.removeEventListener('scroll', handler)
      mql.removeEventListener('change', onResize)
    }
  }, [])

  return hidden
}
