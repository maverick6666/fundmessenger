import { useState, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { formatTime } from '../../lib/format'

/**
 * 전체화면 문서 뷰어 모달 — v1 DocumentViewer 포팅 (라이트모드)
 *
 * - 마크다운 렌더링 (BlockRenderer 제거)
 * - 의사결정서/보고서 전체화면 보기
 */
export function DocumentViewer({ isOpen, onClose, doc, type = 'decision-note', onEdit, onDelete }) {
  const [isClosing, setIsClosing] = useState(false)

  useEffect(() => {
    if (isOpen) document.body.style.overflow = 'hidden'
    else document.body.style.overflow = ''
    return () => { document.body.style.overflow = '' }
  }, [isOpen])

  const handleClose = () => {
    setIsClosing(true)
    setTimeout(() => { setIsClosing(false); onClose() }, 200)
  }

  if (!isOpen || !doc) return null

  return (
    <div className={`fixed inset-0 z-50 transition-all duration-200 ${isClosing ? 'opacity-0' : 'opacity-100'}`}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={handleClose} />

      <div className="absolute inset-0 overflow-y-auto">
        <div className="min-h-full flex items-start justify-center py-8 px-4">
          <div className={`relative w-full max-w-3xl bg-white border border-slate-200 rounded-2xl shadow-2xl transform transition-all duration-200 ${
            isClosing ? 'scale-95 opacity-0' : 'scale-100 opacity-100'
          }`}>
            {/* 헤더 */}
            <header className="sticky top-0 z-10 flex items-center justify-between px-8 py-5 border-b border-slate-100 bg-white/95 backdrop-blur-sm rounded-t-2xl">
              <div className="flex items-center gap-4">
                <span className={`px-2.5 py-1 text-xs font-medium rounded-md ${
                  type === 'column'
                    ? 'bg-purple-50 text-purple-600 ring-1 ring-purple-200'
                    : 'bg-emerald-50 text-emerald-600 ring-1 ring-emerald-200'
                }`}>
                  {type === 'column' ? '칼럼' : '의사결정서'}
                </span>
                {type === 'decision-note' && doc.position && (
                  <span className="text-sm text-slate-500">
                    {doc.position.tickerName || doc.position.ticker}
                  </span>
                )}
              </div>
              <button onClick={handleClose}
                className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </header>

            {/* 본문 */}
            <div className="px-8 py-8">
              <h1 className="text-3xl sm:text-4xl font-semibold text-slate-900 mb-6 leading-tight"
                style={{ fontFamily: "'Noto Serif KR', 'Crimson Pro', serif" }}>
                {doc.title}
              </h1>

              {(doc.author || doc.authorName) && (
                <div className="flex items-center gap-4 mb-8 pb-6 border-b border-slate-100">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-gradient-to-br from-emerald-400 to-emerald-600 flex items-center justify-center text-white font-medium">
                      {(doc.author?.fullName || doc.authorName || '?').charAt(0)}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-slate-700">{doc.author?.fullName || doc.authorName}</p>
                      <p className="text-xs text-slate-500">
                        {formatTime(doc.createdAt)}
                        {doc.updatedAt && doc.updatedAt !== doc.createdAt && (
                          <span className="ml-2 text-slate-400">(수정됨)</span>
                        )}
                      </p>
                    </div>
                  </div>
                </div>
              )}

              <article>
                {doc.content ? (
                  <div className="prose prose-lg max-w-none prose-headings:font-semibold prose-p:leading-relaxed prose-a:text-indigo-600">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>
                      {doc.content}
                    </ReactMarkdown>
                  </div>
                ) : (
                  <p className="text-slate-500 italic">내용이 없습니다.</p>
                )}
              </article>
            </div>

            {/* 하단 액션 */}
            {(onEdit || onDelete) && (
              <footer className="sticky bottom-0 flex items-center justify-end gap-3 px-8 py-4 border-t border-slate-100 bg-white/95 backdrop-blur-sm rounded-b-2xl">
                {onEdit && (
                  <button onClick={() => { onEdit(doc); onClose() }}
                    className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-slate-600 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-lg transition-colors">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                    수정
                  </button>
                )}
                {onDelete && (
                  <button onClick={() => onDelete(doc?.id)}
                    className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-red-600 bg-red-50 hover:bg-red-100 border border-red-200 rounded-lg transition-colors">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                    삭제
                  </button>
                )}
              </footer>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * 포지션 노트 목록 모달
 */
export function PositionNotesViewer({ isOpen, onClose, position, notes = [], onNoteClick, onGenerateReport, isGenerating = false }) {
  const [isClosing, setIsClosing] = useState(false)

  const handleClose = () => {
    setIsClosing(true)
    setTimeout(() => { setIsClosing(false); onClose() }, 200)
  }

  if (!isOpen || !position) return null

  return (
    <div className={`fixed inset-0 z-50 transition-all duration-200 ${isClosing ? 'opacity-0' : 'opacity-100'}`}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={handleClose} />

      <div className="absolute inset-0 overflow-y-auto">
        <div className="min-h-full flex items-start justify-center py-8 px-4">
          <div className={`relative w-full max-w-2xl bg-white border border-slate-200 rounded-2xl shadow-2xl transform transition-all duration-200 ${
            isClosing ? 'scale-95 opacity-0' : 'scale-100 opacity-100'
          }`}>
            {/* 헤더 */}
            <header className="flex items-start justify-between px-6 py-5 border-b border-slate-100">
              <div>
                <div className="flex items-center gap-3 mb-2">
                  <span className={`px-2 py-0.5 text-xs font-medium rounded ${
                    position.status === 'OPEN' ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-100 text-slate-500'
                  }`}>
                    {position.status === 'OPEN' ? '보유중' : '종료'}
                  </span>
                  <span className="text-sm text-slate-500 font-mono">{position.ticker} · {position.market}</span>
                </div>
                <h2 className="text-2xl font-semibold text-slate-900" style={{ fontFamily: "'Noto Serif KR', serif" }}>
                  {position.tickerName || position.ticker}
                </h2>
              </div>
              <button onClick={handleClose}
                className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </header>

            {/* 통계 */}
            <div className="flex items-center gap-6 px-6 py-4 border-b border-slate-100">
              <div className="flex items-center gap-2 text-sm">
                <svg className="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <span className="text-slate-500">노트 {notes.length}개</span>
              </div>

              {onGenerateReport && (
                <button onClick={() => onGenerateReport(position.id)} disabled={isGenerating}
                  className="ml-auto flex items-center gap-2 px-3 py-1.5 text-xs font-medium text-purple-600 bg-purple-50 hover:bg-purple-100 border border-purple-200 rounded-lg transition-colors disabled:opacity-50">
                  {isGenerating ? (
                    <><span className="animate-spin inline-block w-3.5 h-3.5 border-2 border-purple-400 border-t-transparent rounded-full" /> 생성 중...</>
                  ) : (
                    <>
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                      </svg>
                      AI 보고서
                    </>
                  )}
                </button>
              )}
            </div>

            {/* 노트 목록 */}
            <div className="p-4 max-h-[400px] overflow-y-auto">
              {notes.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <div className="w-12 h-12 rounded-xl bg-slate-50 flex items-center justify-center mb-4">
                    <svg className="w-6 h-6 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                  </div>
                  <p className="text-slate-500 mb-1">작성된 노트가 없습니다</p>
                  <p className="text-sm text-slate-400">포지션 상세에서 의사결정 노트를 작성할 수 있습니다</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {notes.map(note => (
                    <button key={note.id} onClick={() => onNoteClick?.(note)}
                      className="w-full text-left p-4 bg-slate-50/50 hover:bg-slate-100 border border-slate-200 shadow-sm hover:border-slate-200 rounded-xl transition-all group">
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex-1 min-w-0">
                          <h3 className="font-medium text-slate-800 group-hover:text-slate-900 truncate">{note.title}</h3>
                          {note.content && (
                            <p className="text-sm text-slate-500 mt-1 line-clamp-2">{note.content.substring(0, 150)}...</p>
                          )}
                        </div>
                        <div className="shrink-0 text-right">
                          <span className="text-xs text-slate-400">{formatTime(note.createdAt)}</span>
                          {(note.author || note.authorName) && (
                            <p className="text-xs text-slate-500 mt-1">{note.author?.fullName || note.authorName}</p>
                          )}
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
