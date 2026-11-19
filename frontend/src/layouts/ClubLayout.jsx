import { useEffect, useState, useRef, createContext, useContext } from 'react'
import { createPortal } from 'react-dom'
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { Menu, Megaphone, Bell, LogOut } from 'lucide-react'
import useAuthStore from '../store/authStore'
import useClubStore from '../store/clubStore'
import useMessagingStore from '../store/messagingStore'
import useNotificationStore from '../store/notificationStore'
import api from '../lib/api'
import AiJobTracker from '../components/AiJobTracker'
import ToastStack from '../components/ToastStack'
import FloatingChatIcon from '../components/FloatingChatIcon'
import MessagingPanel, { MessagingContent } from '../components/MessagingPanel'
import useWebSocket from '../hooks/useWebSocket'
import useScrollDirection from '../hooks/useScrollDirection'
import handleApiError from '../lib/handleApiError'
import { useGuardedNavigate, useUnsavedChanges } from '../components/UnsavedChangesProvider'
import InstallPrompt from '../components/InstallPrompt'
import PushPermissionBanner from '../components/PushPermissionBanner'

/** 모바일 사이드바 토글 컨텍스트 */
const MobileSidebarContext = createContext({ open: false, setOpen: () => {} })
export function useMobileSidebar() { return useContext(MobileSidebarContext) }

