import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuthStore from '../store/authStore'
import api from '../lib/api'
import ConfirmModal from '../components/ConfirmModal'
import handleApiError from '../lib/handleApiError'
import useToastStore from '../store/toastStore'

const SUB_STATUS = {
  INACTIVE: { label: '미활성', color: 'bg-slate-100 text-slate-500' },
  ACTIVE: { label: '활성', color: 'bg-green-100 text-green-700' },
  EXPIRED: { label: '만료', color: 'bg-red-100 text-red-600' },
}

export default function AdminPage() {
  const navigate = useNavigate()
  const logout = useAuthStore(s => s.logout)

  // 탭: 'dashboard' | 'schools' | 'pending' | 'support' | 'modelData' | 'settings'
  const [tab, setTab] = useState('dashboard')

  // 학교/동아리 계층
  const [schools, setSchools] = useState([])
  const [selectedSchool, setSelectedSchool] = useState(null)
  const [clubs, setClubs] = useState([])
  const [selectedClub, setSelectedClub] = useState(null)

  // 가입 신청
  const [memberships, setMemberships] = useState([])
  const [actionLoading, setActionLoading] = useState(null)

  // 로딩
  const [schoolsLoading, setSchoolsLoading] = useState(true)
  const [clubsLoading, setClubsLoading] = useState(false)
  const [membershipsLoading, setMembershipsLoading] = useState(false)

  // 추가 폼
  const [newSchoolName, setNewSchoolName] = useState('')
  const [newClubName, setNewClubName] = useState('')
  const [addingSchool, setAddingSchool] = useState(false)
  const [addingClub, setAddingClub] = useState(false)

  // 알림 모달
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  // 구독 관리
  const [subLoading, setSubLoading] = useState(false)
  const [subForm, setSubForm] = useState({ startDate: '', endDate: '' })

  // 확인 모달
  const [confirmModal, setConfirmModal] = useState(null)

  const showConfirm = useCallback(({ title, message, danger, confirmLabel }) => {
    return new Promise(resolve => {
      setConfirmModal({ title, message, danger, confirmLabel, onConfirm: () => resolve(true), onClose: () => resolve(false) })
    })
  }, [])

  // ========== 데이터 로드 ==========

  const fetchSchools = async () => {
    setSchoolsLoading(true)
    try {
      const data = await api.get('/admin/schools')
      setSchools(data)
    } catch (err) {
      handleApiError(err, '학교 목록 조회')
    } finally {
      setSchoolsLoading(false)
    }
  }

  const fetchClubs = async (schoolId) => {
    setClubsLoading(true)
    setSelectedClub(null)
    setMemberships([])
    try {
      const data = await api.get(`/admin/schools/${schoolId}/clubs`)
      setClubs(data)
    } catch (err) {
      handleApiError(err, '동아리 목록 조회')
    } finally {
      setClubsLoading(false)
    }
  }

  const fetchPendingByClub = async (clubId) => {
    setMembershipsLoading(true)
    try {
      const data = await api.get(`/admin/clubs/${clubId}/pending-memberships`)
      setMemberships(data)
    } catch (err) {
      handleApiError(err, '대기 목록 조회')
    } finally {
      setMembershipsLoading(false)
    }
  }

  const fetchAllPending = async () => {
    setMembershipsLoading(true)
    try {
      const data = await api.get('/admin/pending-memberships')
      setMemberships(data)
    } catch (err) {
      handleApiError(err, '대기 목록 조회')
    } finally {
      setMembershipsLoading(false)
    }
  }

  useEffect(() => { fetchSchools() }, [])

  // ========== 학교/동아리 선택 ==========

  const handleSelectSchool = (school) => {
    setSelectedSchool(school)
    fetchClubs(school.id)
  }

  const handleSelectClub = (club) => {
    setSelectedClub(club)
    // 구독 폼 초기값 설정
    setSubForm({
      startDate: club.subscriptionStartDate || todayStr(),
      endDate: club.subscriptionEndDate || sixMonthsLater(),
    })
    fetchPendingByClub(club.id)
  }

  // ========== 학교/동아리 추가 ==========

  const handleAddSchool = async () => {
    if (!newSchoolName.trim()) return
    setAddingSchool(true)
    try {
      const created = await api.post('/admin/schools', { name: newSchoolName.trim() })
      setSchools(prev => [...prev, created])
      setNewSchoolName('')
    } catch (err) {
      setAlertModal({ open: true, message: '학교 등록 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setAddingSchool(false)
    }
  }

  const handleAddClub = async () => {
    if (!newClubName.trim() || !selectedSchool) return
    setAddingClub(true)
    try {
      const created = await api.post(`/admin/schools/${selectedSchool.id}/clubs`, { name: newClubName.trim() })
      setClubs(prev => [...prev, created])
      setNewClubName('')
      setSchools(prev => prev.map(s =>
        s.id === selectedSchool.id ? { ...s, clubCount: s.clubCount + 1 } : s
      ))
    } catch (err) {
      setAlertModal({ open: true, message: '동아리 등록 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setAddingClub(false)
    }
  }

  // ========== 구독 관리 ==========

  const handleActivateSubscription = async () => {
    if (!selectedClub || !subForm.startDate || !subForm.endDate) return
    setSubLoading(true)
    try {
      const result = await api.post(`/admin/clubs/${selectedClub.id}/subscription/activate`, {
        startDate: subForm.startDate,
        endDate: subForm.endDate,
      })
      updateClubSubscription(result)
    } catch (err) {
      setAlertModal({ open: true, message: '구독 활성화 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setSubLoading(false)
    }
  }

  const handleExtendSubscription = async () => {
    if (!selectedClub || !subForm.endDate) return
    setSubLoading(true)
    try {
      const result = await api.post(`/admin/clubs/${selectedClub.id}/subscription/extend`, {
        endDate: subForm.endDate,
      })
      updateClubSubscription(result)
    } catch (err) {
      setAlertModal({ open: true, message: '구독 연장 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setSubLoading(false)
    }
  }

  const handleDeactivateSubscription = async () => {
    if (!selectedClub) return
    const ok = await showConfirm({ title: '구독 해지', message: `"${selectedClub.name}" 동아리의 구독을 해지하시겠습니까?`, danger: true, confirmLabel: '해지' })
    if (!ok) return
    setSubLoading(true)
    try {
      const result = await api.post(`/admin/clubs/${selectedClub.id}/subscription/deactivate`)
      updateClubSubscription(result)
      setSubForm({ startDate: todayStr(), endDate: sixMonthsLater() })
    } catch (err) {
      setAlertModal({ open: true, message: '구독 해지 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setSubLoading(false)
    }
  }

  const updateClubSubscription = (result) => {
    const updated = {
      ...selectedClub,
      subscriptionStatus: result.subscriptionStatus,
      subscriptionStartDate: result.subscriptionStartDate,
      subscriptionEndDate: result.subscriptionEndDate,
    }
    setSelectedClub(updated)
    setClubs(prev => prev.map(c => c.id === updated.id ? { ...c, ...updated } : c))
  }

  // ========== 승인/거절 ==========

  const handleApprove = async (membershipId) => {
    setActionLoading(membershipId)
    try {
      await api.post(`/admin/memberships/${membershipId}/approve`)
      setMemberships(prev => prev.filter(m => m.id !== membershipId))
      if (selectedClub) {
        setClubs(prev => prev.map(c =>
          c.id === selectedClub.id
            ? { ...c, pendingCount: c.pendingCount - 1, totalMembers: c.totalMembers + 1 }
            : c
        ))
      }
    } catch (err) {
      setAlertModal({ open: true, message: '승인 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setActionLoading(null)
    }
  }

  const handleReject = async (membershipId) => {
    const ok = await showConfirm({ title: '가입 거절', message: '정말 거절하시겠습니까? 이 작업은 되돌릴 수 없습니다.', danger: true, confirmLabel: '거절' })
    if (!ok) return
    setActionLoading(membershipId)
    try {
      await api.post(`/admin/memberships/${membershipId}/reject`)
      setMemberships(prev => prev.filter(m => m.id !== membershipId))
      if (selectedClub) {
        setClubs(prev => prev.map(c =>
          c.id === selectedClub.id ? { ...c, pendingCount: c.pendingCount - 1 } : c
        ))
      }
    } catch (err) {
      setAlertModal({ open: true, message: '거절 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setActionLoading(null)
    }
  }

  const formatDate = (dateStr) => {
    if (!dateStr) return '-'
    const d = new Date(dateStr)
    return d.toLocaleDateString('ko-KR', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit'
    })
  }

  return (
    <div className="min-h-screen bg-[#FAF8F5]">
      {confirmModal && (
        <ConfirmModal
          title={confirmModal.title}
          message={confirmModal.message}
          danger={confirmModal.danger}
          confirmLabel={confirmModal.confirmLabel}
          onConfirm={() => { confirmModal.onConfirm(); setConfirmModal(null) }}
          onClose={() => { confirmModal.onClose(); setConfirmModal(null) }}
        />
      )}
      {alertModal.open && (
        <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
      {/* 헤더 */}
      <header className="bg-white border-b border-slate-100">
        <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-primary rounded-xl flex items-center justify-center">
              <span className="text-white text-sm font-bold">F</span>
            </div>
            <div>
              <h1 className="text-lg font-bold text-slate-900">슈퍼어드민</h1>
              <p className="text-xs text-slate-500">펀드메신저 관리</p>
            </div>
          </div>
          <button
            onClick={() => { logout(); navigate('/login', { replace: true }) }}
            className="text-sm text-slate-500 hover:text-slate-700 transition-colors cursor-pointer"
          >
            로그아웃
          </button>
        </div>
      </header>

      {/* 탭 */}
      <div className="bg-white border-b border-slate-100">
        <div className="max-w-6xl mx-auto px-4 flex gap-1">
          {[
            { key: 'dashboard', label: '대시보드', onClick: () => setTab('dashboard') },
            { key: 'schools', label: '대학/동아리 관리', onClick: () => { setTab('schools'); setSelectedClub(null); setMemberships([]) } },
            { key: 'pending', label: '전체 승인 대기', onClick: () => { setTab('pending'); fetchAllPending() } },
            { key: 'support', label: '문의 관리', onClick: () => setTab('support') },
            { key: 'modelData', label: '모델 데이터', onClick: () => setTab('modelData') },
            { key: 'stickers', label: '스티커', onClick: () => setTab('stickers') },
            { key: 'settings', label: '설정', onClick: () => setTab('settings') },
          ].map(t => (
            <button
              key={t.key}
              onClick={t.onClick}
              className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors cursor-pointer ${
                tab === t.key
                  ? 'border-primary text-primary'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <main className="max-w-6xl mx-auto px-4 py-6">
        {tab === 'dashboard' ? (
          <DashboardPanel />
        ) : tab === 'settings' ? (
          <GlobalSettingsPanel />
        ) : tab === 'support' ? (
          <SupportPanel />
        ) : tab === 'modelData' ? (
          <ModelDataPanel showConfirm={showConfirm} setAlertModal={setAlertModal} />
        ) : tab === 'stickers' ? (
          <StickerManagementPanel />
        ) : tab === 'schools' ? (
          /* ========== 대학/동아리 관리 탭 ========== */
          <div className="flex gap-6">
            {/* 좌측: 학교 목록 */}
            <div className="w-64 shrink-0">
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-sm font-bold text-slate-900">대학교</h2>
                <span className="text-xs text-slate-400">{schools.length}개</span>
              </div>

              {/* 학교 추가 */}
              <div className="flex gap-1.5 mb-3">
                <input
                  value={newSchoolName}
                  onChange={(e) => setNewSchoolName(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleAddSchool()}
                  placeholder="새 대학교명"
                  className="flex-1 min-w-0 px-3 py-2 text-sm bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                />
                <button
                  onClick={handleAddSchool}
                  disabled={addingSchool || !newSchoolName.trim()}
                  className="px-3 py-2 text-sm text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50 shrink-0"
                >
                  +
                </button>
              </div>

              {/* 학교 리스트 */}
              {schoolsLoading ? (
                <div className="py-8 text-center">
                  <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full mx-auto" />
                </div>
              ) : (
                <div className="space-y-1">
                  {schools.map(school => (
                    <button
                      key={school.id}
                      onClick={() => handleSelectSchool(school)}
                      className={`w-full text-left px-3 py-2.5 rounded-lg text-sm transition-all cursor-pointer ${
                        selectedSchool?.id === school.id
                          ? 'bg-primary/10 text-primary font-medium'
                          : 'text-slate-700 hover:bg-slate-100'
                      }`}
                    >
                      <div className="font-medium">{school.name}</div>
                      <div className="text-xs text-slate-400 mt-0.5">동아리 {school.clubCount}개</div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* 우측: 선택된 학교의 동아리 + 구독/가입 관리 */}
            <div className="flex-1 min-w-0">
              {!selectedSchool ? (
                <div className="bg-white rounded-2xl shadow-sm border border-slate-200 shadow-sm p-12 text-center">
                  <p className="text-slate-400 text-sm">좌측에서 대학교를 선택하세요</p>
                </div>
              ) : (
                <>
                  {/* 동아리 목록 */}
                  <div className="mb-6">
                    <div className="flex items-center justify-between mb-3">
                      <h2 className="text-sm font-bold text-slate-900">
                        {selectedSchool.name} — 동아리
                      </h2>
                    </div>

                    {/* 동아리 추가 */}
                    <div className="flex gap-1.5 mb-3">
                      <input
                        value={newClubName}
                        onChange={(e) => setNewClubName(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleAddClub()}
                        placeholder="새 동아리명"
                        className="flex-1 min-w-0 px-3 py-2 text-sm bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                      />
                      <button
                        onClick={handleAddClub}
                        disabled={addingClub || !newClubName.trim()}
                        className="px-3 py-2 text-sm text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50 shrink-0"
                      >
                        추가
                      </button>
                    </div>

                    {clubsLoading ? (
                      <div className="py-4 text-center">
                        <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full mx-auto" />
                      </div>
                    ) : clubs.length === 0 ? (
                      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 text-center">
                        <p className="text-sm text-slate-400">등록된 동아리가 없습니다</p>
                      </div>
                    ) : (
                      <div className="grid grid-cols-2 gap-2">
                        {clubs.map(club => {
                          const sub = SUB_STATUS[club.subscriptionStatus] || SUB_STATUS.INACTIVE
                          return (
                            <button
                              key={club.id}
                              onClick={() => handleSelectClub(club)}
                              className={`text-left px-4 py-3 rounded-xl border transition-all cursor-pointer ${
                                selectedClub?.id === club.id
                                  ? 'bg-primary/5 border-primary'
                                  : 'bg-white border-slate-100 hover:border-slate-200'
                              }`}
                            >
                              <div className="flex items-center gap-2">
                                <span className="font-medium text-sm text-slate-900">{club.name}</span>
                                <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${sub.color}`}>
                                  {sub.label}
                                </span>
                              </div>
                              <div className="flex gap-3 mt-1">
                                <span className="text-xs text-slate-400">멤버 {club.totalMembers}</span>
                                {club.pendingCount > 0 && (
                                  <span className="text-xs text-amber-600 font-medium">
                                    대기 {club.pendingCount}
                                  </span>
                                )}
                                <span className="text-xs text-slate-400">AI 사용 {club.aiUsageCount ?? 0}회</span>
                              </div>
                              {club.subscriptionStatus === 'ACTIVE' && club.subscriptionEndDate && (
                                <div className="text-xs text-slate-400 mt-1">
                                  ~{club.subscriptionEndDate} 까지
                                </div>
                              )}
                            </button>
                          )
                        })}
                      </div>
                    )}
                  </div>

                  {/* 선택된 동아리 상세 */}
                  {selectedClub && (
                    <>
                      {/* 구독 관리 */}
                      <SubscriptionPanel
                        club={selectedClub}
                        subForm={subForm}
                        setSubForm={setSubForm}
                        subLoading={subLoading}
                        onActivate={handleActivateSubscription}
                        onExtend={handleExtendSubscription}
                        onDeactivate={handleDeactivateSubscription}
                      />

                      {/* 소속 멤버 + 역할 관리 */}
                      <ClubMembersPanel club={selectedClub} />

                      {/* 가입 신청 */}
                      <div className="mt-6">
                        <div className="flex items-center justify-between mb-3">
                          <h2 className="text-sm font-bold text-slate-900">
                            {selectedClub.name} — 가입 승인 대기
                          </h2>
                          <button
                            onClick={() => fetchPendingByClub(selectedClub.id)}
                            className="text-xs text-primary hover:text-primary/80 cursor-pointer"
                          >
                            새로고침
                          </button>
                        </div>
                        <MembershipList
                          loading={membershipsLoading}
                          memberships={memberships}
                          actionLoading={actionLoading}
                          onApprove={handleApprove}
                          onReject={handleReject}
                          formatDate={formatDate}
                        />
                      </div>
                    </>
                  )}
                </>
              )}
            </div>
          </div>
        ) : (
          /* ========== 전체 승인 대기 탭 ========== */
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold text-slate-900">전체 승인 대기</h2>
              <button
                onClick={fetchAllPending}
                className="text-sm text-primary hover:text-primary/80 cursor-pointer"
              >
                새로고침
              </button>
            </div>
            <MembershipList
              loading={membershipsLoading}
              memberships={memberships}
              actionLoading={actionLoading}
              onApprove={handleApprove}
              onReject={handleReject}
              formatDate={formatDate}
            />
          </div>
        )}
      </main>
    </div>
  )
}

// ========== 유틸 ==========

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}

function sixMonthsLater() {
  const d = new Date()
  d.setMonth(d.getMonth() + 6)
  return d.toISOString().slice(0, 10)
}

function daysUntil(dateStr) {
  if (!dateStr) return null
  const diff = Math.ceil((new Date(dateStr) - new Date()) / (1000 * 60 * 60 * 24))
  return diff
}

// ========== 구독 관리 패널 ==========

function SubscriptionPanel({ club, subForm, setSubForm, subLoading, onActivate, onExtend, onDeactivate }) {
  const status = club.subscriptionStatus
  const sub = SUB_STATUS[status] || SUB_STATUS.INACTIVE
  const remaining = daysUntil(club.subscriptionEndDate)

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-bold text-slate-900">구독 관리</h3>
        <span className={`text-xs px-2 py-1 rounded-full font-medium ${sub.color}`}>
          {sub.label}
        </span>
      </div>

      {/* 현재 구독 정보 */}
      {status !== 'INACTIVE' && (
        <div className="mb-4 px-3 py-2.5 bg-slate-50 rounded-lg">
          <div className="flex gap-4 text-xs text-slate-600">
            <span>시작일 <strong>{club.subscriptionStartDate || '-'}</strong></span>
            <span>종료일 <strong>{club.subscriptionEndDate || '-'}</strong></span>
            {status === 'ACTIVE' && remaining !== null && (
              <span className={remaining <= 30 ? 'text-amber-600 font-medium' : ''}>
                {remaining > 0 ? `D-${remaining}` : '오늘 만료'}
              </span>
            )}
            {status === 'EXPIRED' && (
              <span className="text-red-500 font-medium">만료됨</span>
            )}
          </div>
        </div>
      )}

      {/* 날짜 입력 */}
      <div className="flex gap-3 mb-3">
        {status === 'INACTIVE' && (
          <div className="flex-1">
            <label className="block text-xs text-slate-500 mb-1">시작일</label>
            <input
              type="date"
              value={subForm.startDate}
              onChange={(e) => setSubForm(prev => ({ ...prev, startDate: e.target.value }))}
              className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
          </div>
        )}
        <div className="flex-1">
          <label className="block text-xs text-slate-500 mb-1">
            {status === 'INACTIVE' ? '종료일' : '새 종료일'}
          </label>
          <input
            type="date"
            value={subForm.endDate}
            onChange={(e) => setSubForm(prev => ({ ...prev, endDate: e.target.value }))}
            className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
          />
        </div>
      </div>

      {/* 빠른 기간 선택 */}
      <div className="flex gap-2 mb-4">
        <QuickDateButton label="6개월" months={6} subForm={subForm} setSubForm={setSubForm} status={status} />
        <QuickDateButton label="1년" months={12} subForm={subForm} setSubForm={setSubForm} status={status} />
      </div>

      {/* 액션 버튼 */}
      <div className="flex gap-2">
        {status === 'INACTIVE' && (
          <button
            onClick={onActivate}
            disabled={subLoading || !subForm.startDate || !subForm.endDate}
            className="flex-1 py-2 text-sm font-medium text-white bg-green-600 rounded-lg hover:bg-green-700 transition-colors cursor-pointer disabled:opacity-50"
          >
            {subLoading ? '처리 중...' : '구독 활성화'}
          </button>
        )}
        {(status === 'ACTIVE' || status === 'EXPIRED') && (
          <>
            <button
              onClick={onExtend}
              disabled={subLoading || !subForm.endDate}
              className="flex-1 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-primary-hover transition-colors cursor-pointer disabled:opacity-50"
            >
              {subLoading ? '처리 중...' : status === 'EXPIRED' ? '구독 재활성화' : '구독 연장'}
            </button>
            <button
              onClick={onDeactivate}
              disabled={subLoading}
              className="px-4 py-2 text-sm font-medium text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors cursor-pointer disabled:opacity-50"
            >
              해지
            </button>
          </>
        )}
      </div>
    </div>
  )
}

function QuickDateButton({ label, months, subForm, setSubForm, status }) {
  const handleClick = () => {
    const base = status === 'INACTIVE'
      ? new Date(subForm.startDate || Date.now())
      : new Date() // 연장 시 오늘부터
    base.setMonth(base.getMonth() + months)
    setSubForm(prev => ({ ...prev, endDate: base.toISOString().slice(0, 10) }))
  }

  return (
    <button
      onClick={handleClick}
      className="px-3 py-1.5 text-xs text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors cursor-pointer"
    >
      {label}
    </button>
  )
}

// ========== 크레딧 관리 패널 ==========

function CreditPanel({ club, creditAmount, setCreditAmount, creditDesc, setCreditDesc, creditLoading, creditHistory, creditHistoryLoading, onGrant, onRevoke }) {
  const typeLabel = { GRANT: '지급', REVOKE: '차감', ALLOCATE: '분배', RECLAIM: '회수', DEDUCT: '소비' }
  const typeColor = { GRANT: 'text-green-600', REVOKE: 'text-red-600', ALLOCATE: 'text-blue-600', RECLAIM: 'text-orange-500', DEDUCT: 'text-red-500' }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 mt-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-bold text-slate-900">크레딧 관리</h3>
        <span className="text-sm font-semibold text-primary">{club.creditBalance ?? 0} 크레딧</span>
      </div>

      {/* 지급/차감 폼 */}
      <div className="flex gap-2 mb-3">
        <input
          type="number"
          min="1"
          value={creditAmount}
          onChange={(e) => setCreditAmount(e.target.value)}
          placeholder="수량"
          className="w-28 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
        />
        <input
          value={creditDesc}
          onChange={(e) => setCreditDesc(e.target.value)}
          placeholder="사유 (선택)"
          className="flex-1 min-w-0 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
        />
        <button
          onClick={onGrant}
          disabled={creditLoading || !creditAmount}
          className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50 shrink-0"
        >
          {creditLoading ? '...' : '지급'}
        </button>
        <button
          onClick={onRevoke}
          disabled={creditLoading || !creditAmount}
          className="px-4 py-2 text-sm font-medium text-white bg-red-500 rounded-lg hover:bg-red-600 transition-colors cursor-pointer disabled:opacity-50 shrink-0"
        >
          {creditLoading ? '...' : '차감'}
        </button>
      </div>

      {/* 빠른 수량 버튼 */}
      <div className="flex gap-2 mb-4">
        {[100, 500, 1000].map(v => (
          <button
            key={v}
            onClick={() => setCreditAmount(String(v))}
            className="px-3 py-1.5 text-xs text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors cursor-pointer"
          >
            {v}
          </button>
        ))}
      </div>

      {/* 내역 */}
      <div>
        <h4 className="text-xs font-medium text-slate-500 mb-2">크레딧 내역</h4>
        {creditHistoryLoading ? (
          <div className="py-4 text-center">
            <div className="animate-spin w-5 h-5 border-2 border-primary border-t-transparent rounded-full mx-auto" />
          </div>
        ) : creditHistory.length === 0 ? (
          <p className="text-xs text-slate-400 text-center py-3">내역이 없습니다</p>
        ) : (
          <div className="space-y-1">
            {creditHistory.map(h => (
              <div key={h.id} className="flex items-center justify-between px-3 py-2 bg-slate-50 rounded-lg text-xs">
                <div className="flex items-center gap-2">
                  <span className={`${typeColor[h.type] || 'text-slate-600'} font-medium`}>
                    {h.type === 'GRANT' ? '+' : '-'}{h.amount}
                  </span>
                  <span className="text-slate-400">{typeLabel[h.type] || h.type}</span>
                  <span className="text-slate-500">{h.description || '-'}</span>
                </div>
                <span className="text-slate-400 shrink-0 ml-2">
                  {h.createdAt ? new Date(h.createdAt).toLocaleDateString('ko-KR') : '-'}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// ========== 가입 신청 목록 ==========

function MembershipList({ loading, memberships, actionLoading, onApprove, onReject, formatDate }) {
  if (loading) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8 text-center">
        <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full mx-auto mb-3" />
        <p className="text-sm text-slate-500">불러오는 중...</p>
      </div>
    )
  }

  if (memberships.length === 0) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8 text-center">
        <p className="text-sm text-slate-400">대기 중인 가입 신청이 없습니다</p>
      </div>
    )
  }

  return (
    <div className="space-y-2">
      {memberships.map(m => (
        <div key={m.id} className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4">
          <div className="flex items-start justify-between mb-2">
            <div>
              <span className="font-semibold text-sm text-slate-900">{m.userName}</span>
              <span className="text-xs text-slate-400 ml-2">{m.schoolName} · {m.clubName}</span>
            </div>
            <span className="text-xs text-slate-400">{formatDate(m.appliedAt)}</span>
          </div>
          <div className="flex gap-4 text-xs text-slate-500 mb-3">
            <span>학번 <strong className="text-slate-700">{m.studentId}</strong></span>
            <span>전화번호 <strong className="text-slate-700">{m.phone}</strong></span>
          </div>
          <div className="flex items-center gap-2 pt-2 border-t border-slate-50">
            <span className="text-xs text-slate-400">승인 시 "일반부원" 태그 자동 부여</span>
            <div className="flex-1" />
            <button
              onClick={() => onReject(m.id)}
              disabled={actionLoading !== null}
              className="px-3 py-1.5 text-xs text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors cursor-pointer disabled:opacity-50"
            >
              거절
            </button>
            <button
              onClick={() => onApprove(m.id)}
              disabled={actionLoading !== null}
              className="px-3 py-1.5 text-xs text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50"
            >
              승인
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}

// ========== 대시보드 패널 ==========

function DashboardPanel() {
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetch = async () => {
      try {
        const data = await api.get('/admin/dashboard')
        setStats(data)
      } catch (err) {
        handleApiError(err, '대시보드 조회')
      } finally {
        setLoading(false)
      }
    }
    fetch()
  }, [])

  if (loading) {
    return (
      <div className="py-12 text-center">
        <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full mx-auto mb-3" />
        <p className="text-sm text-slate-500">불러오는 중...</p>
      </div>
    )
  }

  if (!stats) {
    return <p className="text-sm text-slate-400 text-center py-8">데이터를 불러올 수 없습니다</p>
  }

  const sub = stats.subscriptions || {}
  const mem = stats.members || {}
  const aiUsage = stats.aiUsage || {}

  return (
    <div className="space-y-6">
      {/* 핵심 지표 */}
      <div className="grid grid-cols-4 gap-4">
        <StatCard label="전체 학교" value={stats.totalSchools} />
        <StatCard label="전체 동아리" value={stats.totalClubs} />
        <StatCard label="전체 사용자" value={stats.totalUsers} />
        <StatCard label="MAU (30일)" value={stats.mau} accent />
      </div>

      {/* 구독 / 회원 / AI 사용 */}
      <div className="grid grid-cols-3 gap-4">
        {/* 구독 현황 */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
          <h3 className="text-sm font-bold text-slate-900 mb-4">구독 현황</h3>
          <div className="space-y-3">
            <StatusRow label="활성" value={sub.active ?? 0} color="bg-green-500" />
            <StatusRow label="미활성" value={sub.inactive ?? 0} color="bg-slate-300" />
            <StatusRow label="만료" value={sub.expired ?? 0} color="bg-red-400" />
          </div>
        </div>

        {/* 회원 현황 */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
          <h3 className="text-sm font-bold text-slate-900 mb-4">회원 현황</h3>
          <div className="space-y-3">
            <StatusRow label="승인" value={mem.approved ?? 0} color="bg-green-500" />
            <StatusRow label="대기" value={mem.pending ?? 0} color="bg-amber-400" />
            <StatusRow label="거절" value={mem.rejected ?? 0} color="bg-red-400" />
          </div>
        </div>

        {/* AI 사용 현황 */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
          <h3 className="text-sm font-bold text-slate-900 mb-4">AI 사용 현황</h3>
          <div className="space-y-3">
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">전체 AI 요청</span>
              <span className="font-bold text-primary">{(aiUsage.totalUsage ?? 0).toLocaleString()}회</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function StatCard({ label, value, accent }) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
      <p className="text-xs text-slate-500 mb-1">{label}</p>
      <p className={`text-2xl font-bold ${accent ? 'text-primary' : 'text-slate-900'}`}>
        {(value ?? 0).toLocaleString()}
      </p>
    </div>
  )
}

function StatusRow({ label, value, color }) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <div className={`w-2 h-2 rounded-full ${color}`} />
        <span className="text-sm text-slate-600">{label}</span>
      </div>
      <span className="text-sm font-medium text-slate-900">{value}</span>
    </div>
  )
}

// ========== 동아리 멤버 관리 패널 ==========

function ClubMembersPanel({ club }) {
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(false)
  const addToast = useToastStore.getState().addToast

  const fetchMembers = async () => {
    setLoading(true)
    try {
      const data = await api.get(`/admin/clubs/${club.id}/members`)
      setMembers(data)
    } catch (err) {
      handleApiError(err, '멤버 조회')
    } finally {
      setLoading(false)
    }
  }

  const changeTag = async (membershipId, tagName) => {
    try {
      await api.put(`/admin/clubs/${club.id}/members/${membershipId}/tag`, { tagName })
      addToast({ type: 'SUCCESS', title: `${tagName}(으)로 변경되었습니다` })
      fetchMembers()
    } catch (err) {
      handleApiError(err, '태그 변경')
    }
  }

  useEffect(() => { fetchMembers() }, [club.id])

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 mt-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-bold text-slate-900">{club.name} — 소속 멤버</h3>
        <button onClick={fetchMembers} className="text-xs text-primary hover:text-primary/80 cursor-pointer">
          새로고침
        </button>
      </div>

      {loading ? (
        <div className="py-4 text-center">
          <div className="animate-spin w-5 h-5 border-2 border-primary border-t-transparent rounded-full mx-auto" />
        </div>
      ) : members.length === 0 ? (
        <p className="text-xs text-slate-400 text-center py-3">승인된 멤버가 없습니다</p>
      ) : (
        <div className="divide-y divide-slate-50">
          {members.map(m => (
            <div key={m.id} className="flex items-center justify-between py-2.5">
              <div className="flex items-center gap-3">
                <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium ${
                  m.isExecutive ? 'bg-amber-50 text-amber-600' : 'bg-slate-100 text-slate-600'
                }`}>
                  {m.userName?.charAt(0)}
                </div>
                <div>
                  <span className="text-sm font-medium text-slate-900">{m.userName}</span>
                  <span className="text-xs text-slate-400 ml-2">{m.studentId}</span>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <div className="flex gap-1">
                  {(m.tagNames || []).map((tag, i) => (
                    <span key={i} className="text-xs px-1.5 py-0.5 bg-slate-100 text-slate-600 rounded-full">
                      {tag}
                    </span>
                  ))}
                </div>
                <select
                  defaultValue=""
                  onChange={(e) => { if (e.target.value) { changeTag(m.id, e.target.value); e.target.value = '' } }}
                  className="text-xs border border-slate-200 rounded-lg px-2 py-1 text-slate-500 cursor-pointer"
                >
                  <option value="">역할 변경</option>
                  <option value="회장">회장</option>
                  <option value="부회장">부회장</option>
                  <option value="일반부원">일반부원</option>
                </select>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ========== 문의 관리 패널 ==========

const TICKET_STATUS = {
  OPEN: { label: '대기', color: 'bg-amber-100 text-amber-700' },
  ANSWERED: { label: '답변완료', color: 'bg-green-100 text-green-700' },
  CLOSED: { label: '종료', color: 'bg-slate-100 text-slate-500' },
}

const TICKET_CATEGORY = {
  GENERAL: '일반',
  SUBSCRIPTION: '구독',
  CREDIT: '크레딧',
  BUG_REPORT: '버그',
  OTHER: '기타',
}

function SupportPanel() {
  const [tickets, setTickets] = useState([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState('')
  const [selectedTicket, setSelectedTicket] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [replyContent, setReplyContent] = useState('')
  const [replyLoading, setReplyLoading] = useState(false)

  const fetchTickets = async (status) => {
    setLoading(true)
    try {
      const url = status ? `/admin/support/tickets?status=${status}` : '/admin/support/tickets'
      const data = await api.get(url)
      setTickets(data)
    } catch (err) {
      handleApiError(err, '티켓 목록 조회')
    } finally {
      setLoading(false)
    }
  }

  const fetchDetail = async (id) => {
    setDetailLoading(true)
    try {
      const data = await api.get(`/admin/support/tickets/${id}`)
      setDetail(data)
    } catch (err) {
      handleApiError(err, '티켓 상세 조회')
    } finally {
      setDetailLoading(false)
    }
  }

  const handleSelectTicket = (ticket) => {
    setSelectedTicket(ticket)
    setReplyContent('')
    fetchDetail(ticket.id)
  }

  const handleReply = async () => {
    if (!detail || !replyContent.trim()) return
    setReplyLoading(true)
    try {
      await api.post(`/admin/support/tickets/${detail.id}/reply`, { content: replyContent.trim() })
      setReplyContent('')
      fetchDetail(detail.id)
      fetchTickets(statusFilter)
    } catch (err) {
      setAlertModal({ open: true, message: '답변 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setReplyLoading(false)
    }
  }

  const handleClose = async (id) => {
    const ok = await showConfirm({ title: '문의 종료', message: '이 문의를 종료하시겠습니까?', confirmLabel: '종료' })
    if (!ok) return
    try {
      await api.post(`/admin/support/tickets/${id}/close`)
      fetchDetail(id)
      fetchTickets(statusFilter)
    } catch (err) {
      setAlertModal({ open: true, message: '종료 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  const handleFilterChange = (status) => {
    setStatusFilter(status)
    setSelectedTicket(null)
    setDetail(null)
    fetchTickets(status)
  }

  useEffect(() => { fetchTickets('') }, [])

  return (
    <div className="flex gap-6" style={{ minHeight: '500px' }}>
      {/* 좌측: 티켓 목록 */}
      <div className="w-80 shrink-0">
        {/* 상태 필터 */}
        <div className="flex gap-1 mb-3">
          {[
            { key: '', label: '전체' },
            { key: 'OPEN', label: '대기' },
            { key: 'ANSWERED', label: '답변완료' },
            { key: 'CLOSED', label: '종료' },
          ].map(f => (
            <button
              key={f.key}
              onClick={() => handleFilterChange(f.key)}
              className={`px-3 py-1.5 text-xs rounded-lg transition-colors cursor-pointer ${
                statusFilter === f.key
                  ? 'bg-primary text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        {/* 티켓 리스트 */}
        {loading ? (
          <div className="py-8 text-center">
            <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full mx-auto" />
          </div>
        ) : tickets.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8 text-center">
            <p className="text-sm text-slate-400">문의가 없습니다</p>
          </div>
        ) : (
          <div className="space-y-1">
            {tickets.map(t => {
              const st = TICKET_STATUS[t.status] || TICKET_STATUS.OPEN
              return (
                <button
                  key={t.id}
                  onClick={() => handleSelectTicket(t)}
                  className={`w-full text-left px-3 py-3 rounded-lg transition-all cursor-pointer ${
                    selectedTicket?.id === t.id
                      ? 'bg-primary/10 border border-primary/20'
                      : 'bg-white border border-slate-200 shadow-sm hover:border-slate-200'
                  }`}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${st.color}`}>
                      {st.label}
                    </span>
                    <span className="text-xs text-slate-400">
                      {TICKET_CATEGORY[t.category] || t.category}
                    </span>
                  </div>
                  <p className="text-sm font-medium text-slate-900 truncate">{t.title}</p>
                  <div className="flex justify-between mt-1">
                    <span className="text-xs text-slate-400">{t.userEmail}</span>
                    <span className="text-xs text-slate-400">
                      {t.createdAt ? new Date(t.createdAt).toLocaleDateString('ko-KR') : '-'}
                    </span>
                  </div>
                </button>
              )
            })}
          </div>
        )}
      </div>

      {/* 우측: 상세 + 답변 */}
      <div className="flex-1 min-w-0">
        {!detail && !detailLoading ? (
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
            <p className="text-sm text-slate-400">좌측에서 문의를 선택하세요</p>
          </div>
        ) : detailLoading ? (
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
            <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full mx-auto" />
          </div>
        ) : detail && (
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
            {/* 헤더 */}
            <div className="flex items-start justify-between mb-4">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${(TICKET_STATUS[detail.status] || TICKET_STATUS.OPEN).color}`}>
                    {(TICKET_STATUS[detail.status] || TICKET_STATUS.OPEN).label}
                  </span>
                  <span className="text-xs text-slate-400">{TICKET_CATEGORY[detail.category] || detail.category}</span>
                </div>
                <h3 className="text-lg font-bold text-slate-900">{detail.title}</h3>
                <p className="text-xs text-slate-400 mt-1">{detail.userEmail} · {detail.createdAt ? new Date(detail.createdAt).toLocaleString('ko-KR') : '-'}</p>
              </div>
              {detail.status !== 'CLOSED' && (
                <button
                  onClick={() => handleClose(detail.id)}
                  className="text-xs text-slate-400 hover:text-red-500 cursor-pointer"
                >
                  종료
                </button>
              )}
            </div>

            {/* 문의 내용 */}
            <div className="px-4 py-3 bg-slate-50 rounded-lg mb-4">
              <p className="text-sm text-slate-700 whitespace-pre-wrap">{detail.content}</p>
            </div>

            {/* 답변 스레드 */}
            {detail.replies && detail.replies.length > 0 && (
              <div className="space-y-2 mb-4">
                <h4 className="text-xs font-medium text-slate-500">답변 내역</h4>
                {detail.replies.map(r => (
                  <div
                    key={r.id}
                    className={`px-4 py-3 rounded-lg text-sm ${
                      r.adminReply
                        ? 'bg-blue-50 border border-blue-100'
                        : 'bg-slate-50 border border-slate-200 shadow-sm'
                    }`}
                  >
                    <div className="flex justify-between mb-1">
                      <span className={`text-xs font-medium ${r.adminReply ? 'text-blue-600' : 'text-slate-500'}`}>
                        {r.adminReply ? '관리자' : r.authorEmail}
                      </span>
                      <span className="text-xs text-slate-400">
                        {r.createdAt ? new Date(r.createdAt).toLocaleString('ko-KR') : '-'}
                      </span>
                    </div>
                    <p className="text-slate-700 whitespace-pre-wrap">{r.content}</p>
                  </div>
                ))}
              </div>
            )}

            {/* 답변 입력 */}
            {detail.status !== 'CLOSED' && (
              <div>
                <textarea
                  value={replyContent}
                  onChange={(e) => setReplyContent(e.target.value)}
                  placeholder="답변을 입력하세요..."
                  rows={3}
                  className="w-full px-4 py-3 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary resize-none"
                />
                <div className="flex justify-end mt-2">
                  <button
                    onClick={handleReply}
                    disabled={replyLoading || !replyContent.trim()}
                    className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50"
                  >
                    {replyLoading ? '전송 중...' : '답변 전송'}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

// ========== 모델 데이터 관리 패널 ==========

function ModelDataPanel({ showConfirm, setAlertModal }) {
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [exporting, setExporting] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [lastExportId, setLastExportId] = useState(null)
  const [dateRange, setDateRange] = useState({ from: '', to: '' })

  const fetchStats = async () => {
    setLoading(true)
    try {
      const data = await api.get('/admin/model-data/stats')
      setStats(data)
      // 기간 자동 설정
      if (data.periodFrom && data.periodTo) {
        setDateRange({ from: data.periodFrom, to: data.periodTo })
      }
    } catch (err) {
      handleApiError(err, '모델 데이터 현황 조회')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchStats() }, [])

  const handleExport = async () => {
    if (!dateRange.from || !dateRange.to) {
      setAlertModal({ open: true, message: '내보내기 기간을 선택하세요' })
      return
    }
    setExporting(true)
    try {
      const result = await api.downloadBlob('/admin/model-data/export', {
        from: dateRange.from,
        to: dateRange.to,
      })
      if (!result) return

      // Blob 다운로드
      const url = URL.createObjectURL(result.blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `hype-model-export-${new Date().toISOString().slice(0, 10)}.zip`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)

      setLastExportId(result.exportId)

      // 다운로드 완료 확인 → 삭제 제안
      const ok = await showConfirm({
        title: '다운로드 완료',
        message: `ZIP 파일이 다운로드되었습니다.\n서버에서 해당 기간(${dateRange.from} ~ ${dateRange.to})의 데이터를 삭제하시겠습니까?`,
        danger: true,
        confirmLabel: '서버 데이터 삭제',
      })

      if (ok && result.exportId) {
        await handleDelete(result.exportId)
      }
    } catch (err) {
      setAlertModal({ open: true, message: '내보내기 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setExporting(false)
    }
  }

  const handleDelete = async (exportId) => {
    const id = exportId || lastExportId
    if (!id) {
      setAlertModal({ open: true, message: '삭제할 내보내기 ID가 없습니다. 먼저 내보내기를 실행하세요.' })
      return
    }
    setDeleting(true)
    try {
      const result = await api.delete(`/admin/model-data/${id}`)
      const total = (result.deletedScoreLogs || 0) + (result.deletedAttentionLogs || 0) +
                    (result.deletedEngagements || 0) + (result.deletedSnapshots || 0)
      setAlertModal({ open: true, message: `삭제 완료: 총 ${total.toLocaleString()} rows 삭제됨` })
      setLastExportId(null)
      fetchStats()
    } catch (err) {
      setAlertModal({ open: true, message: '삭제 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setDeleting(false)
    }
  }

  if (loading) {
    return (
      <div className="py-12 text-center">
        <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full mx-auto mb-3" />
        <p className="text-sm text-slate-500">불러오는 중...</p>
      </div>
    )
  }

  const hasData = stats && (stats.scoreLogCount > 0 || stats.attentionLogCount > 0 || stats.engagementCount > 0 || stats.snapshotCount > 0)

  return (
    <div className="max-w-2xl space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-slate-900">모델 데이터</h2>
        <button onClick={fetchStats} className="text-xs text-primary hover:text-primary/80 cursor-pointer">
          새로고침
        </button>
      </div>

      {/* 데이터 현황 */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
        <h3 className="text-sm font-bold text-slate-900 mb-4">데이터 현황</h3>

        {!hasData ? (
          <p className="text-sm text-slate-400 text-center py-6">축적된 모델 데이터가 없습니다</p>
        ) : (
          <>
            {/* 기간 */}
            <div className="px-3 py-2.5 bg-slate-50 rounded-lg mb-4">
              <div className="flex gap-4 text-xs text-slate-600">
                <span>기간 <strong>{stats.periodFrom || '-'} ~ {stats.periodTo || '-'}</strong></span>
                <span>({stats.days || 0}일)</span>
              </div>
            </div>

            {/* 테이블별 row 수 */}
            <div className="space-y-2 mb-4">
              <DataRow label="score_logs" count={stats.scoreLogCount} sizeHint="~0.2KB/row" />
              <DataRow label="attention_logs" count={stats.attentionLogCount} sizeHint="~0.12KB/row" />
              <DataRow label="engagements" count={stats.engagementCount} sizeHint="~0.12KB/row" />
              <DataRow label="snapshots" count={stats.snapshotCount} sizeHint="~0.1KB/row" />
            </div>

            {/* 합계 */}
            <div className="flex items-center justify-between px-3 py-2.5 bg-blue-50 rounded-lg text-sm">
              <span className="text-slate-700 font-medium">예상 용량</span>
              <span className="font-bold text-primary">~{stats.estimatedSizeMb || 0}MB</span>
            </div>
          </>
        )}
      </div>

      {/* 내보내기 */}
      {hasData && (
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
          <h3 className="text-sm font-bold text-slate-900 mb-4">모델 데이터 내보내기</h3>

          {/* 기간 선택 */}
          <div className="flex gap-3 mb-4">
            <div className="flex-1">
              <label className="block text-xs text-slate-500 mb-1">시작일</label>
              <input
                type="date"
                value={dateRange.from}
                onChange={(e) => setDateRange(prev => ({ ...prev, from: e.target.value }))}
                className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-slate-500 mb-1">종료일</label>
              <input
                type="date"
                value={dateRange.to}
                onChange={(e) => setDateRange(prev => ({ ...prev, to: e.target.value }))}
                className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
          </div>

          <p className="text-xs text-slate-400 mb-4">
            ZIP 파일로 다운로드됩니다 (JSONL + CSV). 다운로드 완료 후 서버 데이터 삭제 여부를 확인합니다.
          </p>

          <button
            onClick={handleExport}
            disabled={exporting || deleting || !dateRange.from || !dateRange.to}
            className="w-full py-2.5 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50"
          >
            {exporting ? '내보내기 중...' : '모델 데이터 내보내기'}
          </button>

          {lastExportId && (
            <div className="mt-3 px-3 py-2.5 bg-amber-50 rounded-lg">
              <p className="text-xs text-amber-700">
                마지막 내보내기 ID: <code className="font-mono text-xs">{lastExportId}</code>
              </p>
              <button
                onClick={() => handleDelete()}
                disabled={deleting}
                className="mt-2 px-3 py-1.5 text-xs font-medium text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors cursor-pointer disabled:opacity-50"
              >
                {deleting ? '삭제 중...' : '서버 데이터 삭제'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function DataRow({ label, count, sizeHint }) {
  return (
    <div className="flex items-center justify-between px-3 py-2 bg-slate-50 rounded-lg">
      <div className="flex items-center gap-2">
        <code className="text-xs font-mono text-slate-700">{label}</code>
        <span className="text-xs text-slate-400">{sizeHint}</span>
      </div>
      <span className="text-sm font-medium text-slate-900">{(count || 0).toLocaleString()} rows</span>
    </div>
  )
}

/** ========== 글로벌 설정 패널 (드라이브 정책) ========== */
function GlobalSettingsPanel() {
  const [settings, setSettings] = useState(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [form, setForm] = useState({ driveAllowedFormats: 'pdf', driveMaxFileSizeMb: 50, aiCreditCost: 1, defaultDailyAiLimit: 10, dailyCommentBotMin: 5, dailyCommentBotMax: 10 })

  useEffect(() => { fetchSettings() }, [])

  const fetchSettings = async () => {
    try {
      setLoading(true)
      const res = await api.get('/admin/settings')
      setSettings(res)
      setForm({
        driveAllowedFormats: res.driveAllowedFormats || 'pdf',
        driveMaxFileSizeMb: res.driveMaxFileSizeMb || 50,
        aiCreditCost: res.aiCreditCost ?? 1,
        defaultDailyAiLimit: res.defaultDailyAiLimit ?? 10,
        dailyCommentBotMin: res.dailyCommentBotMin ?? 5,
        dailyCommentBotMax: res.dailyCommentBotMax ?? 10,
      })
    } catch (e) {
      handleApiError(e, '글로벌 설정 조회')
      setMessage('설정을 불러올 수 없습니다')
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      setSaving(true)
      setMessage('')
      const res = await api.put('/admin/settings', form)
      setSettings(res)
      setEditing(false)
      setMessage('저장되었습니다')
      setTimeout(() => setMessage(''), 2000)
    } catch (e) {
      handleApiError(e, '글로벌 설정 저장')
      setMessage(e.message || '저장에 실패했습니다')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-12">
        <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full" />
      </div>
    )
  }

  return (
    <div className="max-w-xl space-y-6">
      <h2 className="text-lg font-bold text-slate-900">글로벌 설정</h2>

      {message && (
        <p className={`text-sm px-3 py-2 rounded-lg ${message.includes('실패') || message.includes('없습니다') ? 'bg-red-50 text-red-500' : 'bg-green-50 text-green-600'}`}>
          {message}
        </p>
      )}

      {/* 드라이브 정책 */}
      <div className="bg-white rounded-2xl border">
        <div className="flex items-center justify-between px-4 py-3 border-b">
          <h3 className="text-sm font-semibold text-slate-700">드라이브 정책</h3>
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
          <div>
            <label className="text-xs text-slate-400 block mb-1">허용 파일 포맷 (쉼표 구분)</label>
            {editing ? (
              <input
                value={form.driveAllowedFormats}
                onChange={e => setForm(f => ({ ...f, driveAllowedFormats: e.target.value }))}
                className="w-full border rounded-lg px-3 py-2 text-sm"
                placeholder="pdf"
              />
            ) : (
              <div className="flex gap-1.5 flex-wrap">
                {(settings?.driveAllowedFormats || 'pdf').split(',').map(fmt => (
                  <span key={fmt} className="px-2 py-0.5 text-xs bg-slate-100 text-slate-600 rounded-md">
                    .{fmt.trim()}
                  </span>
                ))}
              </div>
            )}
            <p className="text-xs text-slate-300 mt-1">현재 PDF 뷰어만 지원. 추후 포맷 확장 시 여기서 관리</p>
          </div>

          <div>
            <label className="text-xs text-slate-400 block mb-1">최대 파일 크기 (MB)</label>
            {editing ? (
              <input
                type="number" min="1" max="500"
                value={form.driveMaxFileSizeMb}
                onChange={e => setForm(f => ({ ...f, driveMaxFileSizeMb: Number(e.target.value) }))}
                className="border rounded-lg px-3 py-2 text-sm w-28"
              />
            ) : (
              <p className="text-sm text-slate-700">{settings?.driveMaxFileSizeMb || 50} MB</p>
            )}
          </div>
        </div>
      </div>

      {/* AI 정책 */}
      <div className="bg-white rounded-2xl border">
        <div className="px-4 py-3 border-b">
          <h3 className="text-sm font-semibold text-slate-700">AI 정책</h3>
        </div>
        <div className="px-4 py-4 space-y-4">
          <div>
            <label className="text-xs text-slate-400 block mb-1">동아리당 일일 AI 생성 제한</label>
            {editing ? (
              <input
                type="number" min="0" max="1000"
                value={form.defaultDailyAiLimit}
                onChange={e => setForm(f => ({ ...f, defaultDailyAiLimit: Number(e.target.value) }))}
                className="border rounded-lg px-3 py-2 text-sm w-28"
              />
            ) : (
              <p className="text-sm text-slate-700">{settings?.defaultDailyAiLimit ?? 10}회 {settings?.defaultDailyAiLimit === 0 ? '(무제한)' : ''}</p>
            )}
            <p className="text-xs text-slate-300 mt-1">0으로 설정하면 무제한</p>
          </div>
        </div>
      </div>

      {/* AI 댓글봇 정책 */}
      <div className="bg-white rounded-2xl border">
        <div className="px-4 py-3 border-b">
          <h3 className="text-sm font-semibold text-slate-700">AI 댓글봇 정책</h3>
        </div>
        <div className="px-4 py-4 space-y-4">
          <div className="flex gap-6">
            <div className="flex-1">
              <label className="text-xs text-slate-400 block mb-1">일일 최소 횟수</label>
              {editing ? (
                <input
                  type="number" min="1" max="50"
                  value={form.dailyCommentBotMin}
                  onChange={e => setForm(f => ({ ...f, dailyCommentBotMin: Number(e.target.value) }))}
                  className="border rounded-lg px-3 py-2 text-sm w-full"
                />
              ) : (
                <p className="text-sm text-slate-700">{settings?.dailyCommentBotMin ?? 5}회</p>
              )}
            </div>
            <div className="flex-1">
              <label className="text-xs text-slate-400 block mb-1">일일 최대 횟수</label>
              {editing ? (
                <input
                  type="number" min="1" max="50"
                  value={form.dailyCommentBotMax}
                  onChange={e => setForm(f => ({ ...f, dailyCommentBotMax: Number(e.target.value) }))}
                  className="border rounded-lg px-3 py-2 text-sm w-full"
                />
              ) : (
                <p className="text-sm text-slate-700">{settings?.dailyCommentBotMax ?? 10}회</p>
              )}
            </div>
          </div>
          <p className="text-xs text-slate-300">동아리당 하루 댓글봇 실행 횟수 범위 (최소~최대 사이 랜덤)</p>
        </div>
      </div>
    </div>
  )
}

// ========== 스티커 관리 패널 (슈퍼어드민) ==========

function StickerManagementPanel() {
  const [packs, setPacks] = useState([])
  const [selectedPackId, setSelectedPackId] = useState(null)
  const [stickers, setStickers] = useState([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [newPackName, setNewPackName] = useState('')
  const [newPackPrice, setNewPackPrice] = useState(0)
  const [uploading, setUploading] = useState(false)

  const fetchPacks = async () => {
    try {
      const data = await api.get('/admin/sticker-packs')
      setPacks(data)
    } catch (err) {
      handleApiError(err, '스티커 팩 조회')
    } finally {
      setLoading(false)
    }
  }

  const fetchStickers = async (packId) => {
    try {
      const data = await api.get(`/admin/sticker-packs/${packId}/stickers`)
      setStickers(data)
    } catch (err) {
      handleApiError(err, '스티커 조회')
    }
  }

  useEffect(() => { fetchPacks() }, [])
  useEffect(() => { if (selectedPackId) fetchStickers(selectedPackId) }, [selectedPackId])

  const handleCreatePack = async () => {
    if (!newPackName.trim()) return
    setCreating(true)
    try {
      await api.post('/admin/sticker-packs', { name: newPackName.trim(), price: newPackPrice })
      setNewPackName('')
      setNewPackPrice(0)
      fetchPacks()
    } catch (err) {
      handleApiError(err, '스티커 팩 생성')
    } finally {
      setCreating(false)
    }
  }

  const handleToggleActive = async (packId) => {
    try {
      await api.patch(`/admin/sticker-packs/${packId}/active`)
      fetchPacks()
    } catch (err) {
      handleApiError(err, '활성화 토글')
    }
  }

  const handleUploadSticker = async (e) => {
    const files = e.target.files
    if (!files || files.length === 0 || !selectedPackId) return
    setUploading(true)
    try {
      for (const file of files) {
        const formData = new FormData()
        formData.append('file', file)
        await api.upload(`/admin/sticker-packs/${selectedPackId}/stickers`, formData)
      }
      fetchStickers(selectedPackId)
      fetchPacks()
    } catch (err) {
      handleApiError(err, '스티커 업로드')
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

  const handleDeleteSticker = async (stickerId) => {
    try {
      await api.delete(`/admin/sticker-packs/${selectedPackId}/stickers/${stickerId}`)
      fetchStickers(selectedPackId)
      fetchPacks()
    } catch (err) {
      handleApiError(err, '스티커 삭제')
    }
  }

  const selectedPack = packs.find(p => p.id === selectedPackId)

  if (loading) return <div className="text-center py-12 text-slate-400">불러오는 중...</div>

  return (
    <div className="flex gap-6">
      {/* 좌측: 팩 목록 */}
      <div className="w-72 shrink-0 space-y-4">
        <h2 className="text-sm font-bold text-slate-900">스티커 팩</h2>

        {/* 팩 생성 */}
        <div className="bg-white border rounded-xl p-3 space-y-2">
          <input
            value={newPackName}
            onChange={e => setNewPackName(e.target.value)}
            placeholder="팩 이름"
            className="w-full text-sm border rounded-md px-2 py-1.5 outline-none focus:ring-1 focus:ring-primary"
          />
          <div className="flex gap-2">
            <input
              type="number"
              value={newPackPrice}
              onChange={e => setNewPackPrice(Number(e.target.value))}
              placeholder="가격"
              className="flex-1 text-sm border rounded-md px-2 py-1.5 outline-none focus:ring-1 focus:ring-primary"
            />
            <button
              onClick={handleCreatePack}
              disabled={creating || !newPackName.trim()}
              className="px-3 py-1.5 text-xs bg-primary text-white rounded-md hover:bg-primary/90 disabled:opacity-50 cursor-pointer"
            >
              생성
            </button>
          </div>
        </div>

        {/* 팩 리스트 */}
        <div className="space-y-1">
          {packs.map(pack => (
            <button
              key={pack.id}
              onClick={() => setSelectedPackId(pack.id)}
              className={`w-full text-left px-3 py-2.5 rounded-lg text-sm flex items-center justify-between transition-colors cursor-pointer ${
                selectedPackId === pack.id
                  ? 'bg-primary/10 text-primary font-medium'
                  : 'hover:bg-slate-50 text-slate-700'
              }`}
            >
              <div className="flex items-center gap-2 min-w-0">
                {pack.thumbnailUrl && (
                  <img src={pack.thumbnailUrl} alt="" className="w-6 h-6 rounded" />
                )}
                <span className="truncate">{pack.name}</span>
                <span className="text-xs text-slate-400">{pack.stickerCount}개</span>
              </div>
              <span className={`text-xs px-1.5 py-0.5 rounded ${pack.active ? 'bg-green-100 text-green-600' : 'bg-slate-100 text-slate-400'}`}>
                {pack.active ? '활성' : '비활성'}
              </span>
            </button>
          ))}
          {packs.length === 0 && (
            <p className="text-xs text-slate-400 text-center py-4">스티커 팩이 없습니다</p>
          )}
        </div>
      </div>

      {/* 우측: 팩 상세 */}
      <div className="flex-1">
        {selectedPack ? (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-slate-900">{selectedPack.name}</h3>
                <p className="text-xs text-slate-500">{selectedPack.stickerCount}개</p>
              </div>
              <button
                onClick={() => handleToggleActive(selectedPack.id)}
                className={`px-3 py-1.5 text-xs rounded-lg cursor-pointer ${
                  selectedPack.active
                    ? 'bg-red-50 text-red-500 hover:bg-red-100'
                    : 'bg-green-50 text-green-600 hover:bg-green-100'
                }`}
              >
                {selectedPack.active ? '비활성화' : '활성화'}
              </button>
            </div>

            {/* 스티커 업로드 */}
            <div className="flex items-center gap-2">
              <label className="px-3 py-1.5 text-xs bg-primary text-white rounded-lg hover:bg-primary/90 cursor-pointer">
                {uploading ? '업로드 중...' : 'WebP 스티커 추가'}
                <input type="file" accept="image/webp" multiple onChange={handleUploadSticker} className="hidden" />
              </label>
              <span className="text-xs text-slate-400">512×512px, 300KB 이하</span>
            </div>

            {/* 스티커 그리드 */}
            <div className="grid grid-cols-6 gap-3">
              {stickers.map(s => (
                <div key={s.id} className="relative group">
                  <img src={s.imageUrl} alt="" className="w-full aspect-square rounded-lg bg-slate-50 object-contain" />
                  <button
                    onClick={() => handleDeleteSticker(s.id)}
                    className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white rounded-full text-xs opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer flex items-center justify-center"
                  >
                    ✕
                  </button>
                </div>
              ))}
              {stickers.length === 0 && (
                <p className="col-span-6 text-center text-xs text-slate-400 py-8">스티커가 없습니다. 위에서 WebP 파일을 업로드하세요.</p>
              )}
            </div>
          </div>
        ) : (
          <div className="text-center py-20 text-slate-400 text-sm">
            좌측에서 스티커 팩을 선택하세요
          </div>
        )}
      </div>
    </div>
  )
}
