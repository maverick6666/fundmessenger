import { useEffect, useRef, useState, useCallback } from 'react'
import useSidePanelStore from '../../store/sidePanelStore'
import DocumentPanel from './DocumentPanel'

/**
 * 노션 스타일 사이드 패널 — v1 SidePanel 포팅 (라이트모드 전용)
 *
 * - 오른쪽에서 슬라이드인
 * - 메인 콘텐츠와 동시 상호작용 가능
 * - ESC로 닫기
 * - 드래그로 너비 조절
 */
export function FundSidePanel() {
  const { isOpen, panelType, panelData, closePanel, panelWidth, setPanelWidth } = useSidePanelStore()
  const panelRef = useRef(null)
  const [isResizing, setIsResizing] = useState(false)
  const [isMobile, setIsMobile] = useState(false)

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < 768)
    check()
    window.addEventListener('resize', check)
    return () => window.removeEventListener('resize', check)
  }, [])

  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape' && isOpen) closePanel() }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [isOpen, closePanel])

  const handleMouseDown = useCallback((e) => {
    e.preventDefault()
    setIsResizing(true)
    const startX = e.clientX
    const startWidth = panelWidth

    const onMove = (e) => setPanelWidth(startWidth + (startX - e.clientX))
    const onUp = () => {
      setIsResizing(false)
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
    }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
  }, [panelWidth, setPanelWidth])

  if (!isOpen) return null

  return (
    <>
      {/* 반투명 오버레이 */}
      <div
        className={`fixed inset-0 z-40 ${isMobile ? 'bg-black/40' : 'bg-black/20 lg:hidden'}`}
        onClick={closePanel}
      />

      {/* 사이드 패널 */}
      <aside
        ref={panelRef}
        className={`
          fixed z-50 transform transition-transform duration-300 ease-out
          ${isOpen ? 'translate-x-0' : 'translate-x-full'}
          flex flex-col shadow-2xl bg-white border-l border-slate-200
          ${isMobile ? 'inset-0 w-full h-full' : 'top-0 right-0 h-screen'}
        `}
        style={isMobile ? {} : { width: `${panelWidth}px`, minWidth: '400px', maxWidth: '90vw' }}
      >
        {/* 리사이즈 핸들 (데스크톱) */}
        {!isMobile && (
          <div
            className={`absolute left-0 top-0 h-full w-1 cursor-ew-resize z-10 group transition-colors ${
              isResizing ? 'bg-indigo-400' : 'hover:bg-indigo-500/50'
            }`}
            onMouseDown={handleMouseDown}
          >
            <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-1 h-8 rounded-full opacity-0 group-hover:opacity-100 transition-opacity bg-slate-400" />
          </div>
        )}

        {/* 헤더 (document 타입용) */}
        {panelType === 'document' && (
          <header className="flex items-center justify-between px-5 py-4 border-b border-slate-200 shrink-0">
            <div className="flex items-center gap-3">
              {panelData?.documentType && (
                <span className={`px-2.5 py-1 text-xs font-medium rounded-md ${
                  panelData.documentType === 'column'
                    ? 'bg-purple-50 text-purple-600 ring-1 ring-purple-200'
                    : 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200'
                }`}>
                  {panelData.documentType === 'column' ? '칼럼' : '의사결정서'}
                </span>
              )}
            </div>
            <button
              onClick={closePanel}
              className="p-2 rounded-lg transition-colors text-slate-500 hover:text-slate-700 hover:bg-slate-100"
              title="닫기 (ESC)"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </header>
        )}

        {/* 커스텀 타입은 자체 헤더 포함 */}
        {panelType === 'custom' && (
          <header className="flex items-center justify-end px-5 py-4 border-b border-slate-200 shrink-0">
            <button onClick={closePanel} className="p-2 rounded-lg text-slate-500 hover:text-slate-700 hover:bg-slate-100">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </header>
        )}

        {/* 패널 콘텐츠 */}
        <div className="flex-1 min-h-0 overflow-y-auto">
          {panelType === 'document' && panelData?.document && (
            <DocumentPanel
              document={panelData.document}
              type={panelData.documentType}
              onSaved={panelData?.onSaved}
            />
          )}
          {panelType === 'custom' && panelData?.render && (
            <div className="h-full overflow-y-auto">
              {panelData.render()}
            </div>
          )}
        </div>
      </aside>

      {/* 리사이즈 중 커서 고정 */}
      {isResizing && (
        <style>{`* { cursor: ew-resize !important; user-select: none !important; }`}</style>
      )}
    </>
  )
}
