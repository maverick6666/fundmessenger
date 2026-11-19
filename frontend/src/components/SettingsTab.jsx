import { useState, useEffect, useRef } from 'react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'

export default function SettingsTab({ clubId }) {
  const [settings, setSettings] = useState(null)
  const [form, setForm] = useState({})
  const [editing, setEditing] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [message, setMessage] = useState('')
  const [logoImgError, setLogoImgError] = useState(false)
  const [botSettings, setBotSettings] = useState({ enabled: false, scope: 'BOTH' })
  const [botSaving, setBotSaving] = useState(false)
  const fileInputRef = useRef(null)

  useEffect(() => {
    fetchSettings()
    fetchBotSettings()
  }, [clubId])

  const fetchSettings = async () => {
    try {
      setLoading(true)
      const res = await api.request(`/clubs/${clubId}/settings`)
      setSettings(res)
      setForm({
        clubDescription: res.clubDescription || '',
        clubLogoUrl: res.clubLogoUrl || '',
        weeklyReviewBotName: res.weeklyReviewBotName || '',
      })
    } catch (e) {
      handleApiError(e, '동아리 설정 조회')
      setMessage('설정을 불러올 수 없습니다')
    } finally {
      setLoading(false)
    }
  }

  const fetchBotSettings = async () => {
    try {
      const res = await api.get(`/clubs/${clubId}/ai-comment-bot/settings`)
      setBotSettings(res)
    } catch (e) {
      handleApiError(e, 'AI 댓글봇 설정 조회')
    }
  }

  const updateBotSettings = async (updates) => {
    try {
      setBotSaving(true)
      const res = await api.request(`/clubs/${clubId}/ai-comment-bot/settings`, {
        method: 'PUT',
        body: JSON.stringify(updates)
      })
      setBotSettings(res)
    } catch (e) {
      handleApiError(e, 'AI 댓글봇 설정 저장')
      setMessage(e.message || 'AI 댓글봇 설정 저장 실패')
      setTimeout(() => setMessage(''), 3000)
    } finally {
      setBotSaving(false)
    }
  }

  const handleLogoUpload = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return

    // 이미지 타입 검증
    if (!file.type.startsWith('image/')) {
      setMessage('이미지 파일만 업로드할 수 있습니다')
      setTimeout(() => setMessage(''), 3000)
      return
    }
    // 10MB 제한
    if (file.size > 10 * 1024 * 1024) {
      setMessage('파일 크기는 10MB 이하여야 합니다')
      setTimeout(() => setMessage(''), 3000)
      return
    }

    try {
      setUploading(true)
      const formData = new FormData()
      formData.append('file', file)
      const res = await api.upload('/files/images', formData)
      setForm(f => ({ ...f, clubLogoUrl: res.url }))
      setMessage('로고가 업로드되었습니다. 저장을 눌러 적용하세요.')
      setTimeout(() => setMessage(''), 3000)
    } catch (err) {
      handleApiError(err, '로고 업로드')
      setMessage(err.message || '업로드에 실패했습니다')
      setTimeout(() => setMessage(''), 3000)
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleSave = async () => {
    try {
      setSaving(true)
      setMessage('')
      const res = await api.request(`/clubs/${clubId}/settings`, {
        method: 'PUT',
        body: JSON.stringify(form)
      })
      setSettings(res)
      setEditing(false)
      setMessage('저장되었습니다')
      setTimeout(() => setMessage(''), 2000)
    } catch (e) {
      handleApiError(e, '동아리 설정 저장')
      setMessage(e.message || '저장에 실패했습니다')
    } finally {
      setSaving(false)
    }
  }

  // 로고 표시 URL: 편집 모드에서는 form 값, 아니면 settings 값
  const displayLogoUrl = editing ? form.clubLogoUrl : settings?.clubLogoUrl

  // 로고 URL 변경 시 에러 상태 리셋
  useEffect(() => { setLogoImgError(false) }, [displayLogoUrl])

  if (loading) return <p className="text-sm text-slate-400">로딩 중...</p>

  return (
    <div className="space-y-6">
      {message && (
        <p className={`text-sm px-3 py-2 rounded-lg ${message.includes('실패') || message.includes('없습니다') ? 'bg-red-50 text-red-500' : 'bg-green-50 text-green-600'}`}>
          {message}
        </p>
      )}

      {/* 동아리 정보 */}
      <div className="bg-white rounded-2xl border">
        <div className="flex items-center justify-between px-4 py-3 border-b">
          <h3 className="text-sm font-semibold text-slate-700">동아리 정보</h3>
          {!editing ? (
            <button
              onClick={() => setEditing(true)}
              className="px-3 py-1.5 text-xs font-medium rounded-lg bg-slate-100 text-slate-600 hover:bg-slate-200 cursor-pointer"
            >
              수정
            </button>
          ) : (
            <div className="flex gap-2">
              <button
                onClick={() => { setEditing(false); fetchSettings() }}
                className="px-3 py-1.5 text-xs rounded-lg bg-slate-100 text-slate-500 hover:bg-slate-200 cursor-pointer"
              >
                취소
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className="px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-white hover:bg-primary/90 cursor-pointer disabled:opacity-50"
              >
                {saving ? '저장 중...' : '저장'}
              </button>
            </div>
          )}
        </div>

        <div className="px-4 py-4 space-y-4">
          {/* 로고 */}
          <div>
            <label className="text-xs text-slate-400 block mb-2">동아리 로고</label>
            <div className="flex items-center gap-4">
              {displayLogoUrl && !logoImgError ? (
                <img
                  src={displayLogoUrl}
                  alt="동아리 로고"
                  className="w-16 h-16 rounded-xl object-cover border border-slate-200 shadow-sm"
                  onError={() => setLogoImgError(true)}
                />
              ) : (
                <div className="w-16 h-16 rounded-xl bg-primary/10 flex items-center justify-center border border-dashed border-slate-200">
                  <span className="text-2xl text-primary font-bold">F</span>
                </div>
              )}

              {editing && (
                <div className="flex flex-col gap-1.5">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleLogoUpload}
                    className="hidden"
                  />
                  <button
                    onClick={() => fileInputRef.current?.click()}
                    disabled={uploading}
                    className="px-3 py-1.5 text-xs font-medium rounded-lg bg-slate-100 text-slate-600 hover:bg-slate-200 cursor-pointer disabled:opacity-50"
                  >
                    {uploading ? '업로드 중...' : '이미지 선택'}
                  </button>
                  {form.clubLogoUrl && (
                    <button
                      onClick={() => setForm(f => ({ ...f, clubLogoUrl: '' }))}
                      className="px-3 py-1.5 text-xs rounded-lg text-red-400 hover:text-red-500 hover:bg-red-50 cursor-pointer"
                    >
                      로고 제거
                    </button>
                  )}
                  <span className="text-xs text-slate-300">JPG, PNG, WebP · 최대 10MB</span>
                </div>
              )}
            </div>
          </div>

          {/* 동아리 설명 */}
          <div>
            <label className="text-xs text-slate-400 block mb-1">동아리 설명</label>
            {editing ? (
              <textarea
                value={form.clubDescription}
                onChange={e => setForm(f => ({ ...f, clubDescription: e.target.value }))}
                className="w-full border rounded-lg px-3 py-2 text-sm resize-none"
                rows={3}
                placeholder="동아리 소개를 입력하세요"
              />
            ) : (
              <p className="text-sm text-slate-700">{settings?.clubDescription || '(미설정)'}</p>
            )}
          </div>
        </div>
      </div>

      {/* AI 댓글봇 */}
      <div className="bg-white rounded-2xl border">
        <div className="px-4 py-3 border-b">
          <h3 className="text-sm font-semibold text-slate-700">AI 댓글봇</h3>
          <p className="text-xs text-slate-400 mt-0.5">AI 페르소나가 자동으로 댓글과 스레드를 작성합니다</p>
        </div>
        <div className="px-4 py-4 space-y-4">
          {/* ON/OFF 토글 */}
          <div className="flex items-center justify-between">
            <span className="text-sm text-slate-700">활성화</span>
            <button
              onClick={() => updateBotSettings({ enabled: !botSettings.enabled, scope: botSettings.scope })}
              disabled={botSaving}
              className={`relative w-10 h-5 rounded-full transition-colors cursor-pointer ${
                botSettings.enabled ? 'bg-blue-500' : 'bg-slate-300'
              } ${botSaving ? 'opacity-50' : ''}`}
            >
              <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform shadow ${
                botSettings.enabled ? 'translate-x-5' : ''
              }`} />
            </button>
          </div>

          {/* 활동 범위 */}
          <div className={botSettings.enabled ? '' : 'opacity-40 pointer-events-none'}>
            <label className="text-xs text-slate-400 block mb-2">활동 범위</label>
            <div className="flex gap-3">
              {[
                { value: 'JONGTOBANG_ONLY', label: '종토방만' },
                { value: 'BOARD_ONLY', label: '게시판만' },
                { value: 'BOTH', label: '둘 다' },
              ].map(opt => (
                <label key={opt.value} className="flex items-center gap-1.5 cursor-pointer">
                  <input
                    type="radio"
                    name="botScope"
                    value={opt.value}
                    checked={botSettings.scope === opt.value}
                    onChange={() => updateBotSettings({ enabled: botSettings.enabled, scope: opt.value })}
                    className="w-3.5 h-3.5 text-blue-500"
                  />
                  <span className="text-sm text-slate-600">{opt.label}</span>
                </label>
              ))}
            </div>
          </div>

          {/* 빈도 안내 */}
          <p className="text-xs text-slate-400">빈도: 하루 5~10회 (자동)</p>
        </div>
      </div>

      {/* 주간 리뷰 봇 이름 */}
      <div className="bg-white rounded-2xl border">
        <div className="px-4 py-3 border-b">
          <h3 className="text-sm font-semibold text-slate-700">주간 리뷰 봇</h3>
          <p className="text-xs text-slate-400 mt-0.5">AI가 주 1회 재조명글을 작성할 때 사용할 발행 이름입니다</p>
        </div>
        <div className="px-4 py-4">
          {editing ? (
            <input
              type="text"
              value={form.weeklyReviewBotName || ''}
              onChange={e => setForm(f => ({ ...f, weeklyReviewBotName: e.target.value }))}
              placeholder="비우면 동아리명 사용"
              className="w-full border rounded-lg px-3 py-2 text-sm"
              maxLength={50}
            />
          ) : (
            <p className="text-sm text-slate-700">
              {settings?.weeklyReviewBotName || '(동아리명 사용)'}
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
