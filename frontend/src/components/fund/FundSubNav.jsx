import { NavLink } from 'react-router-dom'
import { LayoutDashboard, BarChart3, ClipboardList, FileText, TrendingUp } from 'lucide-react'

/**
 * 펀드팀 좌측 사이드바 — v1 Sidebar 스타일
 * ClubLayout 콘텐츠 영역 안에서 좌측에 고정
 */
export default function FundSubNav({ teamId }) {
  const base = `/club/fund/${teamId}`

  return (
    <aside className="w-48 shrink-0 hidden md:block">
      <nav className="sticky top-[calc(var(--header-h)+1.5rem)] space-y-1">
        <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider px-3 mb-2">
          펀드팀
        </div>
        <SidebarLink to={base} end icon={LayoutDashboard}>대시보드</SidebarLink>
        <SidebarLink to={`${base}/positions`} icon={TrendingUp}>포지션</SidebarLink>
        <SidebarLink to={`${base}/requests`} icon={ClipboardList}>요청</SidebarLink>
        <SidebarLink to={`${base}/reports`} icon={FileText}>문서</SidebarLink>
        <SidebarLink to={`${base}/stats`} icon={BarChart3}>통계</SidebarLink>
      </nav>
    </aside>
  )
}

/** 모바일용 하단 탭 바 (md 미만에서만 표시) */
export function FundMobileNav({ teamId }) {
  const base = `/club/fund/${teamId}`
  return (
    <nav className="flex md:hidden border-b border-slate-200 mb-4 -mx-4 px-4 overflow-x-auto">
      <MobileTab to={base} end>대시보드</MobileTab>
      <MobileTab to={`${base}/positions`}>포지션</MobileTab>
      <MobileTab to={`${base}/requests`}>요청</MobileTab>
      <MobileTab to={`${base}/reports`}>문서</MobileTab>
      <MobileTab to={`${base}/stats`}>통계</MobileTab>
    </nav>
  )
}

function SidebarLink({ to, children, end, icon: Icon }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm transition-colors ${
          isActive
            ? 'bg-indigo-50 text-indigo-700 font-semibold'
            : 'text-slate-600 hover:bg-slate-50 hover:text-slate-800'
        }`
      }
    >
      {Icon && <Icon size={16} className="shrink-0" />}
      {children}
    </NavLink>
  )
}

function MobileTab({ to, children, end }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `px-3 py-2 text-sm font-medium border-b-2 transition-colors -mb-px whitespace-nowrap ${
          isActive
            ? 'border-indigo-500 text-indigo-600'
            : 'border-transparent text-slate-500 hover:text-slate-700'
        }`
      }
    >
      {children}
    </NavLink>
  )
}
