import { precacheAndRoute, cleanupOutdatedCaches } from 'workbox-precaching'

// Workbox 프리캐시 (vite-plugin-pwa가 빌드 시 자동 주입)
precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

// ── Push 수신 ──
self.addEventListener('push', (event) => {
  let data = { title: '펀드메신저', body: '새로운 알림이 있습니다' }
  try {
    if (event.data) data = event.data.json()
  } catch {
    if (event.data) data.body = event.data.text()
  }

  // 앱이 포커스 상태이면 시스템 알림 스킵 (WebSocket 토스트가 처리)
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      const hasFocused = windowClients.some((c) => c.focused)
      if (hasFocused) return // WebSocket 토스트로 이미 표시됨

      const options = {
        body: data.body || '',
        icon: '/icons/icon-192.png',
        badge: '/icons/badge-72.png',
        tag: data.tag || 'fm-notification',
        renotify: true,
        data: { targetUrl: data.targetUrl || '/club', type: data.type },
        vibrate: [200, 100, 200],
      }

      return self.registration
        .showNotification(data.title || '펀드메신저', options)
        .then(() => {
          if ('setAppBadge' in navigator) navigator.setAppBadge()
        })
    })
  )
})

// ── 알림 클릭 ──
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = event.notification.data?.targetUrl || '/club'

  event.waitUntil(
    self.clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((windowClients) => {
        // 기존 열려있는 창이 있으면 거기로 이동
        for (const client of windowClients) {
          if (client.url.includes(self.location.origin)) {
            client.navigate(url)
            return client.focus()
          }
        }
        // 없으면 새 창 열기
        return self.clients.openWindow(url)
      })
  )
})

// ── 메시지 수신 (뱃지 리셋 등) ──
self.addEventListener('message', (event) => {
  if (event.data?.type === 'CLEAR_BADGE' && 'clearAppBadge' in navigator) {
    navigator.clearAppBadge()
  }
})
