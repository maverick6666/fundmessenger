import { useState, useEffect } from 'react'
import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, Legend } from 'recharts'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'

const RANGES = [
  { key: '3M', label: '3개월' },
  { key: '6M', label: '6개월' },
  { key: '1Y', label: '1년' },
  { key: 'ALL', label: '전체' },
]

const COLORS = {
  portfolio: '#3b82f6', // blue
  kospi: '#f43f5e',     // rose
  sp500: '#10b981',     // emerald
}

export default function BenchmarkChart({ clubId, teamId }) {
  const [range, setRange] = useState('3M')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!clubId || !teamId) return
    setLoading(true)
    api.get(`/clubs/${clubId}/fund/teams/${teamId}/stats/benchmark?range=${range}`)
      .then(setData)
      .catch(e => handleApiError(e, '벤치마크 조회'))
      .finally(() => setLoading(false))
  }, [clubId, teamId, range])

  // 데이터 병합 — 모든 시리즈의 날짜를 합쳐서 하나의 배열로
  const chartData = mergeData(data)

  const hasPortfolio = data?.portfolio?.length > 0
  const hasKospi = data?.kospi?.length > 0
  const hasSp500 = data?.sp500?.length > 0
  const hasAnyData = hasPortfolio || hasKospi || hasSp500

  return (
    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
      <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-800">벤치마크 비교</h3>
        <div className="flex gap-1">
          {RANGES.map(r => (
            <button
              key={r.key}
              onClick={() => setRange(r.key)}
              className={`text-xs px-2.5 py-1 rounded cursor-pointer transition-colors ${
                range === r.key
                  ? 'text-blue-600 font-semibold bg-blue-50'
                  : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
              }`}
            >
              {r.label}
            </button>
          ))}
        </div>
      </div>

      <div className="px-5 py-4">
        {loading ? (
          <div className="flex items-center justify-center h-[220px]">
            <div className="animate-spin w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full" />
          </div>
        ) : !hasAnyData ? (
          <div className="flex flex-col items-center justify-center h-[220px] text-center">
            <p className="text-sm text-slate-400">
              {!hasPortfolio
                ? '포트폴리오 탭에서 스냅샷을 기록하세요'
                : '벤치마크 데이터를 불러올 수 없습니다'}
            </p>
          </div>
        ) : (
          <div className="h-[220px]">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 5, right: 10, bottom: 0, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
                <XAxis
                  dataKey="date"
                  axisLine={false}
                  tickLine={false}
                  tick={{ fontSize: 10, fill: '#94a3b8' }}
                  interval={Math.max(0, Math.floor(chartData.length / 6) - 1)}
                  tickFormatter={formatXDate}
                  tickMargin={4}
                />
                <YAxis
                  axisLine={false}
                  tickLine={false}
                  tick={{ fontSize: 10, fill: '#94a3b8' }}
                  tickFormatter={v => (v >= 0 ? '+' : '') + (v * 100).toFixed(1) + '%'}
                  width={55}
                />
                <Tooltip content={<BenchmarkTooltip />} />
                <Legend
                  verticalAlign="top"
                  height={28}
                  iconType="line"
                  iconSize={12}
                  formatter={(value) => <span className="text-xs text-slate-600">{value}</span>}
                />
                {hasPortfolio && (
                  <Line
                    type="monotone"
                    dataKey="portfolio"
                    name="포트폴리오"
                    stroke={COLORS.portfolio}
                    strokeWidth={2}
                    dot={false}
                    connectNulls
                  />
                )}
                {hasKospi && (
                  <Line
                    type="monotone"
                    dataKey="kospi"
                    name="KOSPI"
                    stroke={COLORS.kospi}
                    strokeWidth={1.5}
                    dot={false}
                    connectNulls
                    strokeDasharray="4 2"
                  />
                )}
                {hasSp500 && (
                  <Line
                    type="monotone"
                    dataKey="sp500"
                    name="S&P 500"
                    stroke={COLORS.sp500}
                    strokeWidth={1.5}
                    dot={false}
                    connectNulls
                    strokeDasharray="4 2"
                  />
                )}
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
    </div>
  )
}

function BenchmarkTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-white border border-slate-200 rounded-lg px-3 py-2 shadow-sm text-xs space-y-0.5">
      <p className="text-slate-500 font-medium">{label}</p>
      {payload.map((p, i) => (
        <p key={i} style={{ color: p.color }} className="font-semibold">
          {p.name}: {p.value != null ? (p.value >= 0 ? '+' : '') + (p.value * 100).toFixed(2) + '%' : '-'}
        </p>
      ))}
    </div>
  )
}

function formatXDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}.${d.getDate()}`
}

/**
 * 3개 시리즈를 날짜 기준으로 병합
 */
function mergeData(data) {
  if (!data) return []

  const dateMap = new Map()

  const addSeries = (series, key) => {
    if (!series) return
    for (const point of series) {
      if (!dateMap.has(point.date)) {
        dateMap.set(point.date, { date: point.date })
      }
      dateMap.get(point.date)[key] = Number(point.cumulativeReturn)
    }
  }

  addSeries(data.portfolio, 'portfolio')
  addSeries(data.kospi, 'kospi')
  addSeries(data.sp500, 'sp500')

  // 날짜순 정렬
  return [...dateMap.values()].sort((a, b) => a.date.localeCompare(b.date))
}
