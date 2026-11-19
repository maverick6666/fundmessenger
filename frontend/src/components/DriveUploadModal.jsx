import { useState, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { useOutletContext } from 'react-router-dom'
import api from '../lib/api'
import ConfirmModal from './ConfirmModal'
import handleApiError from '../lib/handleApiError'
import useToastStore from '../store/toastStore'
import { API_URL } from '../lib/api'

export default function DriveUploadModal({ file, onClose, onUploaded, defaultTeamId, defaultFolderId }) {
  const { clubInfo } = useOutletContext()
  const [categories, setCategories] = useState([])
  const [driveFolders, setDriveFolders] = useState([])
  const [displayName, setDisplayName] = useState(() => file?.name?.replace(/\.[^/.]+$/, '') || '')
  const [selectedTeamIds, setSelectedTeamIds] = useState(() => defaultTeamId ? [Number(defaultTeamId)] : [])
  const [selectedFolderIds, setSelectedFolderIds] = useState(() => defaultFolderId ? [Number(defaultFolderId)] : [])
  const [selectedCategoryIds, setSelectedCategoryIds] = useState([])
  const [description, setDescription] = useState('')
  const [uploading, setUploading] = useState(false)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  // 소속 팀 목록 (clubInfo.myTeams)
  const myTeams = clubInfo?.myTeams || []

  useEffect(() => {
    if (!clubInfo?.clubId) return
    api.get(`/clubs/${clubInfo.clubId}/semi-categories`)
      .then(catData => setCategories(catData || []))
      .catch(e => handleApiError(e, '카테고리 조회'))
    api.get(`/clubs/${clubInfo.clubId}/drive/folders`)
      .then(data => setDriveFolders(data || []))
      .catch(e => handleApiError(e, '드라이브 폴더 조회'))
  }, [clubInfo?.clubId])

  const toggleTeam = (teamId) => {
    setSelectedTeamIds(prev =>
      prev.includes(teamId) ? prev.filter(id => id !== teamId) : [...prev, teamId]
    )
  }

  const toggleFolder = (folderId) => {
    setSelectedFolderIds(prev =>
      prev.includes(folderId) ? prev.filter(id => id !== folderId) : [...prev, folderId]
    )
  }

  const toggleCategory = (catId) => {
    setSelectedCategoryIds(prev =>
      prev.includes(catId) ? prev.filter(id => id !== catId) : [...prev, catId]
    )
  }

  const handleUpload = async () => {
    if (selectedCategoryIds.length === 0) return setAlertModal({ open: true, message: '태그를 최소 1개 선택해야 합니다' })
    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      if (displayName.trim()) formData.append('displayName', displayName.trim())
      selectedTeamIds.forEach(id => formData.append('teamIds', id))
      selectedFolderIds.forEach(id => formData.append('folderIds', id))
      selectedCategoryIds.forEach(id => formData.append('semiCategoryIds', id))
      if (description.trim()) formData.append('description', description.trim())

      const token = localStorage.getItem('fm_token')
      const res = await fetch(`${API_URL}/clubs/${clubInfo.clubId}/drive/files`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      })
      if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        throw new Error(err.error || '업로드 실패')
      }
      const data = await res.json()
      useToastStore.getState().addToast({ type: 'SUCCESS', title: '파일이 업로드되었습니다' })
      onUploaded?.(data)
      onClose()
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setUploading(false)
    }
  }

  const formatSize = (bytes) => {
    if (!bytes) return ''
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  return createPortal(
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/40" onClick={onClose}>
      <div className="bg-white rounded-2xl w-full max-w-md shadow-xl" onClick={e => e.stopPropagation()}>
        <div className="p-5 border-b border-slate-100">
          <h3 className="text-base font-bold text-slate-900">파일 업로드</h3>
        </div>

        <div className="p-5 space-y-4">
          {/* 파일명 편집 */}
          <div>
            <label className="text-xs font-medium text-slate-500 mb-1 block">파일명</label>
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={displayName}
                onChange={e => setDisplayName(e.target.value)}
                className="flex-1 px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                placeholder="파일명을 입력하세요"
              />
              <span className="text-xs text-slate-400 shrink-0">.{file.name?.split('.').pop()}</span>
            </div>
          </div>

          {/* 파일 정보 */}
          <div className="flex items-center gap-3 p-3 bg-slate-50 rounded-lg">
            <div className="w-10 h-10 bg-red-50 rounded-lg flex items-center justify-center shrink-0">
              <svg className="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
              </svg>
            </div>
            <div className="min-w-0">
              <p className="text-sm font-medium text-slate-900 truncate">{file.name}</p>
              <p className="text-xs text-slate-400">{formatSize(file.size)}</p>
            </div>
          </div>

          {/* 팀 선택 (pill 다중선택, 소속 팀만) */}
          <div>
            <label className="text-xs font-medium text-slate-500 mb-1 block">
              팀 선택 <span className="text-slate-400">(선택하지 않으면 자유)</span>
            </label>
            {myTeams.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {myTeams.map(t => (
                  <button
                    key={t.teamId}
                    onClick={() => toggleTeam(t.teamId)}
                    className={`px-3 py-1.5 text-xs rounded-full border transition-colors cursor-pointer ${
                      selectedTeamIds.includes(t.teamId)
                        ? 'bg-emerald-500 text-white border-emerald-500'
                        : 'bg-white text-slate-600 border-slate-200 hover:border-emerald-300'
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

          {/* 폴더 선택 (pill 다중선택) */}
          {driveFolders.length > 0 && (
            <div>
              <label className="text-xs font-medium text-slate-500 mb-1 block">
                폴더 선택 <span className="text-slate-400">(선택사항)</span>
              </label>
              <div className="flex flex-wrap gap-2">
                {driveFolders.map(f => (
                  <button
                    key={f.id}
                    onClick={() => toggleFolder(f.id)}
                    className={`px-3 py-1.5 text-xs rounded-full border transition-colors cursor-pointer ${
                      selectedFolderIds.includes(f.id)
                        ? 'bg-violet-500 text-white border-violet-500'
                        : 'bg-white text-slate-600 border-slate-200 hover:border-violet-300'
                    }`}
                  >
                    {f.name}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* 태그 선택 */}
          <div>
            <label className="text-xs font-medium text-slate-500 mb-1 block">
              태그 선택 <span className="text-red-400">(최소 1개 필수)</span>
            </label>
            <div className="flex flex-wrap gap-2">
              {categories.map(cat => (
                <button
                  key={cat.id}
                  onClick={() => toggleCategory(cat.id)}
                  className={`px-3 py-1.5 text-xs rounded-full border transition-colors cursor-pointer ${
                    selectedCategoryIds.includes(cat.id)
                      ? 'bg-primary text-white border-blue-500'
                      : 'bg-white text-slate-600 border-slate-200 hover:border-blue-300'
                  }`}
                >
                  {cat.name}
                </button>
              ))}
              {categories.length === 0 && (
                <p className="text-xs text-slate-400">등록된 태그가 없습니다</p>
              )}
            </div>
          </div>

          {/* 설명 */}
          <div>
            <label className="text-xs font-medium text-slate-500 mb-1 block">설명 (선택)</label>
            <input
              type="text"
              value={description}
              onChange={e => setDescription(e.target.value)}
              placeholder="파일에 대한 설명을 입력하세요"
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20"
            />
          </div>
        </div>

        <div className="p-5 border-t border-slate-100 flex justify-end gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm text-slate-500 hover:text-slate-700 cursor-pointer"
          >
            취소
          </button>
          <button
            onClick={handleUpload}
            disabled={uploading || selectedCategoryIds.length === 0}
            className="px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50 transition-colors cursor-pointer"
          >
            {uploading ? '업로드 중...' : '업로드'}
          </button>
        </div>

        {alertModal.open && (
          <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
        )}
      </div>
    </div>,
    document.body
  )
}
