import { useNavigate } from 'react-router-dom'

// 팀 아이콘 색상 순환
const TEAM_COLORS = ['bg-blue-500', 'bg-emerald-500', 'bg-orange-500', 'bg-pink-500', 'bg-cyan-500']

/**
 * 바로가기 — 팀 카드 + 마이페이지 카드
 * 펜파일: 36x36 색상 아이콘(이니셜) + 팀명 + 메타정보 + →
 */
export default function HomeShortcuts({ clubInfo }) {
  const navigate = useNavigate()

  const myTeams = clubInfo?.myTeams || []

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* 헤더 */}
      <div className="px-5 py-4">
        <h3 className="text-base font-bold text-slate-900">바로가기</h3>
      </div>

      <div className="h-px bg-slate-100" />

      {/* 카드 목록 */}
      <div className="p-3 space-y-1.5">
        {/* 내 팀 목록 */}
        {myTeams.map((t, i) => (
          <button
            key={t.teamId}
            onClick={() => navigate(`/club/teams/${t.teamId}`)}
            className="w-full flex items-center gap-3 px-4 py-3 bg-slate-50 rounded-xl hover:bg-slate-100 transition-colors cursor-pointer"
          >
            <span className={`shrink-0 w-9 h-9 rounded-xl ${TEAM_COLORS[i % TEAM_COLORS.length]} flex items-center justify-center text-white text-sm font-bold`}>
              {t.teamName?.charAt(0) || 'T'}
            </span>
            <div className="flex-1 min-w-0 text-left">
              <p className="text-sm font-semibold text-slate-900 truncate">{t.teamName}</p>
              <p className="text-xs text-slate-400">
                {t.teamRole === 'LEADER' ? '팀장' : '팀원'}
              </p>
            </div>
            <span className="text-sm text-slate-400">→</span>
          </button>
        ))}

        {/* 구분선 */}
        {myTeams.length > 0 && <div className="h-px bg-slate-100 mx-2" />}

        {/* 마이페이지 */}
        <button
          onClick={() => navigate('/club/my')}
          className="w-full flex items-center gap-3 px-4 py-3 bg-slate-50 rounded-xl hover:bg-slate-100 transition-colors cursor-pointer"
        >
          <span className="shrink-0 w-9 h-9 rounded-xl bg-violet-500 flex items-center justify-center text-white text-base">
            👤
          </span>
          <div className="flex-1 min-w-0 text-left">
            <p className="text-sm font-semibold text-slate-900">마이페이지</p>
            <p className="text-xs text-slate-400">내 프로필 · 내 활동</p>
          </div>
          <span className="text-sm text-slate-400">→</span>
        </button>
      </div>
    </div>
  )
}