export default function ClubLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const guardedNavigate = useGuardedNavigate()
  const { confirmIfDirty } = useUnsavedChanges()
  const logout = useAuthStore(s => s.logout)
  const headerHidden = useScrollDirection()

  // 페이지 전환 시 스크롤 리셋 + 모바일 메시징 닫기
  const [mobileMessaging, setMobileMessaging] = useState(null) // 'chat' | 'member' | null
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)

  useEffect(() => {
    window.scrollTo(0, 0)
    setMobileMessaging(null)
    setMobileSidebarOpen(false)
  }, [location.pathname])

  const { clubInfo, loading, error, fetchMyClub } = useClubStore()
  const [logoError, setLogoError] = useState(false)

  // 메시징 상태 (모바일 헤더 아이콘용)
  const openOverlay = useMessagingStore(s => s.openOverlay)
  const announcements = useMessagingStore(s => s.announcements)
  const announcementSeenAt = useMessagingStore(s => s.announcementSeenAt)
  const chatRooms = useMessagingStore(s => s.chatRooms)
  const notifUnread = useNotificationStore(s => s.unreadCount)

  const chatUnread = chatRooms.reduce((sum, r) => sum + (r.unreadCount || 0), 0)
  const announcementUnread = announcementSeenAt
    ? announcements.filter(a => new Date(a.createdAt) > new Date(announcementSeenAt)).length
    : announcements.length

  useWebSocket(clubInfo?.clubId)

  useEffect(() => {
    fetchMyClub().catch(e => handleApiError(e, '동아리 정보 조회'))
  }, [])

  // 클럽 진입 시 접속 기록 (1일 1회)
  useEffect(() => {
    if (!clubInfo?.clubId) return
    api.post(`/clubs/${clubInfo.clubId}/access-log`).catch(() => {})
  }, [clubInfo?.clubId])

  // clubInfo 변경 시 에러 상태 리셋
  useEffect(() => { setLogoError(false) }, [clubInfo?.clubLogoUrl])

  if (loading || !clubInfo) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="animate-spin w-8 h-8 border-4 border-primary border-t-transparent rounded-full" />
      </div>
    )
  }

  if (error) {
    const user = useAuthStore.getState().user
    const isSuperAdmin = user?.superAdmin === true
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="text-center">
          <p className="text-sm text-red-500 mb-2">{isSuperAdmin ? '시스템 관리자는 동아리에 소속되어 있지 않습니다' : error}</p>
          {isSuperAdmin ? (
            <div className="flex gap-3 justify-center">
              <button
                onClick={() => guardedNavigate('/admin')}
                className="text-sm text-blue-500 hover:text-blue-700 cursor-pointer"
              >
                관리자 페이지로 이동
              </button>
              <button
                onClick={() => confirmIfDirty(() => { logout(); navigate('/login') })}
                className="text-sm text-slate-500 hover:text-slate-700 cursor-pointer"
              >
                로그아웃
              </button>
            </div>
          ) : (
            <button
              onClick={() => confirmIfDirty(() => { logout(); navigate('/login') })}
              className="text-sm text-slate-500 hover:text-slate-700 cursor-pointer"
            >
              로그아웃
            </button>
          )}
        </div>
      </div>
    )
  }

  const isAdmin = clubInfo.isExecutive
  const fundTeam = clubInfo.myTeams?.find(t => t.teamType === 'FUND')
  const showLogo = clubInfo.clubLogoUrl && !logoError

  // 사이드바가 없는 페이지에서는 모바일 햄버거 숨김
  const hideHamburger = mobileMessaging
    || location.pathname === '/club'
    || location.pathname.startsWith('/club/jongtobang')
    || location.pathname === '/club/my'
    || location.pathname.startsWith('/club/admin')

  return (
    <MobileSidebarContext.Provider value={{ open: mobileSidebarOpen, setOpen: setMobileSidebarOpen }}>
      <div className="min-h-screen flex flex-col bg-[#FAF8F5]">
        {/* 헤더 + 탭: sticky, 모바일에서 스크롤 시 숨김 */}
        <div className={`sticky top-0 z-30 bg-white transition-transform duration-300
                         ${headerHidden ? '-translate-y-full md:translate-y-0' : 'translate-y-0'}`}>
          <header className="border-b border-slate-100">
            <div className="max-w-5xl mx-auto px-4 py-4 flex items-center justify-between">
              {/* 좌측: 모바일 햄버거 + 로고 */}
              <div className="flex items-center gap-3">
                {!hideHamburger && (
                  <button
                    onClick={() => setMobileSidebarOpen(prev => !prev)}
                    className="md:hidden p-1 -ml-1 hover:bg-slate-100 rounded cursor-pointer"
                    aria-label="사이드바 토글"
                  >
                    <Menu className="w-5 h-5 text-slate-600" />
                  </button>
                )}
                {showLogo ? (
                  <img
                    src={clubInfo.clubLogoUrl}
                    alt={clubInfo.clubName}
                    className="w-9 h-9 rounded-xl object-cover"
                    onError={() => setLogoError(true)}
                  />
                ) : (
                  <div className="w-9 h-9 bg-primary rounded-xl flex items-center justify-center">
                    <span className="text-white text-sm font-bold">{clubInfo.clubName?.charAt(0) || 'F'}</span>
                  </div>
                )}
                <div className="hidden md:block">
                  <h1 className="text-lg font-bold text-slate-900 scandi-heading">{clubInfo.clubName}</h1>
                  <p className="text-xs text-slate-500">{clubInfo.schoolName}</p>
                </div>
                {/* 모바일: 클럽명만 간결하게 */}
                <h1 className="md:hidden text-base font-bold text-slate-900 scandi-heading truncate max-w-[120px]">
                  {clubInfo.clubName}
                </h1>
              </div>

              {/* 우측: 데스크톱 — 이름+로그아웃, 모바일 — 📢🔔👤 아이콘 */}
              <div className="flex items-center gap-2">
                {/* 모바일 전용 아이콘 */}
                <button
                  onClick={() => { setMobileMessaging('chat'); openOverlay('announcement') }}
                  className="md:hidden relative p-2 hover:bg-slate-100 rounded-lg cursor-pointer"
                >
                  <Megaphone className="w-4 h-4 text-slate-500" />
                  {announcementUnread > 0 && (
                    <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-orange-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                      {announcementUnread > 9 ? '9+' : announcementUnread}
                    </span>
                  )}
                </button>
                <button
                  onClick={() => { setMobileMessaging('chat'); openOverlay('notification') }}
                  className="md:hidden relative p-2 hover:bg-slate-100 rounded-lg cursor-pointer"
                >
                  <Bell className="w-4 h-4 text-slate-500" />
                  {notifUnread > 0 && (
                    <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                      {notifUnread > 9 ? '9+' : notifUnread}
                    </span>
                  )}
                </button>
                <button
                  onClick={() => confirmIfDirty(() => { logout(); navigate('/login', { replace: true }) })}
                  className="md:hidden p-2 hover:bg-slate-100 rounded-lg cursor-pointer"
                  aria-label="로그아웃"
                >
                  <LogOut className="w-4 h-4 text-slate-500" />
                </button>

                {/* 데스크톱 전용 */}
                <button
                  onClick={() => guardedNavigate('/club/my')}
                  className="hidden md:block text-xs text-slate-500 hover:text-slate-700 transition-colors cursor-pointer"
                >
                  {clubInfo.myName}
                </button>
                <button
                  onClick={() => confirmIfDirty(() => { logout(); navigate('/login', { replace: true }) })}
                  className="hidden md:block text-sm text-slate-500 hover:text-slate-700 transition-colors cursor-pointer"
                >
                  로그아웃
                </button>
              </div>
            </div>
          </header>

          {/* 탭 네비게이션 */}
          <div className="border-b border-slate-100">
            <div className="max-w-5xl mx-auto px-4 flex gap-1 overflow-x-auto scrollbar-hide">
              <TabLink to="/club" end onNavigate={() => setMobileMessaging(null)} forceInactive={!!mobileMessaging}>홈</TabLink>
              <TabLink to="/club/boards" onNavigate={() => setMobileMessaging(null)} forceInactive={!!mobileMessaging}>게시판</TabLink>
              <TabLink to="/club/drive" onNavigate={() => setMobileMessaging(null)} forceInactive={!!mobileMessaging}>드라이브</TabLink>
              <TabLink to="/club/jongtobang" onNavigate={() => setMobileMessaging(null)} forceInactive={!!mobileMessaging}>종토방</TabLink>
              {fundTeam && <TabLink to={`/club/fund/${fundTeam.teamId}`} onNavigate={() => setMobileMessaging(null)} forceInactive={!!mobileMessaging}>펀드팀</TabLink>}
              <MyDropdown teams={clubInfo.myTeams} onNavigate={() => setMobileMessaging(null)} forceInactive={!!mobileMessaging} />
              {isAdmin && <TabLink to="/club/admin" onNavigate={() => setMobileMessaging(null)} forceInactive={!!mobileMessaging}>관리</TabLink>}

              {/* 모바일 전용: 채팅 탭 */}
              <button
                onClick={() => setMobileMessaging(prev => prev === 'chat' ? null : 'chat')}
                className={`md:hidden relative shrink-0 px-4 py-3 text-sm font-medium border-b-2 transition-colors whitespace-nowrap cursor-pointer
                            ${mobileMessaging === 'chat' || mobileMessaging === 'member'
                              ? 'border-primary text-primary'
                              : 'border-transparent text-slate-500 hover:text-slate-700'}`}
              >
                채팅
                {chatUnread > 0 && (
                  <span className="absolute -top-0.5 right-1 w-4 h-4 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                    {chatUnread > 9 ? '9+' : chatUnread}
                  </span>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* 콘텐츠 — 모바일 메시징 활성 시 대체 */}
        {mobileMessaging ? (
          <main className="flex-1 flex flex-col min-h-0 max-w-6xl w-full mx-auto">
            <MessagingContent initialTab={mobileMessaging} fullHeight />
          </main>
        ) : (
          <main className="flex-1 max-w-6xl w-full mx-auto px-3 md:px-6 pt-4 md:pt-6 pb-24 md:pb-40">
            <Outlet context={{ clubInfo }} />
          </main>
        )}

        {/* AI 작업 추적 — 글로벌 우상단 고정 카드 */}
        <AiJobTracker />

        {/* 메시징 패널 + 플로팅 버튼 (데스크톱만) */}
        <FloatingChatIcon />
        <MessagingPanel />

        {/* 실시간 토스트 알림 */}
        <ToastStack />

        {/* iOS Safari 설치 안내 */}
        <InstallPrompt />

        {/* 푸시 알림 권한 요청 배너 */}
        <PushPermissionBanner clubId={clubInfo?.clubId} />
      </div>
    </MobileSidebarContext.Provider>
  )
}

