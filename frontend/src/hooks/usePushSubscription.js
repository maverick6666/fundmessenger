import { useEffect, useCallback, useState } from 'react'
import api from '../lib/api'

let vapidKeyCache = null

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  return Uint8Array.from([...rawData].map((c) => c.charCodeAt(0)))
}

export default function usePushSubscription(clubId) {
  const [permission, setPermission] = useState(
    typeof Notification !== 'undefined' ? Notification.permission : 'denied'
  )
  const [subscription, setSubscription] = useState(null)

  // SW가 등록되어 있으면 기존 구독 확인
  useEffect(() => {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) return

    navigator.serviceWorker.ready
      .then((reg) => reg.pushManager.getSubscription())
      .then((sub) => setSubscription(sub))
      .catch(() => {})
  }, [])

  // 이미 granted이고 구독이 없으면 자동 재구독 (silent)
  useEffect(() => {
    if (permission === 'granted' && !subscription && clubId) {
      silentSubscribe(clubId)
    }
  }, [permission, subscription, clubId])

  async function silentSubscribe(cid) {
    try {
      if (!vapidKeyCache) {
        const data = await api.get('/push/vapid-public-key')
        vapidKeyCache = data.key
      }
      if (!vapidKeyCache) return

      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidKeyCache),
      })

      const { endpoint, keys } = sub.toJSON()
      await api.post(`/clubs/${cid}/push/subscribe`, {
        endpoint,
        p256dh: keys.p256dh,
        auth: keys.auth,
        userAgent: navigator.userAgent,
      })

      setSubscription(sub)
    } catch {
      // silent — 에러 무시
    }
  }

  // 구독 요청 (반드시 사용자 제스처 내에서 호출)
  const subscribe = useCallback(async () => {
    if (!clubId || !('PushManager' in window)) return

    const perm = await Notification.requestPermission()
    setPermission(perm)
    if (perm !== 'granted') return

    await silentSubscribe(clubId)
  }, [clubId])

  // 구독 해제
  const unsubscribe = useCallback(async () => {
    if (!subscription || !clubId) return
    try {
      const { endpoint } = subscription.toJSON()
      await api.delete(`/clubs/${clubId}/push/unsubscribe`, { endpoint })
      await subscription.unsubscribe()
      setSubscription(null)
    } catch {
      // 에러 무시
    }
  }, [subscription, clubId])

  return { permission, subscription, subscribe, unsubscribe }
}
