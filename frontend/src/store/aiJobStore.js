import { create } from 'zustand'
import api from '../lib/api'

/**
 * AI 글생성 작업 추적 — 글로벌 Zustand 스토어
 * ClubLayout 레벨에서 AiJobTracker 카드가 이 스토어를 구독
 */
const EDITOR_TRANSFER_STORAGE_KEY = 'fm_ai_editor_transfer'

const readEditorTransfer = () => {
  if (typeof window === 'undefined') return null

  try {
    const raw = window.sessionStorage.getItem(EDITOR_TRANSFER_STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

const writeEditorTransfer = (payload) => {
  if (typeof window === 'undefined') return

  try {
    if (!payload) {
      window.sessionStorage.removeItem(EDITOR_TRANSFER_STORAGE_KEY)
      return
    }
    window.sessionStorage.setItem(EDITOR_TRANSFER_STORAGE_KEY, JSON.stringify(payload))
  } catch {
    // Best-effort bridge for editor handoff.
  }
}

const useAiJobStore = create((set, get) => ({
  clubId: null,
  jobId: null,
  job: null,       // { status, driveFileName, resultMarkdown, errorMessage }
  editorTransfer: readEditorTransfer(),
  polling: false,
  _intervalId: null,
  _errorCount: 0,

  /** AI 글생성 요청 시작 — jobId 설정 + 폴링 시작 */
  startTracking: (clubId, jobId) => {
    const prev = get()._intervalId
    if (prev) clearInterval(prev)

    set({ clubId, jobId, job: { status: 'QUEUED' }, polling: true, _errorCount: 0 })

    const poll = async () => {
      const { clubId: cid, jobId: jid } = get()
      if (!cid || !jid) return
      try {
        const data = await api.get(`/clubs/${cid}/ai/jobs/${jid}`)
        set({ job: data, _errorCount: 0 })
        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
          get().stopPolling()
        }
      } catch (err) {
        const count = get()._errorCount + 1
        set({ _errorCount: count })
        if (count >= 5) {
          set({ job: { status: 'FAILED', errorMessage: '서버 연결 실패' } })
          get().stopPolling()
        }
      }
    }

    poll()
    const id = setInterval(poll, 2000)
    set({ _intervalId: id })
  },

  stopPolling: () => {
    const id = get()._intervalId
    if (id) clearInterval(id)
    set({ polling: false, _intervalId: null })
  },

  /** 작업 완전 초기화 (닫기/발행 후) */
  clear: () => {
    get().stopPolling()
    set({ clubId: null, jobId: null, job: null, _errorCount: 0 })
  },

  setEditorTransfer: (payload) => {
    writeEditorTransfer(payload)
    set({ editorTransfer: payload })
  },

  consumeEditorTransfer: () => {
    const payload = get().editorTransfer ?? readEditorTransfer()
    writeEditorTransfer(null)
    set({ editorTransfer: null })
    return payload
  },

  /** 진행중 작업이 있는지 */
  hasActiveJob: () => {
    const { job } = get()
    return job && (job.status === 'QUEUED' || job.status === 'PROCESSING')
  },
}))

export default useAiJobStore
