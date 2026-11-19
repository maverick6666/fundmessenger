import { useState, useEffect, useRef, useCallback } from 'react'
import {
  ResponsiveContainer, LineChart, Line, AreaChart, Area,
  BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis,
  CartesianGrid, Tooltip, Legend,
} from 'recharts'
import {
  Hash, Circle, TrendingUp, Users, Activity,
  FileText, BarChart3, Vote, ChevronDown,
} from 'lucide-react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'

/* ── 상수 ── */

const CHART_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16']

const CREDIT_TYPE_LABEL = {
  GRANT: '지급',
  DEDUCT: 'AI 사용',
  ALLOCATE: '팀 분배',
  REVOKE: '차감',
  RECLAIM: '회수',
}

const PERIOD_OPTIONS = [
  { value: 'WEEK', label: '이번 주' },
  { value: 'MONTH', label: '이번 달' },
  { value: 'SEMESTER', label: '이번 학기' },
  { value: 'ALL', label: '전체' },
]

const VIEW_ICONS = {
  numeric: Hash,
  distribution: Circle,
  comparison: Circle,
  trend: TrendingUp,
  active: Vote,
}

/* ── 유틸 ── */

function easeOutCubic(t) {
  return 1 - Math.pow(1 - t, 3)
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  // "2026-03-01" → "3/1", "2026-01" → "1월"
  const parts = dateStr.split('-')
  if (parts.length === 3) return `${Number(parts[1])}/${Number(parts[2])}`
  if (parts.length === 2) return `${Number(parts[1])}월`
  return dateStr
}

function formatWeek(dateStr) {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length >= 2) return `${Number(parts[1])}/${Number(parts[2] || 1)}`
  return dateStr
}

/* ── 서브 컴포넌트 ── */

function CountUpNumber({ value, duration = 800, suffix = '' }) {
  const ref = useRef(null)
  const prevValue = useRef(0)

  useEffect(() => {
    if (value == null) return
    const target = Number(value)
    if (isNaN(target)) {
      if (ref.current) ref.current.textContent = `${value}${suffix}`
      return
    }

    const start = prevValue.current
    const diff = target - start
    const startTime = performance.now()

    function tick(now) {
      const elapsed = now - startTime
      const progress = Math.min(elapsed / duration, 1)
      const eased = easeOutCubic(progress)
      const current = start + diff * eased

      if (ref.current) {
        // 소수점이 포함된 값은 소수 1자리, 아니면 정수
        const formatted = target % 1 !== 0
          ? current.toFixed(1)
          : Math.round(current).toLocaleString()
        ref.current.textContent = `${formatted}${suffix}`
      }

      if (progress < 1) {
        requestAnimationFrame(tick)
      } else {
        prevValue.current = target
      }
    }

    requestAnimationFrame(tick)
  }, [value, duration, suffix])

  return <span ref={ref}>0{suffix}</span>
}

function DeltaIndicator({ value, suffix = '' }) {
  if (value == null || value === 0) return null
  const positive = value > 0
  return (
    <span
      className={`inline-flex items-center text-xs font-medium animate-micro-bounce ${
        positive ? 'text-emerald-500' : 'text-rose-500'
      }`}
    >
      {positive ? '▲' : '▼'} {Math.abs(value)}{suffix}
    </span>
  )
}

function PanelSkeleton() {
  return (
    <div className="space-y-3 p-4">
      <div className="h-5 w-1/3 rounded animate-skeleton" />
      <div className="h-8 w-2/3 rounded animate-skeleton" />
      <div className="h-4 w-1/2 rounded animate-skeleton" />
      <div className="h-32 w-full rounded animate-skeleton" />
    </div>
  )
}

