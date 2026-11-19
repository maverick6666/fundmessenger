/**
 * ChartCodeBlock — TipTap CodeBlock 확장
 * language가 "chart-xxx"이면 차트를 시각적으로 렌더링 + X 삭제 버튼
 * 그 외에는 기본 코드 블록 렌더링
 */
import { NodeViewContent, NodeViewWrapper, ReactNodeViewRenderer } from '@tiptap/react'
import { useMemo } from 'react'
import {
  BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid,
  RadarChart, Radar, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Legend,
} from 'recharts'

// ── 색상 팔레트 (NewsMarkdownRenderer와 동일) ──
const PALETTES = {
  bar:          ['#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#f97316'],
  line:         ['#6366f1', '#14b8a6', '#f43f5e'],
  pie:          ['#0ea5e9', '#f97316', '#22c55e', '#a855f7', '#ef4444', '#eab308', '#64748b', '#ec4899'],
  'stacked-bar':['#6366f1', '#f59e0b', '#10b981', '#ef4444', '#0ea5e9', '#a855f7'],
  radar:        ['#3b82f6', '#f59e0b', '#10b981'],
}

function formatValue(v) {
  if (typeof v !== 'number') return v
  if (Math.abs(v) >= 1e8) return `${(v / 1e8).toFixed(1)}억`
  if (Math.abs(v) >= 1e4) return `${(v / 1e4).toFixed(0)}만`
  return v.toLocaleString()
}