function TabLink({ to, children, end, onNavigate, forceInactive }) {
  const guardedNavigate = useGuardedNavigate()
  const location = useLocation()

  return (
    <NavLink
      to={to}
      end={end}
      onClick={(event) => {
        if (
          event.defaultPrevented ||
          event.button !== 0 ||
          event.metaKey ||
          event.altKey ||
          event.ctrlKey ||
          event.shiftKey
        ) {
          return
        }

        event.preventDefault()
        onNavigate?.()
        if (location.pathname === to || (!end && location.pathname.startsWith(to))) return
        guardedNavigate(to)
      }}
      className={({ isActive }) =>
        `shrink-0 px-4 py-3 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
          isActive && !forceInactive
            ? 'border-primary text-primary'
            : 'border-transparent text-slate-500 hover:text-slate-700'
        }`
      }
    >
      {children}
    </NavLink>
  )
}

function MyDropdown({ teams = [], onNavigate, forceInactive }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const location = useLocation()
  const guardedNavigate = useGuardedNavigate()

  const isActive = !forceInactive && (location.pathname === '/club/my' || location.pathname.startsWith('/club/teams'))

  // portal 배경 딤이 외부 클릭 닫기를 처리하므로 별도 mousedown 리스너 불필요

  // 팀이 없으면 드롭다운 없이 단순 링크
  if (!teams || teams.length === 0) {
    return <TabLink to="/club/my" onNavigate={onNavigate} forceInactive={forceInactive}>마이</TabLink>
  }

  // 드롭다운 위치 계산 (portal로 body에 렌더하므로 절대 좌표 필요)
  const [pos, setPos] = useState({ top: 0, right: 0 })
  useEffect(() => {
    if (open && ref.current) {
      const rect = ref.current.getBoundingClientRect()
      setPos({ top: rect.bottom + 2, right: window.innerWidth - rect.right })
    }
  }, [open])

  return (
    <div ref={ref} className="relative shrink-0">
      <button
        onClick={() => setOpen(prev => !prev)}
        className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors cursor-pointer whitespace-nowrap ${
          isActive
            ? 'border-primary text-primary'
            : 'border-transparent text-slate-500 hover:text-slate-700'
        }`}
      >
        마이
      </button>
      {open && createPortal(
        <div className="fixed inset-0 z-50" onClick={() => setOpen(false)}>
          <div
            className="absolute bg-white rounded-xl shadow-lg border border-slate-200 py-1.5 min-w-[180px]"
            style={{ top: pos.top, right: pos.right, animation: 'dropdown-slide 0.2s ease-out both' }}
            onClick={e => e.stopPropagation()}
          >
            <button
              onClick={() => {
                setOpen(false)
                onNavigate?.()
                guardedNavigate('/club/my')
              }}
              className={`w-full text-left px-4 py-2.5 text-sm font-medium transition-colors cursor-pointer ${
                location.pathname === '/club/my'
                  ? 'bg-primary/10 text-primary'
                  : 'text-slate-700 hover:bg-slate-50'
              }`}
            >
              마이페이지
            </button>
            <div className="mx-3 my-1 border-t border-slate-100" />
            <div className="px-4 py-1.5 text-[11px] font-medium text-slate-400 uppercase tracking-wide">소속 팀</div>
            {teams.map(t => (
              <button
                key={t.teamId}
                onClick={() => {
                  setOpen(false)
                  onNavigate?.()
                  guardedNavigate(`/club/teams/${t.teamId}`)
                }}
                className={`w-full text-left px-4 py-2 text-sm transition-colors cursor-pointer ${
                  location.pathname === `/club/teams/${t.teamId}`
                    ? 'bg-primary/10 text-primary font-semibold'
                    : 'text-slate-600 hover:bg-slate-50'
                }`}
              >
                {t.teamName}
              </button>
            ))}
          </div>
        </div>,
        document.body
      )}
    </div>
  )
}
