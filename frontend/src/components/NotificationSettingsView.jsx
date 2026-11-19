import { useEffect, useState } from 'react'
import { ArrowLeft, Bell, BellOff, BellRing } from 'lucide-react'
import api from '../lib/api'
import useClubStore from '../store/clubStore'
import handleApiError from '../lib/handleApiError'
import usePushSubscription from '../hooks/usePushSubscription'

const NOTIFICATION_GROUPS = [
  {
    label: '필수 알림 (끌 수 없음)',
    items: [
      { type: 'TEAM_CHANGE', label: '팀 배정/해제', required: true },
      { type: 'ROLE_CHANGE', label: '직책 변경', required: true },
      { type: 'GENERATION_TRANSITION', label: '기수 전환', required: true },
    ],
  },
  {
    label: '게시판/드라이브',
    items: [
      { type: 'POST_COMMENT', label: '내 글에 댓글' },
      { type: 'COMMENT_REPLY', label: '내 댓글에 답글' },
      { type: 'CONTENT_LIKED', label: '좋아요' },
      { type: 'DRIVE_COMMENT', label: '드라이브 코멘트' },
    ],
  },
  {
    label: '메시징',
    items: [
      { type: 'NEW_NOTICE', label: '공지사항' },
      { type: 'DM_RECEIVED', label: 'DM 수신' },
      { type: 'LIGHTNING_CREATED', label: '번개 생성' },
    ],
  },
]

export default function NotificationSettingsView({ onBack }) {
  const clubInfo = useClubStore(s => s.clubInfo)
  const clubId = clubInfo?.clubId
  const [settings, setSettings] = useState({})
  const [loading, setLoading] = useState(true)
  const { permission, subscription, subscribe, unsubscribe } = usePushSubscription(clubId)
  const hasPushSupport = typeof window !== 'undefined' && 'PushManager' in window

  useEffect(() => {
    if (!clubId) return
    setLoading(true)
    api.get(`/clubs/${clubId}/notifications/settings`)
      .then(data => {
        const map = {}
        ;(data || []).forEach(s => { map[s.type] = s.enabled })
        setSettings(map)
      })
      .catch(e => handleApiError(e, '알림 설정 조회'))
      .finally(() => setLoading(false))
  }, [clubId])

  const toggle = async (type) => {
    const current = settings[type] !== false
    const next = !current
    setSettings(s => ({ ...s, [type]: next }))
    try {
      await api.put(`/clubs/${clubId}/notifications/settings`, {
        settings: [{ type, enabled: next }],
      })
    } catch (e) {
      handleApiError(e, '알림 설정 변경')
      setSettings(s => ({ ...s, [type]: current }))
    }
  }

  return (
    <div className="flex-1 flex flex-col min-h-0">
      <div className="flex items-center gap-2 px-3 py-2 border-b border-slate-100">
        <button onClick={onBack} className="p-1 hover:bg-slate-100 rounded cursor-pointer">
          <ArrowLeft className="w-4 h-4 text-slate-500" />
        </button>
        <span className="text-sm font-semibold text-slate-900">알림 설정</span>
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-2">
        {/* 푸시 알림 섹션 */}
        {hasPushSupport && (
          <div className="mb-4 p-3 bg-slate-50 rounded-lg">
            <p className="text-xs font-semibold text-slate-400 uppercase mb-2">푸시 알림</p>
            {permission === 'denied' ? (
              <div className="flex items-center gap-2 text-sm text-slate-500">
                <BellOff className="w-4 h-4 text-red-400" />
                <span>브라우저 설정에서 알림을 허용해주세요</span>
              </div>
            ) : permission === 'granted' && subscription ? (
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm text-green-700">
                  <BellRing className="w-4 h-4" />
                  <span>푸시 알림 활성</span>
                </div>
                <button
                  onClick={unsubscribe}
                  className="text-xs text-slate-400 hover:text-red-500 cursor-pointer transition-colors"
                >
                  끄기
                </button>
              </div>
            ) : (
              <button
                onClick={subscribe}
                className="flex items-center gap-2 text-sm text-primary hover:text-primary/80 cursor-pointer transition-colors"
              >
                <Bell className="w-4 h-4" />
                <span>푸시 알림 켜기</span>
              </button>
            )}
            <p className="text-xs text-slate-400 mt-1">앱을 닫아도 새 메시지, 댓글 알림을 받을 수 있습니다</p>
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center py-8">
            <div className="animate-spin w-5 h-5 border-2 border-primary border-t-transparent rounded-full" />
          </div>
        ) : (
          NOTIFICATION_GROUPS.map(group => (
            <div key={group.label} className="mb-4">
              <p className="text-xs font-semibold text-slate-400 uppercase mb-2">{group.label}</p>
              {group.items.map(item => {
                const enabled = item.required || settings[item.type] !== false
                return (
                  <div key={item.type} className="flex items-center justify-between py-2">
                    <span className="text-sm text-slate-700">{item.label}</span>
                    <button
                      onClick={() => !item.required && toggle(item.type)}
                      disabled={item.required}
                      className={`w-10 h-6 rounded-full transition-colors flex items-center px-0.5
                                  ${enabled ? 'bg-primary' : 'bg-slate-300'}
                                  ${item.required ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                    >
                      <div className={`w-5 h-5 bg-white rounded-full shadow transition-transform
                                      ${enabled ? 'translate-x-4' : 'translate-x-0'}`} />
                    </button>
                  </div>
                )
              })}
            </div>
          ))
        )}
      </div>
    </div>
  )
}
