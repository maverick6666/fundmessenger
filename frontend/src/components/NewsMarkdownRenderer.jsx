import { useMemo, useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid,
  Treemap, RadarChart, Radar, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Legend,
} from 'recharts'
import { ReactFlow, Background } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { normalizeChartMarkdown } from '../lib/chartMarkdown'

// ── 색상 팔레트 (차트 타입별로 다른 느낌) ──
const PALETTES = {
  bar:          ['#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#f97316'],
  line:         ['#6366f1', '#14b8a6', '#f43f5e'],
  pie:          ['#0ea5e9', '#f97316', '#22c55e', '#a855f7', '#ef4444', '#eab308', '#64748b', '#ec4899'],
  'stacked-bar':['#6366f1', '#f59e0b', '#10b981', '#ef4444', '#0ea5e9', '#a855f7'],
  waterfall:    { increase: '#22c55e', decrease: '#ef4444', base: '#94a3b8', total: '#3b82f6' },
  treemap:      ['#3b82f6', '#6366f1', '#0ea5e9', '#14b8a6', '#22c55e', '#eab308', '#f59e0b', '#f97316', '#ef4444', '#a855f7'],
  radar:        ['#3b82f6', '#f59e0b', '#10b981'],
}

function formatValue(v) {
  if (typeof v !== 'number') return v
  if (Math.abs(v) >= 1e8) return `${(v / 1e8).toFixed(1)}억`
  if (Math.abs(v) >= 1e4) return `${(v / 1e4).toFixed(0)}만`
  return v.toLocaleString()
}

