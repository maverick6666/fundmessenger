import { useState, useEffect, useRef } from 'react'
import { useOutletContext, useNavigate, Link } from 'react-router-dom'
import api from '../lib/api'
import { formatTime } from '../lib/format'
import Pagination from '../components/Pagination'
import MyAiHistoryPanel from '../components/MyAiHistoryPanel'
import HomeCalendarPanel from '../components/HomeCalendarPanel'
import DriveUploadModal from '../components/DriveUploadModal'
import useClubStore from '../store/clubStore'
import handleApiError from '../lib/handleApiError'
import TAG_COLORS from '../lib/tagColors'
import EmptyState from '../components/EmptyState'
import useToastStore from '../store/toastStore'

// 파일 확장자별 아이콘 스타일
const EXT_STYLES = {
  pdf: { bg: 'bg-red-100', text: 'text-red-500', label: 'PDF' },
  doc: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'DOC' },
  docx: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'DOC' },
  xls: { bg: 'bg-green-100', text: 'text-green-600', label: 'XLS' },
  xlsx: { bg: 'bg-green-100', text: 'text-green-600', label: 'XLS' },
  ppt: { bg: 'bg-orange-100', text: 'text-orange-600', label: 'PPT' },
  pptx: { bg: 'bg-orange-100', text: 'text-orange-600', label: 'PPT' },
  png: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'IMG' },
  jpg: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'IMG' },
  jpeg: { bg: 'bg-blue-100', text: 'text-blue-600', label: 'IMG' },
}
function getExtStyle(name) {
  const ext = name?.split('.').pop()?.toLowerCase() || ''
  return EXT_STYLES[ext] || { bg: 'bg-slate-100', text: 'text-slate-600', label: ext.toUpperCase().substring(0, 3) || '?' }
}


function isNew(createdAt) {
  return Date.now() - new Date(createdAt).getTime() < 24 * 60 * 60 * 1000
}

