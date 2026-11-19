import { useState, useEffect, useRef } from 'react'
import { ResponsiveContainer, AreaChart, Area, XAxis, YAxis, Tooltip, CartesianGrid } from 'recharts'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'

const PERIODS = [
  { key: 'daily', label: '일' },
  { key: 'weekly', label: '주' },
  { key: 'monthly', label: '월' },
]

export default function StockPriceChart({ clubId, stockId, apiUrl }) {
  const [period, setPeriod] = useState('daily')
  const [data, setData] = useState({})     // { daily: [...], weekly: [...], monthly: [...] }
  const [loading, setLoading] = useState(false)
  const dataRef = useRef({})

  const cacheKey = apiUrl || stockId

  const fetchChart = async (p) => {
    if (dataRef.current[p]) return // ref로 최신 상태 확인 (stale closure 방지)
    setLoading(true)
    try {
      const url = apiUrl
        ? `${apiUrl}${apiUrl.includes('?') ? '&' : '?'}period=${p}`
        : `/clubs/${clubId}/jongtobang/stocks/${stockId}/chart?period=${p}`
      const res = await api.get(url)
      setData(prev => {
        const next = { ...prev, [p]: res.prices || [] }
        dataRef.current = next
        return next
      })
    } catch (e) {
      handleApiError(e, '차트 조회')
    } finally {
      setLoading(false)
    }
  }

  // cacheKey 바뀌면 캐시 초기화 + daily 로드
  useEffect(() => {
    dataRef.current = {}
    setData({})
    setPeriod('daily')
  }, [cacheKey])

  // period 또는 cacheKey 바뀌면 fetch
  useEffect(() => {
    if (cacheKey) fetchChart(period)
  }, [cacheKey, period])

  const handlePeriodChange = (p) => {
    setPeriod(p)
  }

  const chartData = (data[period] || []).map(p => ({
    date: formatDate(p.date, period),
    close: Number(p.close),
    fullDate: p.date,
  }))

  const prices = chartData.map(d => d.close)
  const minPrice = prices.length > 0 ? Math.floor(Math.min(...prices) * 0.995) : 0
  const maxPrice = prices.length > 0 ? Math.ceil(Math.max(...prices) * 1.005) : 100

  // 등락 색상 (첫 가격 대비 마지막 가격)
  const isUp = prices.length >= 2 && prices[prices.length - 1] >= prices[0]
  const chartColor = isUp ? '#ef4444' : '#3b82f6' // 상승=red, 하락=blue (한국 컨벤션)

  return (
    <div className="px-5 py-3 border-b border-slate-100">
      {/* 탭 */}
      <div className="flex items-center gap-1 mb-3">
        {PERIODS.map(p => (
          <button
            key={p.key}
            onClick={() => handlePeriodChange(p.key)}
            className={`text-xs px-3 py-1 rounded cursor-pointer transition-colors ${
              period === p.key
                ? 'text-blue-600 font-semibold bg-blue-50'
                : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
            }`}
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* 차트 */}
      <div className="h-[160px]">
        {loading && chartData.length === 0 ? (
          <div className="h-full flex flex-col justify-end gap-1 px-2">
            <div className="flex items-end gap-[3px] h-[120px]">
              {Array.from({ length: 20 }).map((_, i) => (
                <div
                  key={i}
                  className="flex-1 bg-slate-100 rounded-t animate-pulse"
                  style={{ height: `${30 + Math.sin(i * 0.5) * 25 + Math.random() * 20}%` }}
                />
              ))}
            </div>
            <div className="h-4 bg-slate-100 rounded animate-pulse w-full" />
          </div>
        ) : chartData.length === 0 ? (
          <div className="flex items-center justify-center h-full text-xs text-slate-400">
            차트 데이터가 없습니다
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData} margin={{ top: 5, right: 5, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id={`gradient-${cacheKey}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={chartColor} stopOpacity={0.15} />
                  <stop offset="95%" stopColor={chartColor} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
              <XAxis
                dataKey="date"
                axisLine={false}
                tickLine={false}
                tick={{ fontSize: 10, fill: '#94a3b8' }}
                interval={Math.max(0, Math.floor(chartData.length / 5) - 1)}
                tickMargin={4}
              />
              <YAxis
                domain={[minPrice, maxPrice]}
                axisLine={false}
                tickLine={false}
                tick={{ fontSize: 10, fill: '#94a3b8' }}
                tickFormatter={v => formatPrice(v)}
                width={55}
              />
              <Tooltip content={<CustomTooltip />} />
              <Area
                type="monotone"
                dataKey="close"
                stroke={chartColor}
                strokeWidth={1.5}
                fill={`url(#gradient-${cacheKey})`}
                dot={false}
                activeDot={{ r: 3, strokeWidth: 0, fill: chartColor }}
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  const d = payload[0].payload
  return (
    <div className="bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 shadow-sm text-xs">
      <p className="text-slate-500">{d.fullDate}</p>
      <p className="font-semibold text-slate-800">{Number(d.close).toLocaleString()}원</p>
    </div>
  )
}

function formatDate(dateStr, period) {
  const d = new Date(dateStr)
  if (period === 'monthly') {
    return `${String(d.getFullYear()).slice(2)}.${d.getMonth() + 1}`
  }
  if (period === 'weekly') {
    return `${d.getMonth() + 1}.${d.getDate()}`
  }
  return `${d.getMonth() + 1}.${d.getDate()}`
}

function formatPrice(v) {
  if (v >= 1000000) return `${(v / 10000).toFixed(0)}만`
  if (v >= 10000) return `${(v / 10000).toFixed(1)}만`
  return v.toLocaleString()
}
