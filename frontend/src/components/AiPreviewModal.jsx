import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import api from '../lib/api'
import NewsMarkdownRenderer from './NewsMarkdownRenderer'
import ConfirmModal from './ConfirmModal'
import handleApiError from '../lib/handleApiError'

export default function AiPreviewModal({
  clubId,
  jobId,
  markdown,
  title = 'AI 생성 미리보기',
  subtitle = null,
  clubInfo,
  onPublish,
  onRegenerate,
  onClose,
}) {
  const [publishing, setPublishing] = useState(false)
  const [regenerating, setRegenerating] = useState(false)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  // 발행 옵션 스텝
  const [showPublishOptions, setShowPublishOptions] = useState(false)
  const [selectedTeamIds, setSelectedTeamIds] = useState([])
  const [selectedBoardIds, setSelectedBoardIds] = useState([])
  const [boards, setBoards] = useState([])
  const [boardsLoading, setBoardsLoading] = useState(false)

  const myTeams = clubInfo?.myTeams || []

  // 게시판 목록 로드 (발행 옵션 열릴 때)
  useEffect(() => {
    if (!showPublishOptions || !clubId) return
    setBoardsLoading(true)
    api.get(`/clubs/${clubId}/boards/sidebar`)
      .then((data) => {
        // sidebar API: [{boardId, boardName, boardType, postCount}]
        setBoards(data || [])
      })
      .catch((err) => handleApiError(err, '게시판 목록 조회'))
      .finally(() => setBoardsLoading(false))
  }, [showPublishOptions, clubId])

  const toggleTeam = (teamId) => {
    setSelectedTeamIds((prev) =>
      prev.includes(teamId) ? prev.filter((id) => id !== teamId) : [...prev, teamId],
    )
  }

  const toggleBoard = (boardId) => {
    setSelectedBoardIds((prev) =>
      prev.includes(boardId) ? prev.filter((id) => id !== boardId) : [...prev, boardId],
    )
  }

  const handlePublishClick = () => {
    if (!onPublish) return
    setShowPublishOptions(true)
  }

  const handlePublishConfirm = async () => {
    if (!onPublish) return

    setPublishing(true)
    try {
      const body = {}
      if (selectedTeamIds.length > 0) body.teamIds = selectedTeamIds
      if (selectedBoardIds.length > 0) body.boardIds = selectedBoardIds
      const result = await api.post(`/clubs/${clubId}/ai/jobs/${jobId}/publish`, body)
      onPublish(result.postId)
    } catch (err) {
      setAlertModal({ open: true, message: err.message || '발행에 실패했습니다.' })
    } finally {
      setPublishing(false)
    }
  }

  const handleRegenerate = async () => {
    if (!onRegenerate) return

    setRegenerating(true)
    try {
      const result = await api.post(`/clubs/${clubId}/ai/jobs/${jobId}/regenerate`)
      onRegenerate(result)
    } catch (err) {
      setAlertModal({ open: true, message: err.message || '재생성에 실패했습니다.' })
    } finally {
      setRegenerating(false)
    }
  }

  return createPortal(
    <div
      className="fixed inset-0 z-[60] modal-overlay flex items-start justify-center overflow-y-auto bg-black/40 py-8"
      onClick={onClose}
    >
      <div
        className="mx-4 flex max-h-[calc(100vh-4rem)] w-full max-w-3xl flex-col rounded-2xl bg-white shadow-xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-violet-500">AI</span>
              <h2 className="text-base font-bold text-slate-900">{title}</h2>
            </div>
            {subtitle && (
              <p className="mt-1 truncate text-xs text-slate-400">{subtitle}</p>
            )}
          </div>
          <button
            onClick={onClose}
            className="cursor-pointer text-lg text-slate-400 hover:text-slate-600"
            aria-label="미리보기 닫기"
          >
            ×
          </button>
        </div>

        {!showPublishOptions ? (
          /* ── 미리보기 본문 ── */
          <>
            <div className="flex-1 overflow-y-auto px-6 py-4">
              <NewsMarkdownRenderer content={markdown} />
            </div>

            <div className="flex flex-wrap justify-end gap-3 border-t border-slate-100 px-6 py-4">
              <button
                onClick={onClose}
                className="cursor-pointer px-4 py-2 text-sm text-slate-500 hover:text-slate-700"
              >
                닫기
              </button>
              {onRegenerate && (
                <button
                  onClick={handleRegenerate}
                  disabled={regenerating}
                  className="cursor-pointer rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                >
                  {regenerating ? '재생성 중...' : '재생성'}
                </button>
              )}
              {onPublish && (
                <button
                  onClick={handlePublishClick}
                  className="cursor-pointer rounded-lg bg-violet-500 px-4 py-2 text-sm text-white hover:bg-violet-600"
                >
                  바로 발행
                </button>
              )}
            </div>
          </>
        ) : (
          /* ── 발행 옵션 스텝 ── */
          <>
            <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
              <p className="text-sm font-semibold text-slate-800">발행 옵션</p>

              {/* 팀 선택 */}
              <div>
                <label className="mb-1.5 block text-xs font-medium text-slate-500">
                  팀 선택 <span className="text-slate-400">(선택하지 않으면 원본 파일의 팀 유지)</span>
                </label>
                {myTeams.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {myTeams.map((t) => (
                      <button
                        key={t.teamId}
                        onClick={() => toggleTeam(t.teamId)}
                        className={`cursor-pointer rounded-full border px-3 py-1.5 text-xs transition-colors ${
                          selectedTeamIds.includes(t.teamId)
                            ? 'border-emerald-500 bg-emerald-500 text-white'
                            : 'border-slate-200 bg-white text-slate-600 hover:border-emerald-300'
                        }`}
                      >
                        {t.teamName}
                      </button>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400">소속된 팀이 없습니다</p>
                )}
              </div>

              {/* 게시판 선택 */}
              <div>
                <label className="mb-1.5 block text-xs font-medium text-slate-500">
                  게시판 선택 <span className="text-slate-400">(선택하지 않으면 기본 게시판 유지)</span>
                </label>
                {boardsLoading ? (
                  <div className="flex justify-center py-3">
                    <div className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-slate-600" />
                  </div>
                ) : boards.length > 0 ? (
                  <div className="flex flex-wrap gap-2">
                    {boards.map((b) => (
                      <button
                        key={b.boardId}
                        onClick={() => toggleBoard(b.boardId)}
                        className={`cursor-pointer rounded-full border px-3 py-1.5 text-xs transition-colors ${
                          selectedBoardIds.includes(b.boardId)
                            ? 'border-blue-500 bg-primary text-white'
                            : 'border-slate-200 bg-white text-slate-600 hover:border-blue-300'
                        }`}
                      >
                        {b.boardName}
                      </button>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400">게시판이 없습니다</p>
                )}
              </div>
            </div>

            <div className="flex flex-wrap justify-end gap-3 border-t border-slate-100 px-6 py-4">
              <button
                onClick={() => setShowPublishOptions(false)}
                className="cursor-pointer px-4 py-2 text-sm text-slate-500 hover:text-slate-700"
              >
                뒤로
              </button>
              <button
                onClick={handlePublishConfirm}
                disabled={publishing}
                className="cursor-pointer rounded-lg bg-violet-500 px-4 py-2 text-sm text-white hover:bg-violet-600 disabled:opacity-50"
              >
                {publishing ? '발행 중...' : '발행 확인'}
              </button>
            </div>
          </>
        )}
      </div>

      {alertModal.open && (
        <ConfirmModal
          title="알림"
          message={alertModal.message}
          onConfirm={() => setAlertModal({ open: false, message: '' })}
          onClose={() => setAlertModal({ open: false, message: '' })}
          confirmLabel="확인"
          cancelLabel=""
        />
      )}
    </div>,
    document.body
  )
}