function DashboardPanel({ icon: Icon, title, views, activeView, onViewChange, loading, children }) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm shadow-sm overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-50">
        <div className="flex items-center gap-2">
          <Icon className="w-4 h-4 text-primary" />
          <h3 className="text-sm font-semibold text-slate-800">{title}</h3>
        </div>
        {views.length > 1 && (
          <div className="flex items-center gap-1">
            {views.map(v => {
              const VIcon = VIEW_ICONS[v.key] || Hash
              const isActive = activeView === v.key
              return (
                <button
                  key={v.key}
                  onClick={() => onViewChange(v.key)}
                  title={v.label}
                  className={`p-1.5 rounded-md transition-colors cursor-pointer ${
                    isActive
                      ? 'bg-primary text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  <VIcon className="w-3.5 h-3.5" />
                </button>
              )
            })}
          </div>
        )}
      </div>

      {/* Body */}
      {loading ? (
        <PanelSkeleton />
      ) : (
        <div key={activeView} className="animate-dash-fade-in p-4">
          {children}
        </div>
      )}
    </div>
  )
}

/* ── 차트 커스텀 Tooltip ── */

function CustomTooltip({ active, payload, label, formatter }) {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-white rounded-lg border border-slate-200 shadow-sm shadow-lg px-3 py-2 text-xs">
      <p className="text-slate-500 mb-1">{label}</p>
      {payload.map((p, i) => (
        <p key={i} className="font-medium" style={{ color: p.color }}>
          {p.name}: {formatter ? formatter(p.value) : p.value?.toLocaleString()}
        </p>
      ))}
    </div>
  )
}

/* ── StatCard ── */

function StatCard({ label, value, suffix, color = 'text-slate-900' }) {
  return (
    <div className="bg-slate-50 rounded-lg px-3 py-3 text-center">
      <p className={`text-xl font-bold ${color}`}>
        <CountUpNumber value={value} suffix={suffix} />
      </p>
      <p className="text-xs text-slate-400 mt-1">{label}</p>
    </div>
  )
}

/* ── 패널별 뷰 컴포넌트 ── */

// ━━ Member ━━

function MemberNumeric({ data }) {
  if (!data) return null
  const cards = [
    { label: '전체 멤버', value: data.total, color: 'text-slate-900' },
    { label: '활동 중', value: data.active, color: 'text-emerald-600' },
    { label: '비활동', value: data.inactive, color: 'text-slate-400' },
    { label: '이번 달 가입', value: data.newThisMonth, color: 'text-blue-600' },
    { label: '승인 대기', value: data.pending, color: 'text-amber-500' },
  ]
  return (
    <div className="grid grid-cols-3 gap-2">
      {cards.map(c => <StatCard key={c.label} {...c} />)}
    </div>
  )
}

function MemberDistribution({ data }) {
  if (!data?.generationDistribution?.length) {
    return <p className="text-sm text-slate-400 text-center py-8">기수별 데이터가 없습니다</p>
  }
  const total = data.generationDistribution.reduce((s, d) => s + d.count, 0)
  return (
    <div className="flex items-center gap-4">
      <div className="relative w-48 h-48 flex-shrink-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data.generationDistribution}
              dataKey="count"
              nameKey="name"
              innerRadius={50}
              outerRadius={75}
              paddingAngle={2}
              animationDuration={1000}
              animationEasing="ease-out"
            >
              {data.generationDistribution.map((_, i) => (
                <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
              ))}
            </Pie>
            <Tooltip content={<CustomTooltip />} />
          </PieChart>
        </ResponsiveContainer>
        {/* Center label */}
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
          <span className="text-xl font-bold text-slate-800">{total}</span>
          <span className="text-xs text-slate-400">전체</span>
        </div>
      </div>
      <div className="flex-1 space-y-1.5 min-w-0">
        {data.generationDistribution.map((d, i) => (
          <div key={d.name} className="flex items-center gap-2 text-xs">
            <span
              className="w-2.5 h-2.5 rounded-full flex-shrink-0"
              style={{ backgroundColor: CHART_COLORS[i % CHART_COLORS.length] }}
            />
            <span className="text-slate-600 truncate">{d.name}</span>
            <span className="ml-auto font-medium text-slate-800">{d.count}명</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function MemberTrend({ data }) {
  if (!data?.monthlySignups?.length) {
    return <p className="text-sm text-slate-400 text-center py-8">가입 추이 데이터가 없습니다</p>
  }
  const chartData = data.monthlySignups.map(d => ({ ...d, date: formatDate(d.date) }))
  return (
    <div className="h-52">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={chartData}>
          <defs>
            <linearGradient id="signupGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.2} />
              <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
          <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="#94a3b8" />
          <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" allowDecimals={false} />
          <Tooltip content={<CustomTooltip />} />
          <Area
            type="monotone"
            dataKey="count"
            name="가입자"
            stroke="#3b82f6"
            fill="url(#signupGrad)"
            strokeWidth={2}
            animationDuration={1000}
            animationEasing="ease-out"
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}

// ━━ Access ━━

function AccessNumeric({ data }) {
  if (!data) return null
  return (
    <div className="grid grid-cols-3 gap-3">
      <div className="bg-slate-50 rounded-lg px-3 py-4 text-center">
        <p className="text-2xl font-bold text-blue-600">
          <CountUpNumber value={data.weeklyRate} suffix="%" />
        </p>
        <p className="text-xs text-slate-400 mt-1">주간 접속률</p>
      </div>
      <div className="bg-slate-50 rounded-lg px-3 py-4 text-center">
        <p className="text-2xl font-bold text-emerald-600">
          <CountUpNumber value={data.monthlyRate} suffix="%" />
        </p>
        <p className="text-xs text-slate-400 mt-1">월간 접속률</p>
      </div>
      <div className="bg-slate-50 rounded-lg px-3 py-4 text-center">
        <p className="text-2xl font-bold text-slate-800">
          <CountUpNumber value={data.todayVisitors} suffix="명" />
        </p>
        <p className="text-xs text-slate-400 mt-1">오늘 방문자</p>
      </div>
    </div>
  )
}

function AccessTrend({ data }) {
  if (!data?.dailyActiveUsers?.length) {
    return <p className="text-sm text-slate-400 text-center py-8">접속 추이 데이터가 없습니다</p>
  }
  const chartData = data.dailyActiveUsers.map(d => ({ ...d, date: formatDate(d.date) }))
  return (
    <div className="h-52">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={chartData}>
          <defs>
            <linearGradient id="dauGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.15} />
              <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
          <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="#94a3b8" />
          <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" allowDecimals={false} />
          <Tooltip content={<CustomTooltip />} />
          <Area
            type="monotone"
            dataKey="count"
            name="DAU"
            stroke="#3b82f6"
            fill="url(#dauGrad)"
            strokeWidth={2}
            animationDuration={1000}
            animationEasing="ease-out"
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}

function AccessComparison({ data }) {
  if (!data?.teamAccessRates?.length) {
    return <p className="text-sm text-slate-400 text-center py-8">팀별 접속률 데이터가 없습니다</p>
  }
  const sorted = [...data.teamAccessRates].sort((a, b) => b.rate - a.rate)
  const getColor = (rate) => {
    if (rate > 80) return '#10b981'
    if (rate > 50) return '#f59e0b'
    return '#ef4444'
  }
  return (
    <div className="space-y-2">
      {sorted.map((team, i) => (
        <div key={i} className="flex items-center gap-3">
          <span className="text-xs text-slate-500 w-24 truncate flex-shrink-0">{team.name}</span>
          <div className="flex-1 h-5 bg-slate-100 rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-700"
              style={{
                width: `${Math.min(team.rate, 100)}%`,
                backgroundColor: getColor(team.rate),
              }}
            />
          </div>
          <span className="text-xs font-medium text-slate-700 w-12 text-right">{team.rate.toFixed(1)}%</span>
        </div>
      ))}
    </div>
  )
}

// ━━ Credit ━━

function CreditNumeric({ data }) {
  if (!data) return null
  return (
    <div className="space-y-3">
      <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-lg px-4 py-4 text-center">
        <p className="text-3xl font-bold text-blue-700">
          <CountUpNumber value={data.balance} />
        </p>
        <p className="text-xs text-blue-400 mt-1">현재 잔액</p>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <div className="bg-slate-50 rounded-lg px-3 py-3 text-center">
          <p className="text-lg font-bold text-emerald-600">
            +<CountUpNumber value={data.grantedThisMonth} />
          </p>
          <p className="text-xs text-slate-400 mt-0.5">이번 달 지급</p>
        </div>
        <div className="bg-slate-50 rounded-lg px-3 py-3 text-center">
          <p className="text-lg font-bold text-rose-500">
            -<CountUpNumber value={data.usedThisMonth} />
          </p>
          <p className="text-xs text-slate-400 mt-0.5">이번 달 사용</p>
        </div>
      </div>
    </div>
  )
}

function CreditTrend({ data }) {
  if (!data?.monthlyCreditFlow?.length) {
    return <p className="text-sm text-slate-400 text-center py-8">크레딧 흐름 데이터가 없습니다</p>
  }
  const chartData = data.monthlyCreditFlow.map(d => ({ ...d, month: formatDate(d.month) }))
  return (
    <div className="h-52">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
          <XAxis dataKey="month" tick={{ fontSize: 11 }} stroke="#94a3b8" />
          <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" />
          <Tooltip content={<CustomTooltip />} />
          <Legend
            wrapperStyle={{ fontSize: 11 }}
            iconType="circle"
            iconSize={8}
          />
          <Bar dataKey="grant" name="지급" fill="#3b82f6" stackId="a" radius={[0, 0, 0, 0]} animationDuration={1000} animationEasing="ease-out" />
          <Bar dataKey="deduct" name="사용" fill="#ef4444" stackId="a" animationDuration={1000} animationEasing="ease-out" />
          <Bar dataKey="allocate" name="분배" fill="#f59e0b" stackId="a" radius={[3, 3, 0, 0]} animationDuration={1000} animationEasing="ease-out" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function CreditDistribution({ data }) {
  if (!data?.typeDistribution?.length) {
    return <p className="text-sm text-slate-400 text-center py-8">유형별 데이터가 없습니다</p>
  }
  const mapped = data.typeDistribution.map(d => ({
    ...d,
    displayName: CREDIT_TYPE_LABEL[d.name] || d.name,
  }))
  const total = mapped.reduce((s, d) => s + d.count, 0)
  return (
    <div className="flex items-center gap-4">
      <div className="relative w-48 h-48 flex-shrink-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={mapped}
              dataKey="count"
              nameKey="displayName"
              innerRadius={50}
              outerRadius={75}
              paddingAngle={2}
              animationDuration={1000}
              animationEasing="ease-out"
            >
              {mapped.map((_, i) => (
                <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
              ))}
            </Pie>
            <Tooltip content={<CustomTooltip />} />
          </PieChart>
        </ResponsiveContainer>
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
          <span className="text-lg font-bold text-slate-800">{total.toLocaleString()}</span>
          <span className="text-xs text-slate-400">합계</span>
        </div>
      </div>
      <div className="flex-1 space-y-1.5 min-w-0">
        {mapped.map((d, i) => (
          <div key={d.name} className="flex items-center gap-2 text-xs">
            <span
              className="w-2.5 h-2.5 rounded-full flex-shrink-0"
              style={{ backgroundColor: CHART_COLORS[i % CHART_COLORS.length] }}
            />
            <span className="text-slate-600 truncate">{d.displayName}</span>
            <span className="ml-auto font-medium text-slate-800">{d.count.toLocaleString()}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

// ━━ Content ━━

function ContentNumeric({ data }) {
  if (!data) return null
  const cards = [
    { label: '게시글', value: data.posts, color: 'text-blue-600' },
    { label: '댓글', value: data.comments, color: 'text-emerald-600' },
    { label: '파일', value: data.files, color: 'text-amber-600' },
    { label: '좋아요', value: data.likes, color: 'text-rose-500' },
  ]
  return (
    <div className="grid grid-cols-2 gap-2">
      {cards.map(c => <StatCard key={c.label} {...c} />)}
    </div>
  )
}

function ContentTrend({ data }) {
  if (!data?.weeklyActivity?.length) {
    return <p className="text-sm text-slate-400 text-center py-8">주간 활동 데이터가 없습니다</p>
  }
  const chartData = data.weeklyActivity.map(d => ({ ...d, week: formatWeek(d.week) }))
  return (
    <div className="h-52">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
          <XAxis dataKey="week" tick={{ fontSize: 11 }} stroke="#94a3b8" />
          <YAxis tick={{ fontSize: 11 }} stroke="#94a3b8" allowDecimals={false} />
          <Tooltip content={<CustomTooltip />} />
          <Legend
            wrapperStyle={{ fontSize: 11 }}
            iconType="circle"
            iconSize={8}
          />
          <Line type="monotone" dataKey="posts" name="게시글" stroke="#3b82f6" strokeWidth={2} dot={false} animationDuration={1000} animationEasing="ease-out" />
          <Line type="monotone" dataKey="comments" name="댓글" stroke="#10b981" strokeWidth={2} dot={false} animationDuration={1000} animationEasing="ease-out" />
          <Line type="monotone" dataKey="files" name="파일" stroke="#f59e0b" strokeWidth={2} dot={false} animationDuration={1000} animationEasing="ease-out" />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function ContentComparison({ data }) {
  if (!data?.boardPostCounts?.length) {
    return <p className="text-sm text-slate-400 text-center py-8">게시판별 데이터가 없습니다</p>
  }
  const sorted = [...data.boardPostCounts].sort((a, b) => b.count - a.count)
  const max = sorted[0]?.count || 1
  return (
    <div className="space-y-2">
      {sorted.map((board, i) => (
        <div key={i} className="flex items-center gap-3">
          <span className="text-xs text-slate-500 w-24 truncate flex-shrink-0">{board.name}</span>
          <div className="flex-1 h-5 bg-slate-100 rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-700"
              style={{
                width: `${(board.count / max) * 100}%`,
                backgroundColor: CHART_COLORS[i % CHART_COLORS.length],
              }}
            />
          </div>
          <span className="text-xs font-medium text-slate-700 w-10 text-right">{board.count}</span>
        </div>
      ))}
    </div>
  )
}

// ━━ Poll Panel ━━

function PollActiveList({ clubId }) {
  const [polls, setPolls] = useState([])
  const [tab, setTab] = useState('OPEN')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    api.get(`/clubs/${clubId}/polls?status=${tab}`)
      .then(res => setPolls(res || []))
      .catch(() => setPolls([]))
      .finally(() => setLoading(false))
  }, [clubId, tab])

  if (loading) return <div className="space-y-3 py-4">{[1,2].map(i => <div key={i} className="h-16 rounded-lg animate-skeleton" />)}</div>

  return (
    <div>
      <div className="flex gap-1 mb-3">
        {[{ key: 'OPEN', label: '진행 중' }, { key: 'CLOSED', label: '완료' }].map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-3 py-1 text-xs rounded-full transition-colors ${tab === t.key ? 'bg-primary text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'}`}>
            {t.label}
          </button>
        ))}
      </div>
      {polls.length === 0 ? (
        <div className="text-center py-8 text-slate-400 text-sm">
          {tab === 'OPEN' ? '진행 중인 투표가 없습니다' : '완료된 투표가 없습니다'}
        </div>
      ) : (
        <div className="space-y-2">
          {polls.map(p => (
            <div key={p.id} className="border border-slate-200 shadow-sm rounded-lg p-3 hover:bg-slate-50/50 transition-colors">
              <div className="flex items-start justify-between gap-2">
                <p className="text-sm font-medium text-slate-800 line-clamp-1">📊 {p.question}</p>
                {p.closed && <span className="shrink-0 text-xs px-1.5 py-0.5 rounded bg-slate-100 text-slate-500">마감</span>}
              </div>
              <div className="flex items-center gap-3 mt-1.5 text-xs text-slate-400">
                <span>👥 {p.participantCount}/{p.totalMembers} 참여</span>
                {p.deadline && <span>⏰ {new Date(p.deadline).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })}</span>}
              </div>
              {p.results && p.results.length > 0 && (
                <div className="mt-2 space-y-1">
                  {p.results.slice(0, 3).map(r => (
                    <div key={r.index} className="flex items-center gap-2 text-xs">
                      <span className="w-16 truncate text-slate-600">{r.text}</span>
                      <div className="flex-1 bg-slate-100 rounded-full h-1.5">
                        <div className="bg-blue-400 h-1.5 rounded-full transition-all duration-500" style={{ width: `${r.percentage}%` }} />
                      </div>
                      <span className="w-8 text-right text-slate-400">{Math.round(r.percentage)}%</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/* ── 패널 설정 ── */

const PANELS = [
  {
    key: 'member',
    icon: Users,
    title: '멤버',
    views: [
      { key: 'numeric', label: '숫자' },
      { key: 'distribution', label: '분포' },
      { key: 'trend', label: '추이' },
    ],
    defaultView: 'numeric',
  },
  {
    key: 'access',
    icon: Activity,
    title: '접속',
    views: [
      { key: 'numeric', label: '숫자' },
      { key: 'trend', label: '추이' },
      { key: 'comparison', label: '비교' },
    ],
    defaultView: 'numeric',
  },
  {
    key: 'content',
    icon: FileText,
    title: '콘텐츠',
    views: [
      { key: 'numeric', label: '숫자' },
      { key: 'trend', label: '추이' },
      { key: 'comparison', label: '비교' },
    ],
    defaultView: 'numeric',
  },
  {
    key: 'poll',
    icon: Vote,
    title: '투표',
    views: [{ key: 'active', label: '활성' }],
    defaultView: 'active',
  },
]

/* ── 뷰 렌더러 ── */

function renderPanelView(panelKey, viewKey, data, clubId) {
  const d = data?.[panelKey]

  switch (panelKey) {
    case 'member':
      if (viewKey === 'numeric') return <MemberNumeric data={d} />
      if (viewKey === 'distribution') return <MemberDistribution data={d} />
      if (viewKey === 'trend') return <MemberTrend data={d} />
      break
    case 'access':
      if (viewKey === 'numeric') return <AccessNumeric data={d} />
      if (viewKey === 'trend') return <AccessTrend data={d} />
      if (viewKey === 'comparison') return <AccessComparison data={d} />
      break
    case 'content':
      if (viewKey === 'numeric') return <ContentNumeric data={d} />
      if (viewKey === 'trend') return <ContentTrend data={d} />
      if (viewKey === 'comparison') return <ContentComparison data={d} />
      break
    case 'poll':
      return <PollActiveList clubId={clubId} />
  }

  return <p className="text-sm text-slate-400 text-center py-8">데이터 없음</p>
}

/* ── 메인 컴포넌트 ── */

export default function DashboardTab({ clubId }) {
  const [period, setPeriod] = useState('MONTH')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [views, setViews] = useState(() => {
    const init = {}
    PANELS.forEach(p => { init[p.key] = p.defaultView })
    return init
  })

  const fetchDashboard = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const res = await api.get(`/clubs/${clubId}/admin/dashboard?period=${period}`)
      setData(res)
    } catch (e) {
      handleApiError(e, '대시보드 조회')
      setError('대시보드 데이터를 불러올 수 없습니다')
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [clubId, period])

  useEffect(() => {
    fetchDashboard()
  }, [fetchDashboard])

  const handleViewChange = useCallback((panelKey, viewKey) => {
    setViews(prev => ({ ...prev, [panelKey]: viewKey }))
  }, [])

  return (
    <div className="space-y-4">
      {/* Period filter */}
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-slate-800">대시보드</h2>
        <div className="relative">
          <select
            value={period}
            onChange={e => setPeriod(e.target.value)}
            className="appearance-none bg-white border border-slate-200 rounded-lg pl-3 pr-8 py-1.5 text-sm text-slate-700 cursor-pointer hover:border-slate-300 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
          >
            {PERIOD_OPTIONS.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400 pointer-events-none" />
        </div>
      </div>

      {/* Error banner */}
      {error && (
        <div className="bg-red-50 text-red-500 text-sm px-4 py-3 rounded-lg">
          {error}
          <button
            onClick={fetchDashboard}
            className="ml-2 underline hover:text-red-600 cursor-pointer"
          >
            다시 시도
          </button>
        </div>
      )}

      {/* Panel grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {PANELS.map((panel) => (
          <div
            key={panel.key}
            className={panel.key === 'poll' && PANELS.length % 2 === 1 ? 'lg:col-span-2' : ''}
          >
            <DashboardPanel
              icon={panel.icon}
              title={panel.title}
              views={panel.views}
              activeView={views[panel.key]}
              onViewChange={(vk) => handleViewChange(panel.key, vk)}
              loading={loading}
            >
              {renderPanelView(panel.key, views[panel.key], data, clubId)}
            </DashboardPanel>
          </div>
        ))}
      </div>
    </div>
  )
}
