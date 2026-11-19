import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight, Clock, ArrowLeft } from 'lucide-react'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const HOUR_OPTIONS = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'))
const MINUTE_OPTIONS = ['00', '10', '20', '30', '40', '50']

const pad = (v) => String(v).padStart(2, '0')

export const toDateValue = (date) => {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export const createDefaultParts = () => {
  const now = new Date()
  const rounded = new Date(now)
  rounded.setSeconds(0, 0)
  const nextMin = Math.ceil((rounded.getMinutes() + 1) / 10) * 10
  if (nextMin >= 60) {
    rounded.setHours(rounded.getHours() + 1, 0, 0, 0)
  } else {
    rounded.setMinutes(nextMin)
  }
  return { date: toDateValue(rounded), hour: pad(rounded.getHours()), minute: pad(rounded.getMinutes()) }
}

export const formatDateTimeSummary = (value) => {
  if (!value) return '설정 없음'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '설정 없음'
  return d.toLocaleString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short', hour: 'numeric', minute: '2-digit' })
}

export const isPastDateTime = (date, hour, minute) => {
  const v = new Date(`${date}T${hour}:${minute}`)
  return Number.isNaN(v.getTime()) || v.getTime() <= Date.now()
}

const toMonthStart = (dateValue) => {
  const base = dateValue ? new Date(`${dateValue}T00:00`) : new Date()
  return new Date(base.getFullYear(), base.getMonth(), 1)
}

const buildCalendarDays = (monthDate, selectedDate) => {
  const monthStart = new Date(monthDate.getFullYear(), monthDate.getMonth(), 1)
  const gridStart = new Date(monthStart)
  gridStart.setDate(monthStart.getDate() - monthStart.getDay())
  const today = toDateValue(new Date())
  return Array.from({ length: 42 }, (_, i) => {
    const cell = new Date(gridStart)
    cell.setDate(gridStart.getDate() + i)
    const value = toDateValue(cell)
    return {
      value, label: cell.getDate(),
      currentMonth: cell.getMonth() === monthDate.getMonth(),
      disabled: value < today, isToday: value === today, selected: value === selectedDate,
    }
  })
}

/**
 * 날짜+시간 선택 뷰 — 패널 내부에서 전체를 차지하는 인라인 컴포넌트
 * Props: initialDate, initialHour, initialMinute, onApply({ date, hour, minute }), onClear, onBack, title?, allowPast?
 */
export default function DateTimePicker({
  initialDate, initialHour, initialMinute,
  onApply, onClear, onBack,
  title = '날짜/시간 설정', clearLabel = '설정 없음', allowPast = false,
  reminderDays: initialReminderDays, // { 7: false, 3: false, 1: false } — null이면 리마인더 UI 숨김
}) {
  const [draftDate, setDraftDate] = useState(initialDate)
  const [draftHour, setDraftHour] = useState(initialHour)
  const [draftMinute, setDraftMinute] = useState(initialMinute)
  const [reminders, setReminders] = useState(initialReminderDays || null)
  const [calendarMonth, setCalendarMonth] = useState(() => toMonthStart(initialDate))

  useEffect(() => {
    setDraftDate(initialDate)
    setDraftHour(initialHour)
    setDraftMinute(initialMinute)
    setCalendarMonth(toMonthStart(initialDate))
  }, [initialDate, initialHour, initialMinute])

  const calendarDays = useMemo(() => buildCalendarDays(calendarMonth, draftDate), [calendarMonth, draftDate])

  const draftValue = `${draftDate}T${draftHour}:${draftMinute}`
  const error = !allowPast && isPastDateTime(draftDate, draftHour, draftMinute)
    ? '현재보다 이후 시간으로 설정해 주세요.'
    : ''

  const applyQuick = (days) => {
    const d = new Date(); d.setDate(d.getDate() + days)
    const next = toDateValue(d)
    setDraftDate(next); setCalendarMonth(toMonthStart(next))
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-2 px-4 py-3 border-b border-slate-100">
        <button onClick={onBack} className="p-1 rounded-full hover:bg-slate-100 cursor-pointer">
          <ArrowLeft size={16} className="text-slate-500" />
        </button>
        <p className="text-sm font-semibold text-slate-900">{title}</p>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
        {/* 빠른 선택 */}
        <div className="flex flex-wrap gap-2">
          {[['오늘', 0], ['내일', 1], ['+7일', 7]].map(([label, days]) => (
            <button key={days} type="button" onClick={() => applyQuick(days)}
              className="rounded-full border border-slate-200 px-3 py-1 text-xs font-medium text-slate-600 hover:border-primary/40 hover:text-primary cursor-pointer">
              {label}
            </button>
          ))}
        </div>

        {/* 캘린더 */}
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-3">
          <div className="mb-2 flex items-center justify-between">
            <button type="button" onClick={() => { const p = new Date(calendarMonth); p.setMonth(p.getMonth() - 1); setCalendarMonth(p) }}
              className="rounded-full p-1 text-slate-400 hover:bg-white hover:text-slate-600 cursor-pointer">
              <ChevronLeft size={16} />
            </button>
            <p className="text-xs font-semibold text-slate-700">
              {calendarMonth.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long' })}
            </p>
            <button type="button" onClick={() => { const n = new Date(calendarMonth); n.setMonth(n.getMonth() + 1); setCalendarMonth(n) }}
              className="rounded-full p-1 text-slate-400 hover:bg-white hover:text-slate-600 cursor-pointer">
              <ChevronRight size={16} />
            </button>
          </div>
          <div className="grid grid-cols-7 gap-0.5 text-center text-xs text-slate-400">
            {WEEKDAYS.map(d => <span key={d} className="py-0.5">{d}</span>)}
          </div>
          <div className="mt-0.5 grid grid-cols-7 gap-0.5">
            {calendarDays.map(day => (
              <button key={day.value} type="button" disabled={day.disabled} onClick={() => setDraftDate(day.value)}
                className={`aspect-square rounded-xl text-xs transition-colors ${
                  day.selected ? 'bg-primary text-white shadow-sm'
                    : day.currentMonth ? 'text-slate-700 hover:bg-white' : 'text-slate-300 hover:bg-white'
                } ${day.disabled ? 'cursor-not-allowed text-slate-200 hover:bg-transparent' : 'cursor-pointer'} ${
                  day.isToday && !day.selected ? 'border border-primary/30 bg-white text-primary' : ''
                }`}>
                {day.label}
              </button>
            ))}
          </div>
        </div>

        {/* 시/분 */}
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <label className="flex items-center gap-1 text-xs font-medium text-slate-500"><Clock size={12} /> 시</label>
            <select value={draftHour} onChange={e => setDraftHour(e.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700 outline-none focus:border-primary/40">
              {HOUR_OPTIONS.map(h => <option key={h} value={h}>{h}시</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className="flex items-center gap-1 text-xs font-medium text-slate-500"><Clock size={12} /> 분</label>
            <select value={draftMinute} onChange={e => setDraftMinute(e.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700 outline-none focus:border-primary/40">
              {MINUTE_OPTIONS.map(m => <option key={m} value={m}>{m}분</option>)}
            </select>
          </div>
        </div>

        {/* 요약 */}
        <div className="rounded-xl bg-slate-50 px-3 py-2">
          <p className="text-xs text-slate-400">선택한 시간</p>
          <p className="text-sm font-semibold text-slate-700">{formatDateTimeSummary(draftValue)}</p>
        </div>
        {error && <p className="text-xs text-rose-500">{error}</p>}

        {/* 리마인더 (공지용) */}
        {reminders && (
          <div className="flex items-center gap-3 rounded-xl bg-slate-50 px-3 py-2.5">
            <span className="text-xs text-slate-500">🔔 리마인더</span>
            {[7, 3, 1].map(d => (
              <label key={d} className="flex items-center gap-1 text-xs text-slate-600 cursor-pointer">
                <input type="checkbox" checked={reminders[d] || false}
                  onChange={() => setReminders(prev => ({ ...prev, [d]: !prev[d] }))}
                  className="rounded border-slate-300" />
                D-{d}
              </label>
            ))}
          </div>
        )}
      </div>

      <div className="flex items-center justify-between border-t border-slate-100 px-4 py-3">
        {onClear ? (
          <button type="button" onClick={onClear} className="text-xs font-medium text-slate-400 hover:text-slate-600 cursor-pointer">
            {clearLabel}
          </button>
        ) : <div />}
        <button type="button" disabled={Boolean(error)}
          onClick={() => { if (!error) onApply({ date: draftDate, hour: draftHour, minute: draftMinute, reminders }) }}
          className="rounded-lg bg-primary px-4 py-2 text-xs font-medium text-white hover:bg-primary/90 cursor-pointer disabled:opacity-40">
          적용
        </button>
      </div>
    </div>
  )
}