// ── 미니 차트 컴포넌트들 ──
function MiniBar({ config }) {
  const { data, unit = '' } = config
  if (!data?.length) return null
  return (
    <ResponsiveContainer width="100%" height={140}>
      <BarChart data={data} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
        <XAxis dataKey="label" tick={{ fontSize: 10 }} />
        <YAxis tick={{ fontSize: 10 }} tickFormatter={formatValue} />
        <Tooltip formatter={(v) => [`${formatValue(v)}${unit}`, '']} />
        <Bar dataKey="value" radius={[3, 3, 0, 0]}>
          {data.map((_, i) => <Cell key={i} fill={PALETTES.bar[i % PALETTES.bar.length]} />)}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}

function MiniLine({ config }) {
  const { data, unit = '' } = config
  if (!data?.length) return null
  return (
    <ResponsiveContainer width="100%" height={140}>
      <LineChart data={data} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
        <XAxis dataKey="label" tick={{ fontSize: 10 }} />
        <YAxis tick={{ fontSize: 10 }} tickFormatter={formatValue} />
        <Tooltip formatter={(v) => [`${formatValue(v)}${unit}`, '']} />
        <Line type="monotone" dataKey="value" stroke={PALETTES.line[0]} strokeWidth={2} dot={{ r: 2 }} />
      </LineChart>
    </ResponsiveContainer>
  )
}

function MiniPie({ config }) {
  const { data, unit = '' } = config
  if (!data?.length) return null
  return (
    <ResponsiveContainer width="100%" height={150}>
      <PieChart>
        <Pie data={data} dataKey="value" nameKey="label" cx="50%" cy="50%" outerRadius={50}
             label={({ label, percent }) => `${label} ${(percent * 100).toFixed(0)}%`}
             labelLine={{ strokeWidth: 1 }} fontSize={9}>
          {data.map((_, i) => <Cell key={i} fill={PALETTES.pie[i % PALETTES.pie.length]} />)}
        </Pie>
        <Tooltip formatter={(v) => [`${formatValue(v)}${unit}`, '']} />
      </PieChart>
    </ResponsiveContainer>
  )
}

function MiniStackedBar({ config }) {
  const { categories, series, unit = '' } = config
  if (!categories || !series) return null
  const data = categories.map((cat, i) => {
    const row = { label: cat }
    series.forEach(s => { row[s.name] = s.data[i] })
    return row
  })
  return (
    <ResponsiveContainer width="100%" height={150}>
      <BarChart data={data} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
        <XAxis dataKey="label" tick={{ fontSize: 10 }} />
        <YAxis tick={{ fontSize: 10 }} tickFormatter={formatValue} />
        <Tooltip formatter={(v) => [`${formatValue(v)}${unit}`, '']} />
        <Legend wrapperStyle={{ fontSize: 10 }} />
        {series.map((s, i) => (
          <Bar key={s.name} dataKey={s.name} stackId="a" fill={PALETTES['stacked-bar'][i % PALETTES['stacked-bar'].length]} />
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}

function MiniRadar({ config }) {
  const { axes, series } = config
  if (!axes || !series) return null
  const data = axes.map((axis, i) => {
    const row = { axis }
    series.forEach(s => { row[s.name] = s.data[i] })
    return row
  })
  return (
    <ResponsiveContainer width="100%" height={160}>
      <RadarChart data={data} cx="50%" cy="50%" outerRadius={50}>
        <PolarGrid stroke="#e2e8f0" />
        <PolarAngleAxis dataKey="axis" tick={{ fontSize: 9 }} />
        {series.map((s, i) => (
          <Radar key={s.name} name={s.name} dataKey={s.name}
            stroke={PALETTES.radar[i % PALETTES.radar.length]} fill={PALETTES.radar[i % PALETTES.radar.length]} fillOpacity={0.15} strokeWidth={2} />
        ))}
        <Legend wrapperStyle={{ fontSize: 10 }} />
      </RadarChart>
    </ResponsiveContainer>
  )
}

// ── 타입→컴포넌트 매핑 ──
const CHART_MAP = {
  bar: MiniBar,
  line: MiniLine,
  pie: MiniPie,
  'stacked-bar': MiniStackedBar,
  radar: MiniRadar,
}

function normalizeData(config, chartType) {
  if (!config.data) return config
  const normalized = { ...config }
  normalized.data = config.data.map(d => {
    const row = { ...d }
    if ('x' in row && !('label' in row)) { row.label = row.x; delete row.x }
    if ('y' in row && !('value' in row)) { row.value = row.y; delete row.y }
    return row
  })
  // 멀티시리즈 bar → stacked-bar 자동 전환
  if (chartType === 'bar' && normalized.data.length > 0) {
    const keys = Object.keys(normalized.data[0]).filter(k => k !== 'label' && k !== 'x')
    if (keys.length > 1) {
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
  return normalized
}

// ── CodeBlock NodeView ──
function ChartCodeBlockView({ node, deleteNode, editor, extension }) {
  const language = node.attrs.language || ''
  const isChart = language.startsWith('chart')
  const editable = editor?.isEditable

  const chartType = useMemo(() => {
    if (!isChart) return null
    // "chart-bar" → "bar", "chart" → JSON 내부 type
    const suffix = language.replace('chart-', '').replace('chart', '')
    return suffix || null
  }, [language, isChart])

  const chartConfig = useMemo(() => {
    if (!isChart) return null
    try {
      const text = node.textContent?.trim()
      if (!text) return null
      const raw = JSON.parse(text)
      const type = chartType || raw.type
      if (!type) return null
      const config = normalizeData(raw, type)
      return { type: config._autoStacked ? 'stacked-bar' : type, config }
    } catch {
      return null
    }
  }, [isChart, node.textContent, chartType])

  // 차트가 아니면 기본 코드 블록 렌더링
  if (!isChart) {
    return (
      <NodeViewWrapper as="pre" className="bg-slate-900 text-slate-100 rounded-lg p-4 my-3 text-sm overflow-x-auto">
        <NodeViewContent as="code" />
      </NodeViewWrapper>
    )
  }

  // 차트 렌더링
  const ChartComponent = chartConfig ? CHART_MAP[chartConfig.type] : null

  return (
    <NodeViewWrapper>
      <div className="my-3 relative group" contentEditable={false}>
        {editable && (
          <button
            onClick={deleteNode}
            className="absolute -top-2 -right-2 z-10 w-6 h-6 bg-red-500 text-white rounded-full text-xs font-bold opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer flex items-center justify-center shadow-md hover:bg-red-600"
            title="차트 삭제"
          >
            ×
          </button>
        )}
        <div className="p-4 bg-slate-50/70 rounded-xl border border-slate-200">
          {chartConfig?.config?.title && (
            <h4 className="text-xs font-bold text-slate-600 mb-2 text-center">{chartConfig.config.title}</h4>
          )}
          {ChartComponent ? (
            <ChartComponent config={chartConfig.config} />
          ) : (
            <div className="text-center py-4">
              <span className="text-xs px-2 py-1 bg-blue-50 text-blue-600 rounded font-medium">
                📊 {chartConfig?.type || chartType || 'chart'} 차트
              </span>
              <p className="text-xs text-slate-400 mt-1">지원되지 않는 차트 타입이거나 데이터 오류</p>
            </div>
          )}
        </div>
        {/* 숨겨진 코드 (마크다운 저장용) */}
        <div className="hidden">
          <NodeViewContent as="code" />
        </div>
      </div>
    </NodeViewWrapper>
  )
}

// ── TipTap Extension ──
// StarterKit의 codeBlock을 비활성화하고 이걸로 대체
import CodeBlock from '@tiptap/extension-code-block'

const ChartCodeBlock = CodeBlock.extend({
  addStorage() {
    return {
      markdown: {
        serialize(state, node) {
          state.write(`\`\`\`${node.attrs.language || ''}\n`)
          state.text(node.textContent, false)
          state.ensureNewLine()
          state.write('```')
          state.closeBlock(node)
        },
        parse: {
          setup(markdownit) {
            markdownit.set({
              langPrefix: this.options.languageClassPrefix ?? 'language-',
            })
          },
          updateDOM(element) {
            element.innerHTML = element.innerHTML.replace(/\n<\/code><\/pre>/g, '</code></pre>')
          },
        },
      },
    }
  },
  addNodeView() {
    return ReactNodeViewRenderer(ChartCodeBlockView)
  },
})

export default ChartCodeBlock