export default function MyPage() {
  const { clubInfo } = useOutletContext()
  const navigate = useNavigate()
  const { generations, fetchGenerations } = useClubStore()
  const [myGens, setMyGens] = useState([])
  const [genLoading, setGenLoading] = useState(false)
  const [genEditing, setGenEditing] = useState(false)

  // 프로필 수정 상태
  const [profileEditing, setProfileEditing] = useState(false)
  const [profile, setProfile] = useState({ bio: '', phone: '', avatarUrl: '' })
  const [profileForm, setProfileForm] = useState({ bio: '', phone: '' })
  const [avatarFile, setAvatarFile] = useState(null)
  const [avatarPreview, setAvatarPreview] = useState(null)
  const [profileSaving, setProfileSaving] = useState(false)

  const [genError, setGenError] = useState(false)

  useEffect(() => {
    if (!clubInfo?.clubId) return
    setGenError(false)
    fetchGenerations(clubInfo.clubId).catch(() => setGenError(true))
    if (clubInfo?.membershipId) {
      api.get(`/clubs/${clubInfo.clubId}/generations/members/${clubInfo.membershipId}`)
        .then(setMyGens).catch(() => setGenError(true))
    }
  }, [clubInfo?.clubId, clubInfo?.membershipId])

  // 프로필 로드
  useEffect(() => {
    api.get('/auth/me/profile')
      .then(data => {
        setProfile(data)
        setProfileForm({ bio: data.bio || '', phone: data.phone || '' })
      })
      .catch(e => handleApiError(e, '프로필 조회'))
  }, [])

  const handleProfileEdit = () => {
    setProfileEditing(true)
    setProfileForm({ bio: profile.bio || '', phone: profile.phone || '' })
    setAvatarFile(null)
    setAvatarPreview(null)
  }

  const handleProfileCancel = () => {
    setProfileEditing(false)
    setAvatarFile(null)
    setAvatarPreview(null)
  }

  const handleAvatarChange = (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    setAvatarFile(file)
    const reader = new FileReader()
    reader.onload = (ev) => setAvatarPreview(ev.target.result)
    reader.readAsDataURL(file)
  }

  const handleProfileSave = async () => {
    setProfileSaving(true)
    try {
      const formData = new FormData()
      formData.append('bio', profileForm.bio)
      formData.append('phone', profileForm.phone)
      if (avatarFile) formData.append('avatar', avatarFile)
      const data = await api.uploadPut('/auth/me/profile', formData)
      setProfile(data)
      setProfileEditing(false)
      useToastStore.getState().addToast({ type: 'SUCCESS', title: '프로필이 저장되었습니다' })
      setAvatarFile(null)
      setAvatarPreview(null)
    } catch (err) {
      handleApiError(err, '프로필 저장')
    } finally {
      setProfileSaving(false)
    }
  }

  const handleToggleGen = async (genId) => {
    if (!genEditing) return
    setGenLoading(true)
    try {
      const has = myGens.some(g => g.id === genId)
      if (has) {
        await api.delete(`/clubs/${clubInfo.clubId}/generations/members/${clubInfo.membershipId}/${genId}`)
        setMyGens(prev => prev.filter(g => g.id !== genId))
      } else {
        await api.post(`/clubs/${clubInfo.clubId}/generations/members/${clubInfo.membershipId}/${genId}`)
        const gen = generations.find(g => g.id === genId)
        if (gen) setMyGens(prev => [...prev, gen].sort((a, b) => a.displayOrder - b.displayOrder))
      }
    } catch (err) {
      handleApiError(err, '기수 변경')
    } finally {
      setGenLoading(false)
    }
  }

  // 팀 정보 문자열
  const myTeams = clubInfo?.myTeams || []
  const teamText = myTeams.length === 0
    ? '소속 팀 없음'
    : myTeams.length === 1
      ? myTeams[0].teamName
      : `${myTeams[0].teamName} 외 ${myTeams.length - 1}팀`

  // 태그 기반: SYSTEM 태그 중 첫 번째 이름 표시
  const myTags = clubInfo?.myTags || []
  const systemTag = myTags.find(t => t.tagType === 'SYSTEM')
  const positionText = systemTag?.name || '일반부원'

  return (
    <div className="space-y-5 animate-fade-in-up">
      {/* 브레드크럼 */}
      <div className="flex items-center gap-1 text-sm text-slate-400">
        <Link to="/club" className="hover:text-slate-600 transition-colors">홈</Link>
        <span>/</span>
        <span className="text-slate-600 font-medium">마이페이지</span>
      </div>

      {/* ① 프로필 카드 */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-md px-5 md:px-7 py-5 md:py-6">
        <div className="flex items-center gap-4">
          {/* 아바타 */}
          <div className="relative shrink-0">
            {(avatarPreview || profile.avatarUrl) ? (
              <img
                src={avatarPreview || profile.avatarUrl}
                alt="프로필"
                className="w-14 h-14 rounded-full object-cover"
              />
            ) : (
              <div className="w-14 h-14 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-600 text-xl font-bold">
                {clubInfo?.myName?.charAt(0) || '?'}
              </div>
            )}
            {profileEditing && (
              <label className="absolute -bottom-1 -right-1 w-6 h-6 bg-white border border-slate-200 rounded-full flex items-center justify-center cursor-pointer hover:bg-slate-50 transition-colors shadow-sm">
                <svg className="w-3 h-3 text-slate-500" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" /><circle cx="12" cy="13" r="3" /></svg>
                <input type="file" accept="image/*" className="hidden" onChange={handleAvatarChange} />
              </label>
            )}
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="text-lg md:text-lg font-bold text-slate-900">{clubInfo?.myName}</h1>
              {!profileEditing && (
                <button
                  onClick={handleProfileEdit}
                  className="text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
                  title="프로필 수정"
                >
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" /></svg>
                </button>
              )}
            </div>
            <p className="text-sm text-slate-400">
              {clubInfo?.schoolName} · {teamText} · {positionText}
            </p>
            {!profileEditing && profile.bio && (
              <p className="text-xs text-slate-500 mt-1">{profile.bio}</p>
            )}
            {!profileEditing && profile.phone && (
              <p className="text-xs text-slate-400 mt-0.5">{profile.phone}</p>
            )}
            <p className="text-xs text-slate-300 mt-0.5">
              내 글 {clubInfo?.myPostCount ?? 0} · 내 파일 {clubInfo?.myFileCount ?? 0}
            </p>
          </div>
        </div>

        {/* 프로필 편집 폼 */}
        {profileEditing && (
          <div className="mt-4 pt-4 border-t border-slate-100 space-y-3">
            <div>
              <label className="text-xs font-medium text-slate-500 block mb-1">한줄 소개</label>
              <input
                type="text"
                value={profileForm.bio}
                onChange={(e) => setProfileForm(prev => ({ ...prev, bio: e.target.value }))}
                maxLength={100}
                placeholder="자기소개를 입력하세요"
                className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-300"
              />
              <p className="text-xs text-slate-400 mt-0.5 text-right">{profileForm.bio.length}/100</p>
            </div>
            <div>
              <label className="text-xs font-medium text-slate-500 block mb-1">연락처</label>
              <input
                type="text"
                value={profileForm.phone}
                onChange={(e) => setProfileForm(prev => ({ ...prev, phone: e.target.value }))}
                maxLength={30}
                placeholder="010-0000-0000"
                className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-300"
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={handleProfileCancel}
                className="px-3 py-1.5 text-xs text-slate-500 hover:text-slate-700 transition-colors cursor-pointer"
              >
                취소
              </button>
              <button
                onClick={handleProfileSave}
                disabled={profileSaving}
                className="px-4 py-1.5 bg-primary text-white text-xs font-medium rounded-lg hover:bg-primary-hover transition-colors cursor-pointer disabled:opacity-50"
              >
                {profileSaving ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        )}

        {/* 기수 설정 */}
        {genError && (
          <div className="mt-4 pt-4 border-t border-slate-100">
            <p className="text-xs text-red-500">기수 정보를 불러오지 못했습니다</p>
          </div>
        )}
        {generations.length > 0 && (
          <div className="mt-4 pt-4 border-t border-slate-100">
            <div className="flex items-center gap-2 mb-2">
              <p className="text-xs font-medium text-slate-500">내 기수</p>
              <button
                onClick={() => setGenEditing(prev => !prev)}
                className="text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
                title={genEditing ? '수정 완료' : '기수 수정'}
              >
                {genEditing ? (
                  <svg className="w-3.5 h-3.5 text-blue-500" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" /></svg>
                ) : (
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" /></svg>
                )}
              </button>
            </div>
            <div className="flex gap-1.5 flex-wrap">
              {genEditing ? (
                // 편집 모드: 모든 기수 표시, 토글 가능
                generations.map(gen => {
                  const selected = myGens.some(g => g.id === gen.id)
                  return (
                    <button
                      key={gen.id}
                      onClick={() => handleToggleGen(gen.id)}
                      disabled={genLoading}
                      className={`text-xs px-3 py-1 rounded-full transition-colors cursor-pointer ${
                        selected
                          ? 'bg-primary text-white font-semibold'
                          : 'bg-slate-100 text-slate-500 font-medium hover:bg-slate-200'
                      } ${genLoading ? 'opacity-50 cursor-not-allowed' : ''}`}
                    >
                      {gen.name}
                      {selected && ' ✓'}
                    </button>
                  )
                })
              ) : (
                // 뷰 모드: 선택된 기수만 표시
                myGens.length > 0 ? (
                  myGens.map(gen => (
                    <span
                      key={gen.id}
                      className="text-xs px-3 py-1 rounded-full bg-primary text-white font-semibold"
                    >
                      {gen.name}
                    </span>
                  ))
                ) : (
                  <span className="text-xs text-slate-400">설정된 기수가 없습니다</span>
                )
              )}
            </div>
          </div>
        )}
      </div>

      {/* ② 캘린더 섹션 */}
      <HomeCalendarPanel
        clubId={clubInfo?.clubId}
        isExecutive={clubInfo?.isExecutive}
        teams={clubInfo?.myTeams?.map(t => ({ teamId: t.teamId, teamName: t.teamName })) || []}
        myTeams={clubInfo?.myTeams || []}
      />

      {/* ③ 콘텐츠 패널 */}
      <MyContentPanel
        clubId={clubInfo?.clubId}
        navigate={navigate}
      />
    </div>
  )
}



/* ─── 콘텐츠 패널 ─── */
function MyContentPanel({ clubId, navigate }) {
  const [tab, setTab] = useState('drive')
  const [tags, setTags] = useState([])
  const [tagId, setTagId] = useState('')

  // Drive
  const [files, setFiles] = useState([])
  const [filePage, setFilePage] = useState(0)
  const [fileTotalPages, setFileTotalPages] = useState(0)
  const [filesLoading, setFilesLoading] = useState(true)
  const [uploadFile, setUploadFile] = useState(null)
  const [isDragging, setIsDragging] = useState(false)
  const dragCounter = useRef(0)

  // Board
  const [posts, setPosts] = useState([])
  const [postPage, setPostPage] = useState(0)
  const [postTotalPages, setPostTotalPages] = useState(0)
  const [postsLoading, setPostsLoading] = useState(true)

  useEffect(() => {
    if (!clubId) return
    api.get(`/clubs/${clubId}/semi-categories`).then(setTags).catch(e => handleApiError(e, '카테고리 조회'))
  }, [clubId])

  useEffect(() => { setFilePage(0); setPostPage(0) }, [tagId, tab])

  // 내 파일
  useEffect(() => {
    if (!clubId || tab !== 'drive') return
    setFilesLoading(true)
    const params = new URLSearchParams()
    params.set('myOnly', 'true')
    if (tagId) params.set('tagId', tagId)
    params.set('page', String(filePage))
    params.set('size', '15')
    api.get(`/clubs/${clubId}/drive/files?${params}`)
      .then(data => { setFiles(data.content || []); setFileTotalPages(data.totalPages || 0) })
      .catch(e => handleApiError(e, '내 파일 조회'))
      .finally(() => setFilesLoading(false))
  }, [clubId, tagId, filePage, tab])

  // 내 글
  useEffect(() => {
    if (!clubId || tab !== 'board') return
    setPostsLoading(true)
    const postParams = new URLSearchParams()
    postParams.set('page', String(postPage))
    postParams.set('size', '15')
    if (tagId) postParams.set('tagId', tagId)
    api.get(`/clubs/${clubId}/posts/mine?${postParams}`)
      .then(data => { setPosts(data.content || []); setPostTotalPages(data.totalPages || 0) })
      .catch(e => handleApiError(e, '내 게시글 조회'))
      .finally(() => setPostsLoading(false))
  }, [clubId, postPage, tab])

  const handleUploadClick = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.onchange = (e) => {
      const file = e.target.files?.[0]
      if (file) setUploadFile(file)
    }
    input.click()
  }

  const handleDragEnter = (e) => { e.preventDefault(); dragCounter.current++; setIsDragging(true) }
  const handleDragLeave = (e) => { e.preventDefault(); dragCounter.current--; if (dragCounter.current === 0) setIsDragging(false) }
  const handleDragOver = (e) => e.preventDefault()
  const handleDrop = (e) => {
    e.preventDefault(); dragCounter.current = 0; setIsDragging(false)
    const file = e.dataTransfer?.files?.[0]
    if (file) setUploadFile(file)
  }

  const fetchFiles = () => {
    setFilesLoading(true)
    const params = new URLSearchParams()
    params.set('myOnly', 'true')
    if (tagId) params.set('tagId', tagId)
    params.set('page', String(filePage))
    params.set('size', '15')
    api.get(`/clubs/${clubId}/drive/files?${params}`)
      .then(data => { setFiles(data.content || []); setFileTotalPages(data.totalPages || 0) })
      .catch(e => handleApiError(e, '내 파일 조회'))
      .finally(() => setFilesLoading(false))
  }

  const handleAction = () => {
    if (tab === 'drive') {
      handleUploadClick()
      return
    }

    if (tab === 'board') {
      navigate('/club/boards')
    }
  }

  // 게시판 탭: 프론트 태그 필터링 (posts/mine에 tagId 미지원)
  const filteredPosts = tagId
    ? posts.filter(p => p.semiCategories?.some(sc => String(sc.id) === tagId))
    : posts

  return (
    <div
      className="bg-white rounded-2xl border border-slate-200 overflow-hidden relative"
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {isDragging && (
        <div className="absolute inset-0 z-10 bg-blue-50/80 border-2 border-dashed border-blue-400 rounded-2xl flex items-center justify-center pointer-events-none">
          <p className="text-blue-600 font-semibold text-sm">파일을 놓아주세요</p>
        </div>
      )}
      {/* 탭 바 */}
      <div className="flex items-center border-b border-slate-200">
        <button
          onClick={() => setTab('drive')}
          className={`px-5 py-3 text-sm transition-colors cursor-pointer ${
            tab === 'drive'
              ? 'font-semibold text-blue-500 border-b-2 border-blue-500'
              : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          드라이브
        </button>
        <button
          onClick={() => setTab('board')}
          className={`px-5 py-3 text-sm transition-colors cursor-pointer ${
            tab === 'board'
              ? 'font-semibold text-blue-500 border-b-2 border-blue-500'
              : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          게시판
        </button>
        <button
          onClick={() => setTab('ai')}
          className={`px-5 py-3 text-sm transition-colors cursor-pointer ${
            tab === 'ai'
              ? 'font-semibold text-blue-500 border-b-2 border-blue-500'
              : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          AI 생성
        </button>
        <button
          onClick={() => setTab('support')}
          className={`px-5 py-3 text-sm transition-colors cursor-pointer ${
            tab === 'support'
              ? 'font-semibold text-blue-500 border-b-2 border-blue-500'
              : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          문의
        </button>
        <div className="flex-1" />
        {tab !== 'ai' && tab !== 'support' && (
        <button
          onClick={handleAction}
          className="mr-3 px-3.5 py-1.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors cursor-pointer"
        >
          {tab === 'drive' ? '+ 업로드' : '+ 글쓰기'}
        </button>
        )}
      </div>

      {/* 콘텐츠 영역 */}
      <div className="px-5 md:px-6 py-5 space-y-5">
        {/* 태그 필터 */}
        {tab !== 'ai' && tab !== 'support' && (
        <div className="flex gap-1.5 flex-wrap">
          <button
            onClick={() => setTagId('')}
            className={`shrink-0 text-xs px-3 py-1 rounded-full transition-colors cursor-pointer ${
              !tagId ? 'bg-slate-900 text-white font-semibold' : 'bg-slate-100 text-slate-500 font-medium hover:bg-slate-200'
            }`}
          >
            전체
          </button>
          {tags.map(t => (
            <button
              key={t.id}
              onClick={() => setTagId(String(t.id))}
              className={`shrink-0 text-xs px-3 py-1 rounded-full transition-colors cursor-pointer ${
                String(t.id) === tagId ? 'bg-slate-900 text-white font-semibold' : 'bg-slate-100 text-slate-500 font-medium hover:bg-slate-200'
              }`}
            >
              {t.name}
            </button>
          ))}
        </div>
        )}

        {/* 드라이브 탭 */}
        {tab === 'drive' && (
          <>
            {filesLoading ? (
              <div className="py-6 flex justify-center">
                <div className="animate-spin w-4 h-4 border-2 border-slate-300 border-t-slate-600 rounded-full" />
              </div>
            ) : files.length === 0 ? (
              <EmptyState icon="upload" message="업로드한 파일이 없습니다" ctaLabel="드라이브로 이동 →" onCta={() => navigate('/club/drive')} />
            ) : (
              <div>
                {files.map(file => {
                  const ext = getExtStyle(file.originalName)
                  return (
                    <button
                      key={file.id}
                      onClick={() => navigate(`/club/drive?file=${file.id}`)}
                      className="w-full text-left flex items-center gap-3 py-2.5 border-b border-slate-100 last:border-b-0 hover:bg-slate-50 transition-colors cursor-pointer group"
                    >
                      <span className={`shrink-0 w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold ${ext.bg} ${ext.text}`}>
                        {ext.label}
                      </span>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-slate-900 truncate group-hover:text-blue-600 transition-colors">
                          {file.originalName}
                        </p>
                        <p className="text-xs text-slate-400 mt-0.5">
                          {formatTime(file.createdAt)}
                          {file.semiCategories?.[0]?.name && ` · ${file.semiCategories[0].name}`}
                        </p>
                      </div>
                    </button>
                  )
                })}
              </div>
            )}
            {fileTotalPages > 1 && (
              <Pagination page={filePage} totalPages={fileTotalPages} setPage={setFilePage} />
            )}
          </>
        )}

        {/* 게시판 탭 */}
        {tab === 'board' && (
          <>
            {postsLoading ? (
              <div className="py-6 flex justify-center">
                <div className="animate-spin w-4 h-4 border-2 border-slate-300 border-t-slate-600 rounded-full" />
              </div>
            ) : filteredPosts.length === 0 ? (
              <EmptyState icon="write" message="작성한 게시글이 없습니다" ctaLabel="게시판으로 이동 →" onCta={() => navigate('/club/boards')} />
            ) : (
              <div>
                {filteredPosts.map(post => (
                  <button
                    key={post.id}
                    onClick={() => navigate(`/club/boards?board=${post.boards?.[0]?.id}&post=${post.id}`)}
                    className="w-full text-left py-2.5 border-b border-slate-100 last:border-b-0 hover:bg-slate-50 transition-colors cursor-pointer group"
                  >
                    <div className="flex items-center gap-1.5 flex-wrap">
                      {post.semiCategories?.slice(0, 1).map((cat, i) => (
                        <span key={cat.id} className={`text-xs px-2 py-0.5 rounded ${TAG_COLORS[i % TAG_COLORS.length]} font-medium`}>
                          {cat.name}
                        </span>
                      ))}
                      <span className="text-sm font-medium text-slate-900 truncate group-hover:text-blue-600 transition-colors">
                        {post.title}
                      </span>
                      {isNew(post.createdAt) && (
                        <span className="text-xs px-1.5 py-0.5 bg-red-50 text-red-500 rounded font-bold shrink-0">N</span>
                      )}
                    </div>
                    <p className="text-xs text-slate-400 mt-1">
                      {formatTime(post.createdAt)}
                    </p>
                  </button>
                ))}
              </div>
            )}
            {postTotalPages > 1 && (
              <Pagination page={postPage} totalPages={postTotalPages} setPage={setPostPage} />
            )}
          </>
        )}

        {tab === 'ai' && (
          <MyAiHistoryPanel clubId={clubId} />
        )}

        {tab === 'support' && (
          <MySupportPanel />
        )}
      </div>

      {/* 업로드 모달 */}
      {uploadFile && (
        <DriveUploadModal
          file={uploadFile}
          onClose={() => setUploadFile(null)}
          onUploaded={() => fetchFiles()}
        />
      )}
    </div>
  )
}


/* ─── 문의 패널 ─── */
const CATEGORY_LABELS = {
  GENERAL: '일반 문의',
  SUBSCRIPTION: '구독 관련',
  CREDIT: '크레딧 관련',
  BUG_REPORT: '버그 신고',
  OTHER: '기타',
}

const STATUS_STYLES = {
  OPEN: { bg: 'bg-yellow-100', text: 'text-yellow-700', label: '답변 대기' },
  ANSWERED: { bg: 'bg-green-100', text: 'text-green-700', label: '답변 완료' },
  CLOSED: { bg: 'bg-slate-100', text: 'text-slate-500', label: '종료' },
}

function MySupportPanel() {
  const [tickets, setTickets] = useState([])
  const [loading, setLoading] = useState(true)
  const [detail, setDetail] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ title: '', content: '', category: 'GENERAL' })
  const [submitting, setSubmitting] = useState(false)
  const [replyContent, setReplyContent] = useState('')
  const [replying, setReplying] = useState(false)
  const addToast = useToastStore(s => s.addToast)

  const fetchTickets = () => {
    setLoading(true)
    api.get('/support/tickets')
      .then(setTickets)
      .catch(e => handleApiError(e, '문의 목록 조회'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { fetchTickets() }, [])

  const handleCreate = async () => {
    if (!form.title.trim() || !form.content.trim()) return
    setSubmitting(true)
    try {
      await api.post('/support/tickets', form)
      addToast({ type: 'SUCCESS', title: '문의가 등록되었습니다' })
      setShowForm(false)
      setForm({ title: '', content: '', category: 'GENERAL' })
      fetchTickets()
    } catch (e) {
      handleApiError(e, '문의 등록')
    } finally {
      setSubmitting(false)
    }
  }

  const openDetail = async (id) => {
    try {
      const data = await api.get(`/support/tickets/${id}`)
      setDetail(data)
    } catch (e) {
      handleApiError(e, '문의 상세 조회')
    }
  }

  const handleReply = async () => {
    if (!replyContent.trim() || !detail) return
    setReplying(true)
    try {
      await api.post(`/support/tickets/${detail.id}/reply`, { content: replyContent.trim() })
      setReplyContent('')
      openDetail(detail.id)
      addToast({ type: 'SUCCESS', title: '답변이 등록되었습니다' })
    } catch (e) {
      handleApiError(e, '답변 등록')
    } finally {
      setReplying(false)
    }
  }

  // 상세 보기
  if (detail) {
    const st = STATUS_STYLES[detail.status] || STATUS_STYLES.OPEN
    return (
      <div className="space-y-4">
        <button
          onClick={() => setDetail(null)}
          className="text-sm text-slate-400 hover:text-slate-600 transition-colors cursor-pointer flex items-center gap-1"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" /></svg>
          목록으로
        </button>

        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className={`text-xs px-2 py-0.5 rounded font-medium ${st.bg} ${st.text}`}>{st.label}</span>
            <span className="text-xs text-slate-400">{CATEGORY_LABELS[detail.category] || detail.category}</span>
          </div>
          <h3 className="text-base font-bold text-slate-900">{detail.title}</h3>
          <p className="text-xs text-slate-400 mt-1">{formatTime(detail.createdAt)}</p>
        </div>

        <div className="bg-slate-50 rounded-xl px-4 py-3">
          <p className="text-sm text-slate-700 whitespace-pre-wrap">{detail.content}</p>
        </div>

        {/* 답변 목록 */}
        {detail.replies?.length > 0 && (
          <div className="space-y-3">
            <p className="text-xs font-semibold text-slate-500">답변 {detail.replies.length}개</p>
            {detail.replies.map(reply => (
              <div
                key={reply.id}
                className={`rounded-xl px-4 py-3 ${reply.adminReply ? 'bg-blue-50 border border-blue-100' : 'bg-slate-50'}`}
              >
                <div className="flex items-center gap-2 mb-1">
                  <span className={`text-xs font-semibold ${reply.adminReply ? 'text-blue-600' : 'text-slate-600'}`}>
                    {reply.adminReply ? '관리자' : '나'}
                  </span>
                  <span className="text-xs text-slate-400">{formatTime(reply.createdAt)}</span>
                </div>
                <p className="text-sm text-slate-700 whitespace-pre-wrap">{reply.content}</p>
              </div>
            ))}
          </div>
        )}

        {/* 추가 답변 입력 */}
        {detail.status !== 'CLOSED' && (
          <div className="space-y-2">
            <textarea
              value={replyContent}
              onChange={e => setReplyContent(e.target.value)}
              placeholder="추가 내용을 입력하세요..."
              rows={3}
              className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-300 resize-none"
            />
            <div className="flex justify-end">
              <button
                onClick={handleReply}
                disabled={replying || !replyContent.trim()}
                className="px-4 py-1.5 bg-primary text-white text-xs font-medium rounded-lg hover:bg-primary-hover transition-colors cursor-pointer disabled:opacity-50"
              >
                {replying ? '전송 중...' : '답변 보내기'}
              </button>
            </div>
          </div>
        )}
      </div>
    )
  }

  // 작성 폼
  if (showForm) {
    return (
      <div className="space-y-4">
        <button
          onClick={() => setShowForm(false)}
          className="text-sm text-slate-400 hover:text-slate-600 transition-colors cursor-pointer flex items-center gap-1"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" /></svg>
          취소
        </button>

        <div>
          <label className="text-xs font-medium text-slate-500 block mb-1">카테고리</label>
          <select
            value={form.category}
            onChange={e => setForm(prev => ({ ...prev, category: e.target.value }))}
            className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-300"
          >
            {Object.entries(CATEGORY_LABELS).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="text-xs font-medium text-slate-500 block mb-1">제목</label>
          <input
            type="text"
            value={form.title}
            onChange={e => setForm(prev => ({ ...prev, title: e.target.value }))}
            maxLength={200}
            placeholder="문의 제목을 입력하세요"
            className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-300"
          />
        </div>

        <div>
          <label className="text-xs font-medium text-slate-500 block mb-1">내용</label>
          <textarea
            value={form.content}
            onChange={e => setForm(prev => ({ ...prev, content: e.target.value }))}
            placeholder="문의 내용을 자세히 입력해주세요"
            rows={5}
            className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-300 resize-none"
          />
        </div>

        <div className="flex justify-end">
          <button
            onClick={handleCreate}
            disabled={submitting || !form.title.trim() || !form.content.trim()}
            className="px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors cursor-pointer disabled:opacity-50"
          >
            {submitting ? '등록 중...' : '문의 등록'}
          </button>
        </div>
      </div>
    )
  }

  // 목록
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs text-slate-400">총 {tickets.length}건</p>
        <button
          onClick={() => setShowForm(true)}
          className="px-3.5 py-1.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors cursor-pointer"
        >
          + 문의하기
        </button>
      </div>

      {loading ? (
        <div className="py-6 flex justify-center">
          <div className="animate-spin w-4 h-4 border-2 border-slate-300 border-t-slate-600 rounded-full" />
        </div>
      ) : tickets.length === 0 ? (
        <EmptyState icon="chat" message="문의 내역이 없습니다" />
      ) : (
        <div>
          {tickets.map(ticket => {
            const st = STATUS_STYLES[ticket.status] || STATUS_STYLES.OPEN
            return (
              <button
                key={ticket.id}
                onClick={() => openDetail(ticket.id)}
                className="w-full text-left py-3 border-b border-slate-100 last:border-b-0 hover:bg-slate-50 transition-colors cursor-pointer group"
              >
                <div className="flex items-center gap-2">
                  <span className={`shrink-0 text-xs px-2 py-0.5 rounded font-medium ${st.bg} ${st.text}`}>{st.label}</span>
                  <span className="text-sm font-medium text-slate-900 truncate group-hover:text-blue-600 transition-colors">
                    {ticket.title}
                  </span>
                </div>
                <p className="text-xs text-slate-400 mt-1">
                  {CATEGORY_LABELS[ticket.category] || ticket.category} · {formatTime(ticket.createdAt)}
                </p>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