// ── 1. Bar Chart ──
function BarBlock({ config }) {
  const { title, data, unit = '' } = config
  const colors = PALETTES.bar
  return (
    <ChartWrapper title={title}>
      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={data} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey="label" tick={{ fontSize: 11 }} />
          <YAxis tick={{ fontSize: 11 }} tickFormatter={formatValue} />
          <Tooltip formatter={(v) => [`${formatValue(v)}${unit}`, '']} />
          <Bar dataKey="value" radius={[4, 4, 0, 0]}>
            {data.map((_, i) => <Cell key={i} fill={colors[i % colors.length]} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartWrapper>
  )
}

// ── 2. Line Chart ──
function LineBlock({ config }) {
  const { title, data, unit = '' } = config
  return (
    <ChartWrapper title={title}>
      <ResponsiveContainer width="100%" height={180}>
        <LineChart data={data} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey="label" tick={{ fontSize: 11 }} />
          <YAxis tick={{ fontSize: 11 }} tickFormatter={formatValue} />
          <Tooltip formatter={(v) => [`${formatValue(v)}${unit}`, '']} />
          <Line type="monotone" dataKey="value" stroke={PALETTES.line[0]} strokeWidth={2} dot={{ r: 3 }} />
        </LineChart>
      </ResponsiveContainer>
    </ChartWrapper>
  )
}

// ── 3. Pie Chart ──
function PieBlock({ config }) {
  const { title, data, unit = '' } = config
  const colors = PALETTES.pie
  return (
    <ChartWrapper title={title}>
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="label" cx="50%" cy="50%" outerRadius={65}
               label={({ label, percent }) => `${label} ${(percent * 100).toFixed(0)}%`}
               labelLine={{ strokeWidth: 1 }} fontSize={10}>
            {data.map((_, i) => <Cell key={i} fill={colors[i % colors.length]} />)}
          </Pie>
          <Tooltip formatter={(v) => [`${formatValue(v)}${unit}`, '']} />
        </PieChart>
      </ResponsiveContainer>
    </ChartWrapper>
  )
}

// ── 4. Stacked Bar Chart ──
function StackedBarBlock({ config }) {
  const { title, categories, series, unit = '' } = config
  if (!categories || !series) return null
  const data = categories.map((cat, i) => {
    const row = { label: cat }
    series.forEach(s => { row[s.name] = s.data[i] })
    return row
  })
  const colors = PALETTES['stacked-bar']
  return (
    <ChartWrapper title={title}>
      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={data} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey="label" tick={{ fontSize: 11 }} />
          <YAxis tick={{ fontSize: 11 }} tickFormatter={formatValue} />
          <Tooltip formatter={(v) => [`${formatValue(v)}${unit}`, '']} />
          <Legend wrapperStyle={{ fontSize: 11 }} />
          {series.map((s, i) => (
            <Bar key={s.name} dataKey={s.name} stackId="a" fill={colors[i % colors.length]} radius={i === series.length - 1 ? [4, 4, 0, 0] : [0, 0, 0, 0]} />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </ChartWrapper>
  )
}

// ── 5. Waterfall Chart (커스텀 stacked bar) ──
function WaterfallBlock({ config }) {
  const { title, data, unit = '' } = config
  const colors = PALETTES.waterfall
  // 누적 계산: 각 항목의 invisible base + visible bar
  let running = 0
  const processed = data.map(d => {
    if (d.type === 'base' || d.type === 'total') {
      const result = { label: d.label, invisible: 0, bar: d.value, fill: colors[d.type] }
      running = d.value
      return result
    }
    const prev = running
    running += d.value
    return {
      label: d.label,
      invisible: d.value > 0 ? prev : prev + d.value,
      bar: Math.abs(d.value),
      fill: d.value > 0 ? colors.increase : colors.decrease,
    }
  })
  return (
    <ChartWrapper title={title}>
      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={processed} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis dataKey="label" tick={{ fontSize: 10 }} />
          <YAxis tick={{ fontSize: 11 }} tickFormatter={formatValue} />
          <Tooltip formatter={(v, name) => name === 'invisible' ? null : [`${formatValue(v)}${unit}`, '']}
                   itemSorter={() => 1} />
          <Bar dataKey="invisible" stackId="a" fill="transparent" />
          <Bar dataKey="bar" stackId="a" radius={[4, 4, 0, 0]}>
            {processed.map((d, i) => <Cell key={i} fill={d.fill} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartWrapper>
  )
}

// ── 6. Flow Chart (React Flow) ──
function FlowBlock({ config }) {
  const { title, nodes: rawNodes, edges: rawEdges } = config
  if (!rawNodes || !rawEdges) return null

  // 간단한 자동 레이아웃: 레벨별 배치
  const adjacency = {}
  rawEdges.forEach(e => { adjacency[e.from] = adjacency[e.from] || []; adjacency[e.from].push(e.to) })
  const levels = {}
  const roots = rawNodes.filter(n => !rawEdges.some(e => e.to === n.id)).map(n => n.id)
  const queue = roots.map(id => ({ id, level: 0 }))
  const visited = new Set()
  while (queue.length) {
    const { id, level } = queue.shift()
    if (visited.has(id)) continue
    visited.add(id)
    levels[id] = level
    ;(adjacency[id] || []).forEach(child => queue.push({ id: child, level: level + 1 }))
  }
  // 미방문 노드 처리
  rawNodes.forEach(n => { if (!(n.id in levels)) levels[n.id] = 0 })

  const levelGroups = {}
  rawNodes.forEach(n => {
    const l = levels[n.id]
    levelGroups[l] = levelGroups[l] || []
    levelGroups[l].push(n)
  })

  const nodes = rawNodes.map(n => {
    const l = levels[n.id]
    const group = levelGroups[l]
    const idx = group.indexOf(n)
    return {
      id: n.id,
      data: { label: n.label },
      position: { x: l * 200, y: idx * 70 },
      style: {
        background: '#f0f9ff', border: '1px solid #bae6fd', borderRadius: 8,
        padding: '8px 12px', fontSize: 11, fontWeight: 600, color: '#0c4a6e',
        width: 'auto', minWidth: 120,
      },
    }
  })
  const edges = rawEdges.map((e, i) => ({
    id: `e${i}`, source: e.from, target: e.to,
    animated: true, style: { stroke: '#93c5fd', strokeWidth: 2 },
  }))

  const maxLevel = Math.max(...Object.values(levels), 0)
  const maxPerLevel = Math.max(...Object.values(levelGroups).map(g => g.length), 1)

  return (
    <ChartWrapper title={title}>
      <div style={{ height: Math.max(maxPerLevel * 70 + 20, 150), width: '100%' }}>
        <ReactFlow nodes={nodes} edges={edges} fitView
          nodesDraggable={false} nodesConnectable={false} elementsSelectable={false}
          panOnDrag={false} zoomOnScroll={false} zoomOnPinch={false} zoomOnDoubleClick={false}
          proOptions={{ hideAttribution: true }}>
          <Background color="#e2e8f0" gap={20} />
        </ReactFlow>
      </div>
    </ChartWrapper>
  )
}

// ── 7. Timeline ──
function TimelineBlock({ config }) {
  const { title, events } = config
  if (!events?.length) return null
  return (
    <ChartWrapper title={title}>
      <div className="relative pl-6 py-1">
        <div className="absolute left-2.5 top-0 bottom-0 w-0.5 bg-blue-200" />
        {events.map((evt, i) => (
          <div key={i} className="relative mb-3 last:mb-0">
            <div className="absolute -left-3.5 top-1 w-2.5 h-2.5 rounded-full bg-blue-500 border-2 border-white" />
            <div className="ml-2">
              <span className="text-xs font-bold text-blue-600">{evt.date}</span>
              <p className="text-xs text-slate-700 mt-0.5">{evt.label}</p>
            </div>
          </div>
        ))}
      </div>
    </ChartWrapper>
  )
}

// ── 8. Gauge (반원 SVG) ──
function GaugeBlock({ config }) {
  const { title, value, max = 100, label, color = 'blue' } = config
  const pct = Math.min(value / max, 1)
  const colorMap = {
    green: { arc: '#22c55e', bg: '#dcfce7' },
    red: { arc: '#ef4444', bg: '#fee2e2' },
    yellow: { arc: '#eab308', bg: '#fef9c3' },
    blue: { arc: '#3b82f6', bg: '#dbeafe' },
  }
  const c = colorMap[color] || colorMap.blue
  const r = 56, cx = 100, cy = 76
  const startAngle = Math.PI
  const angle = startAngle - pct * Math.PI
  const x1 = cx + r * Math.cos(startAngle), y1 = cy - r * Math.sin(startAngle)
  const x2 = cx + r * Math.cos(angle), y2 = cy - r * Math.sin(angle)
  const largeArc = pct > 0.5 ? 1 : 0

  return (
    <ChartWrapper title={title}>
      <div className="flex flex-col items-center py-2">
        <svg width="200" height="120" viewBox="0 0 200 120">
          <path d={`M ${x1} ${y1} A ${r} ${r} 0 1 1 ${cx + r} ${cy}`} fill="none" stroke="#e2e8f0" strokeWidth="12" strokeLinecap="round" />
          {pct > 0 && (
            <path d={`M ${x1} ${y1} A ${r} ${r} 0 ${largeArc} 1 ${x2} ${y2}`} fill="none" stroke={c.arc} strokeWidth="12" strokeLinecap="round" />
          )}
          <text x={cx} y={cy - 4} textAnchor="middle" fill={c.arc} fontSize="24" fontWeight="bold">{value}</text>
          {label && <text x={cx} y={cy + 16} textAnchor="middle" fill="#64748b" fontSize="12">{label}</text>}
        </svg>
      </div>
    </ChartWrapper>
  )
}

// ── 9. Treemap ──
function TreemapBlock({ config }) {
  const { title, data, unit = '' } = config
  const colors = PALETTES.treemap
  const treemapData = [{ name: 'root', children: data.map((d, i) => ({ name: d.label, size: d.value, fill: colors[i % colors.length] })) }]
  return (
    <ChartWrapper title={title}>
      <ResponsiveContainer width="100%" height={180}>
        <Treemap data={treemapData[0].children} dataKey="size" nameKey="name"
          content={({ x, y, width, height, name, fill }) => {
            if (width < 30 || height < 20) return null
            return (
              <g>
                <rect x={x} y={y} width={width} height={height} fill={fill} rx={4} stroke="#fff" strokeWidth={2} />
                {width > 50 && height > 30 && (
                  <>
                    <text x={x + width / 2} y={y + height / 2 - 5} textAnchor="middle" fill="#fff" fontSize={10} fontWeight={600}>{name}</text>
                    <text x={x + width / 2} y={y + height / 2 + 9} textAnchor="middle" fill="rgba(255,255,255,0.8)" fontSize={9}>{formatValue(data.find(d => d.label === name)?.value)}{unit}</text>
                  </>
                )}
              </g>
            )
          }}
        />
      </ResponsiveContainer>
    </ChartWrapper>
  )
}

// ── 10. Radar Chart ──
function RadarBlock({ config }) {
  const { title, axes, series } = config
  if (!axes || !series) return null
  const data = axes.map((axis, i) => {
    const row = { axis }
    series.forEach(s => { row[s.name] = s.data[i] })
    return row
  })
  const colors = PALETTES.radar
  return (
    <ChartWrapper title={title}>
      <ResponsiveContainer width="100%" height={220}>
        <RadarChart data={data} cx="50%" cy="50%" outerRadius={70}>
          <PolarGrid stroke="#e2e8f0" />
          <PolarAngleAxis dataKey="axis" tick={{ fontSize: 10 }} />
          <PolarRadiusAxis tick={{ fontSize: 9 }} />
          {series.map((s, i) => (
            <Radar key={s.name} name={s.name} dataKey={s.name}
              stroke={colors[i % colors.length]} fill={colors[i % colors.length]} fillOpacity={0.15} strokeWidth={2} />
          ))}
          <Legend wrapperStyle={{ fontSize: 11 }} />
          <Tooltip />
        </RadarChart>
      </ResponsiveContainer>
    </ChartWrapper>
  )
}

// ── 차트 래퍼 ──
function ChartWrapper({ title, children }) {
  return (
    <div className="my-4 p-4 bg-slate-50/70 rounded-xl border border-slate-200">
      {title && <h4 className="text-xs font-bold text-slate-600 mb-3 text-center">{title}</h4>}
      {children}
    </div>
  )
}

// ── 차트 블록 라우터 ──
const CHART_COMPONENTS = {
  'bar': BarBlock,
  'line': LineBlock,
  'pie': PieBlock,
  'stacked-bar': StackedBarBlock,
  'waterfall': WaterfallBlock,
  'flow': FlowBlock,
  'timeline': TimelineBlock,
  'gauge': GaugeBlock,
  'treemap': TreemapBlock,
  'radar': RadarBlock,
}

function ChartRouter({ type, config }) {
  const Component = CHART_COMPONENTS[type]
  if (!Component) return <pre className="text-xs text-red-400">지원하지 않는 차트: {type}</pre>

  const layout = config.layout || 'full'
  const caption = config.caption

  if (layout === 'half-left') {
    return (
      <div className="my-4 flex gap-4 items-center">
        <div className="w-1/2 min-w-0 shrink-0"><Component config={config} /></div>
        {caption && <div className="w-1/2"><p className="text-sm text-slate-600 leading-relaxed">{caption}</p></div>}
      </div>
    )
  }
  if (layout === 'half-right') {
    return (
      <div className="my-4 flex gap-4 items-center">
        {caption && <div className="w-1/2"><p className="text-sm text-slate-600 leading-relaxed">{caption}</p></div>}
        <div className="w-1/2 min-w-0 shrink-0"><Component config={config} /></div>
      </div>
    )
  }
  if (layout === 'half-center') {
    return (
      <div className="my-4 flex justify-center">
        <div className="w-1/2 min-w-0"><Component config={config} /></div>
      </div>
    )
  }

  // full (default)
  return <Component config={config} />
}

// ── 데이터 키 정규화 ──
// x/y → label/value, 멀티시리즈 bar → stacked-bar 자동 전환
function normalizeChartConfig(config, chartType) {
  const normalized = { ...config }
  if (normalized.data) {
    normalized.data = config.data.map(d => {
      const row = { ...d }
      if ('x' in row && !('label' in row)) { row.label = row.x; delete row.x }
      if ('y' in row && !('value' in row)) { row.value = row.y; delete row.y }
      return row
    })

    // 멀티시리즈 bar 감지: data[0]에 label 외 여러 키가 있으면 stacked-bar로 전환
    if (chartType === 'bar' && normalized.data.length > 0) {
      const keys = Object.keys(normalized.data[0]).filter(k => k !== 'label' && k !== 'x')
      if (keys.length > 1 || (keys.length === 1 && keys[0] !== 'value')) {
        const seriesNames = keys.filter(k => k !== 'value')
        if (seriesNames.length > 1) {
          normalized._autoStacked = true
          normalized.categories = normalized.data.map(d => d.label)
          normalized.series = seriesNames.map(name => ({
            name,
            data: normalized.data.map(d => d[name] || 0),
          }))
        }
      }
    }
  }
  return normalized
}

// ── 마크다운 파서 ──
// ```chart-bar ... ``` 또는 ```chart\n{"type":"bar",...}``` 둘 다 매칭
const CHART_REGEX = /```chart(?:-([\w-]+))?\s*\n([\s\S]*?)```/g

function parseChartBlocks(markdown) {
  if (!markdown) return []
  const parts = []
  let lastIndex = 0
  let match
  CHART_REGEX.lastIndex = 0
  while ((match = CHART_REGEX.exec(markdown)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ type: 'markdown', content: markdown.slice(lastIndex, match.index) })
    }
    try {
      const raw = JSON.parse(match[2].trim())
      // 타입: 펜스 접미사 우선, 없으면 JSON 내부 type 필드
      const chartType = match[1] || raw.type
      if (!chartType) throw new Error('no chart type')
      const config = normalizeChartConfig(raw, chartType)
      const finalType = config._autoStacked ? 'stacked-bar' : chartType
      parts.push({ type: 'chart', chartType: finalType, config })
    } catch {
      parts.push({ type: 'markdown', content: match[0] })
    }
    lastIndex = match.index + match[0].length
  }
  if (lastIndex < markdown.length) {
    parts.push({ type: 'markdown', content: markdown.slice(lastIndex) })
  }
  return parts
}

// ── 메인 컴포넌트 ──
const mdComponents = {
  h2: ({ children }) => <h2 className="text-base font-bold text-slate-800 mt-4 mb-2 pb-1 border-b border-slate-100">{children}</h2>,
  h3: ({ children }) => <h3 className="text-sm font-bold text-slate-700 mt-3 mb-1.5">{children}</h3>,
  p: ({ children }) => <p className="text-sm text-slate-600 leading-relaxed mb-2">{children}</p>,
  strong: ({ children }) => <strong className="font-bold text-slate-800">{children}</strong>,
  ul: ({ children }) => <ul className="text-sm text-slate-600 list-disc pl-5 mb-2 space-y-1">{children}</ul>,
  ol: ({ children }) => <ol className="text-sm text-slate-600 list-decimal pl-5 mb-2 space-y-1">{children}</ol>,
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  blockquote: ({ children }) => <blockquote className="border-l-3 border-blue-400 pl-3 my-2 text-sm text-slate-500 italic">{children}</blockquote>,
  table: ({ children }) => (
    <div className="my-3 overflow-x-auto rounded-lg border border-slate-200">
      <table className="w-full text-xs">{children}</table>
    </div>
  ),
  thead: ({ children }) => <thead className="bg-slate-50">{children}</thead>,
  th: ({ children }) => <th className="px-3 py-2 text-left font-bold text-slate-700 border-b border-slate-200">{children}</th>,
  td: ({ children }) => <td className="px-3 py-2 text-slate-600 border-b border-slate-100">{children}</td>,
  hr: () => <hr className="my-3 border-slate-100" />,
}

export default function NewsMarkdownRenderer({ content }) {
  // TipTap JSON으로 저장된 구 데이터 → 텍스트 추출
  const normalizedContent = useMemo(() => {
    if (!content) return ''
    const trimmed = content.trim()
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
      try {
        const doc = JSON.parse(trimmed)
        const extract = (node) => {
          if (typeof node === 'string') return node
          if (node.text) return node.text
          if (node.content) return node.content.map(extract).join('')
          return ''
        }
        return normalizeChartMarkdown((doc.content || []).map(n => extract(n)).join('\n\n'))
      } catch { return normalizeChartMarkdown(content) }
    }
    return normalizeChartMarkdown(content)
  }, [content])
  const parts = useMemo(() => parseChartBlocks(normalizedContent), [normalizedContent])
  if (!normalizedContent) return null

  return (
    <div className="news-markdown">
      {parts.map((part, i) =>
        part.type === 'chart' ? (
          <ChartRouter key={i} type={part.chartType} config={part.config} />
        ) : (
          <ReactMarkdown key={i} remarkPlugins={[remarkGfm]} components={mdComponents}>
            {part.content}
          </ReactMarkdown>
        )
      )}
    </div>
  )
}
