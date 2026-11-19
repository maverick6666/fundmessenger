import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Users, Shield, RefreshCw, MessageSquare, Reply, Heart, Activity, Megaphone, Sparkles, Mail, X } from 'lucide-react'
import useToastStore from '../store/toastStore'
import { useUnsavedChanges } from './UnsavedChangesProvider'
import useMessagingStore from '../store/messagingStore'
import { resolveNotificationTarget } from '../lib/notificationTargets'

const ICON_MAP = {
  TEAM_CHANGE: { icon: Users, color: 'text-indigo-500', bg: 'bg-indigo-50' },
  ROLE_CHANGE: { icon: Shield, color: 'text-purple-500', bg: 'bg-purple-50' },
  GENERATION_TRANSITION: { icon: RefreshCw, color: 'text-purple-500', bg: 'bg-purple-50' },
  POST_COMMENT: { icon: MessageSquare, color: 'text-amber-500', bg: 'bg-amber-50' },
  COMMENT_REPLY: { icon: Reply, color: 'text-amber-500', bg: 'bg-amber-50' },
  DRIVE_COMMENT: { icon: MessageSquare, color: 'text-amber-500', bg: 'bg-amber-50' },
  CONTENT_LIKED: { icon: Heart, color: 'text-rose-500', bg: 'bg-rose-50' },
  TEAM_ACTIVITY: { icon: Activity, color: 'text-emerald-500', bg: 'bg-emerald-50' },
  NEW_NOTICE: { icon: Megaphone, color: 'text-blue-500', bg: 'bg-blue-50' },
  AI_GENERATION: { icon: Sparkles, color: 'text-violet-500', bg: 'bg-violet-50' },
  DM_RECEIVED: { icon: Mail, color: 'text-sky-500', bg: 'bg-sky-50' },
}

function formatRelativeTime(createdAt) {
  if (!createdAt) return ''
  const now = Date.now()
  const ts = typeof createdAt === 'number' ? createdAt : new Date(createdAt).getTime()
  const diff = Math.floor((now - ts) / 1000)
  if (diff < 10) return '방금 전'
  if (diff < 60) return `${diff}초 전`
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`
  return `${Math.floor(diff / 86400)}일 전`
}

export default function ToastCard({ toast }) {
  const navigate = useNavigate()
  const { confirmIfDirty } = useUnsavedChanges()
  const removeToast = useToastStore(s => s.removeToast)
  const openChatRoom = useMessagingStore(s => s.openChatRoom)
  const [exiting, setExiting] = useState(false)
  const [paused, setPaused] = useState(false)
  const timerRef = useRef(null)

  const { icon: Icon, color, bg } = ICON_MAP[toast.type] || ICON_MAP.POST_COMMENT

  useEffect(() => {
    if (!paused) {
      timerRef.current = setTimeout(() => {
        setExiting(true)
        setTimeout(() => removeToast(toast.toastId), 300)
      }, 5000)
    }
    return () => clearTimeout(timerRef.current)
  }, [paused, toast.toastId, removeToast])

  const handleClick = () => {
    const target = resolveNotificationTarget(toast.targetUrl)

    if (target.kind === 'route') {
      confirmIfDirty(() => {
        navigate(target.url)
        removeToast(toast.toastId)
      })
      return
    }

    if (target.kind === 'chat') {
      confirmIfDirty(() => {
        openChatRoom(target.roomId)
        removeToast(toast.toastId)
      })
      return
    }

    removeToast(toast.toastId)
  }

  const handleClose = (e) => {
    e.stopPropagation()
    setExiting(true)
    setTimeout(() => removeToast(toast.toastId), 300)
  }

  return (
    <div
      className={`w-80 bg-white rounded-2xl shadow-lg border border-slate-200 shadow-sm p-4 cursor-pointer
        transition-all duration-300 ease-out hover:shadow-xl
        ${exiting ? 'animate-toast-exit' : 'animate-toast-enter'}`}
      onClick={handleClick}
      onMouseEnter={() => { setPaused(true); clearTimeout(timerRef.current) }}
      onMouseLeave={() => setPaused(false)}
    >
      <div className="flex items-start gap-3">
        <div className={`flex-shrink-0 w-8 h-8 rounded-lg ${bg} flex items-center justify-center`}>
          <Icon className={`w-4 h-4 ${color}`} />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-slate-800 truncate">{toast.title}</p>
          {toast.content && (
            <p className="text-xs text-slate-500 mt-0.5 line-clamp-2">{toast.content}</p>
          )}
        </div>
        <div className="flex-shrink-0 flex flex-col items-end gap-1">
          <button
            onClick={handleClose}
            className="text-slate-300 hover:text-slate-500 transition-colors cursor-pointer"
          >
            <X className="w-3.5 h-3.5" />
          </button>
          <span className="text-xs text-slate-400">{formatRelativeTime(toast.createdAt)}</span>
        </div>
      </div>
    </div>
  )
}
