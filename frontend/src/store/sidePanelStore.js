import { create } from 'zustand'

/**
 * 노션 스타일 사이드 패널 전역 상태
 *
 * - 오른쪽에서 슬라이드인
 * - 다른 문서 클릭 시 내용만 교체 (무한 중첩 X)
 * - 왼쪽 메인 콘텐츠는 상호작용 가능
 */
const useSidePanelStore = create((set, get) => ({
  isOpen: false,
  panelType: null,   // 'document' | 'note-editor' | 'custom'
  panelData: null,
  panelWidth: 520,   // 기본 너비 (px)

  openPanel: ({ type, data }) => {
    set({ isOpen: true, panelType: type, panelData: data })
  },

  replacePanel: ({ type, data }) => {
    set({ panelType: type, panelData: data })
  },

  closePanel: () => {
    set({ isOpen: false, panelType: null, panelData: null })
  },

  /** 문서 뷰어 열기 */
  openDocument: (document, documentType = 'decision-note', onSaved = null) => {
    const { isOpen, openPanel, replacePanel } = get()
    const action = isOpen ? replacePanel : openPanel
    action({ type: 'document', data: { document, documentType, onSaved } })
  },

  /** 의사결정노트 에디터 열기 */
  openNoteEditor: (note, onSaved = null) => {
    get().openPanel({ type: 'note-editor', data: { note, onSaved } })
  },

  setPanelWidth: (width) => {
    set({ panelWidth: Math.max(400, Math.min(width, 800)) })
  },
}))

export default useSidePanelStore
