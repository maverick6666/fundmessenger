import { useEffect, useMemo, useRef, useState } from 'react'
import { CalendarDays, Plus, Trash2, X, ArrowLeft } from 'lucide-react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import DateTimePicker, { createDefaultParts, formatDateTimeSummary, isPastDateTime } from './DateTimePicker'

/** 투표 만들기 — 패널 내 인라인 (portal 없음) */
export default function CreatePollModal({ clubId, chatRoomId, onClose, onCreated }) {
  const defaults = useMemo(() => createDefaultParts(), [])

  const [question, setQuestion] = useState('')
  const [options, setOptions] = useState(['', ''])
  const [multipleChoice, setMultipleChoice] = useState(false)
  const [anonymous, setAnonymous] = useState(false)
  const [hasDeadline, setHasDeadline] = useState(false)
  const [showDeadlinePicker, setShowDeadlinePicker] = useState(false)
  const [deadlineDate, setDeadlineDate] = useState(defaults.date)
  const [deadlineHour, setDeadlineHour] = useState(defaults.hour)
  const [deadlineMinute, setDeadlineMinute] = useState(defaults.minute)
  const [submitting, setSubmitting] = useState(false)

  const optionRefs = useRef([])
  const questionRef = useRef(null)

  useEffect(() => { questionRef.current?.focus() }, [])

  const addOption = () => {
    if (options.length >= 10) return
    setOptions(prev => [...prev, ''])
    window.setTimeout(() => { optionRefs.current[options.length]?.focus() }, 50)
  }

  const removeOption = (index) => {
    if (options.length <= 2) return
    setOptions(prev => prev.filter((_, i) => i !== index))
  }

  const updateOption = (index, value) => {
    setOptions(prev => prev.map((opt, i) => (i === index ? value : opt)))
  }

  const resetDeadline = () => {
    setHasDeadline(false)
    setDeadlineDate(defaults.date)
    setDeadlineHour(defaults.hour)
    setDeadlineMinute(defaults.minute)
  }

  const validOptions = options.filter(opt => opt.trim())
  const deadlineValue = hasDeadline ? `${deadlineDate}T${deadlineHour}:${deadlineMinute}` : ''
  const deadlineError = hasDeadline && isPastDateTime(deadlineDate, deadlineHour, deadlineMinute)
    ? '현재보다 이후 시간으로 설정해 주세요.' : ''
  const canSubmit = question.trim() && validOptions.length >= 2 && !submitting && !deadlineError

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    try {
      const response = await api.post(`/clubs/${clubId}/polls`, {
        chatRoomId, question: question.trim(), options: validOptions,
        multipleChoice, anonymous, deadline: hasDeadline ? deadlineValue : null,
      })
      onCreated(response)
      onClose()
    } catch (error) {
      handleApiError(error, '투표 생성')
    } finally {
      setSubmitting(false)
    }
  }

  // 마감 시간 설정 뷰
  if (showDeadlinePicker) {
    return (
      <DateTimePicker
        initialDate={deadlineDate}
        initialHour={deadlineHour}
        initialMinute={deadlineMinute}
        title="마감 시간 설정"
        clearLabel="마감 없음"
        onApply={({ date, hour, minute }) => {
          setDeadlineDate(date); setDeadlineHour(hour); setDeadlineMinute(minute)
          setHasDeadline(true); setShowDeadlinePicker(false)
        }}
        onClear={() => { resetDeadline(); setShowDeadlinePicker(false) }}
        onBack={() => setShowDeadlinePicker(false)}
      />
    )
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
        <div className="flex items-center gap-2">
          <button onClick={onClose} className="p-1 rounded-full hover:bg-slate-100 cursor-pointer">
            <ArrowLeft size={16} className="text-slate-500" />
          </button>
          <h3 className="text-sm font-bold text-slate-900">투표 만들기</h3>
        </div>
        <button type="button" onClick={onClose} className="rounded-full p-1 text-slate-400 hover:text-slate-600 cursor-pointer">
          <X size={16} />
        </button>
      </div>

      <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">질문</label>
          <input ref={questionRef} type="text" value={question}
            onChange={e => setQuestion(e.target.value.slice(0, 200))}
            placeholder="투표 질문을 입력해 주세요"
            className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/40"
            maxLength={200} />
        </div>

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-600">선택지</label>
          <div className="space-y-2">
            {options.map((option, index) => (
              <div key={index} className="flex items-center gap-2">
                <span className="w-4 flex-shrink-0 text-center text-xs text-slate-400">{index + 1}</span>
                <input ref={el => { optionRefs.current[index] = el }}
                  type="text" value={option} onChange={e => updateOption(index, e.target.value)}
                  placeholder={`선택지 ${index + 1}`}
                  className="flex-1 rounded-xl border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary/40 focus:ring-1 focus:ring-primary/40"
                  maxLength={100} />
                {options.length > 2 && (
                  <button type="button" onClick={() => removeOption(index)}
                    className="rounded-full p-1 text-slate-300 hover:text-red-400 cursor-pointer">
                    <Trash2 size={14} />
                  </button>
                )}
              </div>
            ))}
          </div>
          {options.length < 10 && (
            <button type="button" onClick={addOption}
              className="mt-2 flex items-center gap-1 text-xs text-primary hover:text-primary/80 cursor-pointer">
              <Plus size={12} /> 선택지 추가
            </button>
          )}
        </div>

        <div className="space-y-2">
          <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" checked={multipleChoice} onChange={e => setMultipleChoice(e.target.checked)}
              className="h-3.5 w-3.5 cursor-pointer rounded border-slate-300 text-primary" />
            <span className="text-xs text-slate-700">복수 선택 허용</span>
          </label>
          <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" checked={anonymous} onChange={e => setAnonymous(e.target.checked)}
              className="h-3.5 w-3.5 cursor-pointer rounded border-slate-300 text-primary" />
            <span className="text-xs text-slate-700">익명 투표</span>
          </label>
        </div>

        <button type="button" onClick={() => setShowDeadlinePicker(true)}
          className="flex w-full items-center justify-between rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-left hover:border-primary/20 hover:bg-white cursor-pointer">
          <div className="flex items-center gap-2">
            <CalendarDays className="h-4 w-4 text-slate-400" />
            <div>
              <p className="text-xs font-medium text-slate-700">마감 시간</p>
              <p className="text-xs text-slate-400">{hasDeadline ? formatDateTimeSummary(deadlineValue) : '설정 없음'}</p>
            </div>
          </div>
          <span className="text-xs text-primary font-medium">{hasDeadline ? '수정' : '추가'}</span>
        </button>
        {deadlineError && <p className="text-xs text-rose-500">{deadlineError}</p>}

        <button type="submit" disabled={!canSubmit}
          className="w-full rounded-xl bg-primary py-2.5 text-sm font-medium text-white hover:bg-primary/90 cursor-pointer disabled:opacity-40">
          {submitting ? '생성 중...' : '생성'}
        </button>
      </form>
    </div>
  )
}
