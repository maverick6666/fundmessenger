export async function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) return null

  try {
    const registration = await navigator.serviceWorker.register('/sw.js', { scope: '/' })

    // SW 업데이트 감지
    registration.addEventListener('updatefound', () => {
      const newWorker = registration.installing
      if (!newWorker) return

      newWorker.addEventListener('statechange', () => {
        if (newWorker.state === 'activated' && navigator.serviceWorker.controller) {
          // 새 SW가 활성화됨 — 페이지 새로고침 유도 가능
          console.log('[SW] 새 버전이 설치되었습니다. 새로고침하면 적용됩니다.')
        }
      })
    })

    return registration
  } catch (error) {
    console.warn('[SW] 등록 실패:', error)
    return null
  }
}
