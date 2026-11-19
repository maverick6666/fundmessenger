import { useState, useEffect } from 'react'
import api from '../lib/api'
import ConfirmModal from './ConfirmModal'
import handleApiError from '../lib/handleApiError'

export default function GenerationManager({ clubId }) {
  const [generations, setGenerations] = useState([])
  const [loading, setLoading] = useState(true)
  const [showBatchModal, setShowBatchModal] = useState(false)
  const [startNum, setStartNum] = useState('')
  const [endNum, setEndNum] = useState('')
  const [batchLoading, setBatchLoading] = useState(false)
  const [deleteLoading, setDeleteLoading] = useState(null)
  const [showTransition, setShowTransition] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  useEffect(() => {
    fetchGenerations()
  }, [clubId])

  const fetchGenerations = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/generations`)
      setGenerations(data)
    } catch (err) {
      handleApiError(err, '기수 조회')
    } finally {
      setLoading(false)
    }
  }

  const handleBatchCreate = async () => {
    const s = parseInt(startNum)
    const e = parseInt(endNum)
    if (!s || !e || s > e || e - s > 50) return
    setBatchLoading(true)
    try {
      const created = await api.post(`/clubs/${clubId}/generations/batch`, { startNumber: s, endNumber: e })
      setGenerations(prev => [...prev, ...created])
      setShowBatchModal(false)
      setStartNum('')
      setEndNum('')
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setBatchLoading(false)
    }
  }

  const handleDeleteClick = (genId) => {
    setDeleteTarget(genId)
    setShowDeleteConfirm(true)
  }

  const handleDeleteConfirm = async () => {
    const genId = deleteTarget
    setShowDeleteConfirm(false)
    setDeleteTarget(null)
    setDeleteLoading(genId)
    try {
      await api.delete(`/clubs/${clubId}/generations/${genId}`)
      setGenerations(prev => prev.filter(g => g.id !== genId))
    } catch (err) {
      setAlertModal({ open: true, message: err.message })
    } finally {
      setDeleteLoading(null)
    }
  }

  if (loading) {
    return <div className="text-center py-8 text-slate-400">불러오는 중...</div>
  }

  return (
    <div className="space-y-6">
      {showDeleteConfirm && (
        <ConfirmModal
          title="기수 삭제"
          message="이 기수 태그를 삭제하시겠습니까?"
          confirmLabel="삭제"
          onConfirm={handleDeleteConfirm}
          onClose={() => { setShowDeleteConfirm(false); setDeleteTarget(null) }}
          danger
        />
      )}
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-slate-900">기수 관리</h3>
        <div className="flex gap-2">
          {generations.length > 0 && (
            <button
              onClick={() => setShowTransition(true)}
              className="px-4 py-2 text-sm font-medium bg-amber-500 text-white rounded-lg hover:bg-amber-600 transition-colors cursor-pointer"
            >
              기수 전환
            </button>
          )}
          <button
            onClick={() => setShowBatchModal(true)}
            className="px-4 py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary/90 transition-colors cursor-pointer"
          >
            + 기수 일괄 생성
          </button>
        </div>
      </div>

      {/* 기수 목록 */}
      {generations.length === 0 ? (
        <div className="text-center py-12 text-slate-400">
          <p className="text-sm">등록된 기수가 없습니다</p>
          <p className="text-xs mt-1">일괄 생성으로 기수 태그를 추가하세요</p>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          {generations.map(gen => (
            <div
              key={gen.id}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-indigo-50 text-indigo-700 rounded-full text-sm font-medium"
            >
              <span>{gen.name}</span>
              <button
                onClick={() => handleDeleteClick(gen.id)}
                disabled={deleteLoading === gen.id}
                className="w-4 h-4 flex items-center justify-center text-indigo-400 hover:text-red-500 transition-colors cursor-pointer disabled:opacity-50"
                title="삭제"
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      {/* 멤버 기수 관리 안내 */}
      {generations.length > 0 && (
        <div className="bg-slate-50 rounded-xl p-4 text-sm text-slate-500">
          <p>멤버의 기수는 관리 탭의 멤버 목록에서 개별 설정할 수 있습니다.</p>
          <p className="mt-1">기수 전환 버튼으로 새 기수를 시작하고, 멤버/팀 상태를 일괄 처리할 수 있습니다.</p>
        </div>
      )}

      {/* 일괄 생성 모달 */}
      {showBatchModal && (
        <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center">
          <div className="absolute inset-0 bg-black/30" onClick={() => setShowBatchModal(false)} />
          <div className="relative bg-white rounded-2xl shadow-xl px-6 py-5 max-w-sm w-full mx-4">
            <h4 className="text-base font-semibold text-slate-900 mb-4">기수 일괄 생성</h4>
            <div className="flex items-center gap-3 mb-4">
              <div className="flex-1">
                <label className="block text-xs text-slate-500 mb-1">시작</label>
                <input
                  type="number"
                  min="1"
                  value={startNum}
                  onChange={e => setStartNum(e.target.value)}
                  placeholder="1"
                  className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
                />
              </div>
              <span className="text-slate-400 mt-5">~</span>
              <div className="flex-1">
                <label className="block text-xs text-slate-500 mb-1">끝</label>
                <input
                  type="number"
                  min="1"
                  value={endNum}
                  onChange={e => setEndNum(e.target.value)}
                  placeholder="35"
                  className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-primary/20 focus:border-primary outline-none"
                />
              </div>
            </div>
            {startNum && endNum && parseInt(endNum) >= parseInt(startNum) && (
              <p className="text-xs text-slate-500 mb-4">
                {parseInt(startNum)}기 ~ {parseInt(endNum)}기 ({parseInt(endNum) - parseInt(startNum) + 1}개) 생성
              </p>
            )}
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowBatchModal(false)}
                className="px-4 py-2 text-sm text-slate-500 hover:text-slate-700 rounded-lg hover:bg-slate-50 transition-colors cursor-pointer"
              >
                취소
              </button>
              <button
                onClick={handleBatchCreate}
                disabled={batchLoading || !startNum || !endNum || parseInt(startNum) > parseInt(endNum)}
                className="px-4 py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50"
              >
                {batchLoading ? '생성 중...' : '생성'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 기수 전환 위자드 */}
      {showTransition && (
        <TransitionWizard
          clubId={clubId}
          onClose={() => setShowTransition(false)}
          onComplete={() => { setShowTransition(false); fetchGenerations() }}
        />
      )}

      {alertModal.open && (
        <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
    </div>
  )
}

/* ─── 기수 전환 5단계 위자드 ─── */
const STEPS = [
  { label: '새 기수', desc: '새로운 기수 이름 설정' },
  { label: '멤버 분류', desc: '계속활동 / 졸업 / 탈퇴 분류' },
  { label: '팀 처리', desc: '유지 / 해체 결정' },
  { label: '역할 초기화', desc: '임원 직급 초기화 확인' },
  { label: '회장 지정', desc: '차기 회장 선택' },
]

function TransitionWizard({ clubId, onClose, onComplete }) {
  const [step, setStep] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)

  // 데이터
  const [members, setMembers] = useState([])
  const [teams, setTeams] = useState([])
  const [dataLoading, setDataLoading] = useState(true)

  // Step 1: 새 기수 이름
  const [newGenName, setNewGenName] = useState('')

  // Step 2: 멤버 분류 (default: continue)
  const [memberClassification, setMemberClassification] = useState({})

  // Step 3: 팀 처리 (default: retain)
  const [teamAction, setTeamAction] = useState({})

  // Step 5: 차기 회장
  const [nextPresidentId, setNextPresidentId] = useState(null)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  useEffect(() => {
    Promise.all([
      api.get(`/clubs/${clubId}/members`),
      api.get(`/clubs/${clubId}/teams`),
    ]).then(([m, t]) => {
      const activeMembers = (m || []).filter(mem => mem.status === 'APPROVED')
      setMembers(activeMembers)
      setTeams(t || [])
      // 기본값: 모두 continue, 모두 retain
      const mc = {}
      activeMembers.forEach(mem => { mc[mem.membershipId] = 'continue' })
      setMemberClassification(mc)
      const ta = {}
      ;(t || []).forEach(team => { ta[team.id] = 'retain' })
      setTeamAction(ta)
    }).catch(err => {
      handleApiError(err, '기수 전환 데이터 로딩')
    }).finally(() => setDataLoading(false))
  }, [clubId])

  const continueMemberIds = Object.entries(memberClassification).filter(([, v]) => v === 'continue').map(([k]) => Number(k))
  const alumniMemberIds = Object.entries(memberClassification).filter(([, v]) => v === 'alumni').map(([k]) => Number(k))
  const inactiveMemberIds = Object.entries(memberClassification).filter(([, v]) => v === 'inactive').map(([k]) => Number(k))
  const retainTeamIds = Object.entries(teamAction).filter(([, v]) => v === 'retain').map(([k]) => Number(k))
  const dissolveTeamIds = Object.entries(teamAction).filter(([, v]) => v === 'dissolve').map(([k]) => Number(k))

  const canNext = () => {
    if (step === 0) return true // newGenName은 optional
    if (step === 1) return members.length > 0
    if (step === 2) return true
    if (step === 3) return true
    if (step === 4) return nextPresidentId != null
    return false
  }

  const handleSubmit = async () => {
    setSubmitting(true)
    try {
      const res = await api.post(`/clubs/${clubId}/generations/transition`, {
        newGenerationName: newGenName || null,
        continueMemberIds,
        alumniMemberIds,
        inactiveMemberIds,
        retainTeamIds,
        dissolveTeamIds,
        nextPresidentMembershipId: nextPresidentId,
      })
      setResult(res)
      setStep(5) // 완료 단계
    } catch (err) {
      setAlertModal({ open: true, message: '기수 전환 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setSubmitting(false)
    }
  }

  if (dataLoading) {
    return (
      <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center">
        <div className="absolute inset-0 bg-black/40" />
        <div className="relative bg-white rounded-2xl shadow-xl p-8 text-center">
          <div className="animate-spin w-6 h-6 border-2 border-slate-300 border-t-slate-600 rounded-full mx-auto" />
          <p className="text-sm text-slate-500 mt-3">데이터 불러오는 중...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="fixed inset-0 z-50 modal-overlay flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={step < 5 ? onClose : undefined} />
      <div className="relative bg-white rounded-2xl shadow-xl w-full max-w-2xl mx-4 max-h-[85vh] flex flex-col">
        {/* 헤더 + 스텝 인디케이터 */}
        <div className="px-6 pt-5 pb-4 border-b border-slate-100">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-bold text-slate-900">기수 전환</h3>
            {step < 5 && (
              <button onClick={onClose} className="text-slate-400 hover:text-slate-600 cursor-pointer text-lg">✕</button>
            )}
          </div>
          {step < 5 && (
            <div className="flex gap-1">
              {STEPS.map((s, i) => (
                <div key={i} className="flex-1">
                  <div className={`h-1 rounded-full transition-colors ${i <= step ? 'bg-amber-500' : 'bg-slate-200'}`} />
                  <p className={`text-xs mt-1 ${i === step ? 'text-amber-600 font-semibold' : 'text-slate-400'}`}>
                    {s.label}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 콘텐츠 */}
        <div className="flex-1 overflow-y-auto px-6 py-5">
          {step === 0 && <Step1_GenName name={newGenName} setName={setNewGenName} />}
          {step === 1 && <Step2_Members members={members} classification={memberClassification} setClassification={setMemberClassification} />}
          {step === 2 && <Step3_Teams teams={teams} teamAction={teamAction} setTeamAction={setTeamAction} />}
          {step === 3 && (
            <Step4_RoleReset
              alumniCount={alumniMemberIds.length}
              inactiveCount={inactiveMemberIds.length}
              continueCount={continueMemberIds.length}
            />
          )}
          {step === 4 && (
            <Step5_President
              members={members}
              continueMemberIds={continueMemberIds}
              nextPresidentId={nextPresidentId}
              setNextPresidentId={setNextPresidentId}
            />
          )}
          {step === 5 && result && <StepComplete result={result} />}
        </div>

        {/* 푸터 */}
        <div className="px-6 py-4 border-t border-slate-100 flex justify-between">
          {step < 5 ? (
            <>
              <button
                onClick={() => step > 0 ? setStep(step - 1) : onClose()}
                className="px-4 py-2 text-sm text-slate-500 hover:text-slate-700 rounded-lg hover:bg-slate-50 transition-colors cursor-pointer"
              >
                {step === 0 ? '취소' : '이전'}
              </button>
              {step < 4 ? (
                <button
                  onClick={() => setStep(step + 1)}
                  disabled={!canNext()}
                  className="px-5 py-2 text-sm font-medium bg-amber-500 text-white rounded-lg hover:bg-amber-600 transition-colors cursor-pointer disabled:opacity-50"
                >
                  다음
                </button>
              ) : (
                <button
                  onClick={handleSubmit}
                  disabled={!canNext() || submitting}
                  className="px-5 py-2 text-sm font-medium bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors cursor-pointer disabled:opacity-50"
                >
                  {submitting ? '처리 중...' : '기수 전환 실행'}
                </button>
              )}
            </>
          ) : (
            <div className="w-full flex justify-end">
              <button
                onClick={onComplete}
                className="px-5 py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary/90 transition-colors cursor-pointer"
              >
                완료
              </button>
            </div>
          )}
        </div>

        {alertModal.open && (
          <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
        )}
      </div>
    </div>
  )
}

/* ─── Step 1: 새 기수 이름 ─── */
function Step1_GenName({ name, setName }) {
  return (
    <div className="space-y-4">
      <div>
        <h4 className="text-base font-semibold text-slate-900">새 기수 이름</h4>
        <p className="text-sm text-slate-500 mt-1">비워두면 자동으로 다음 번호가 부여됩니다 (예: 36기).</p>
      </div>
      <input
        type="text"
        value={name}
        onChange={e => setName(e.target.value)}
        placeholder="예: 36기"
        className="w-full px-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-amber-200 focus:border-amber-400 outline-none"
      />
    </div>
  )
}

/* ─── Step 2: 멤버 분류 ─── */
const CLASS_OPTIONS = [
  { value: 'continue', label: '계속', color: 'bg-green-100 text-green-700' },
  { value: 'alumni', label: '졸업', color: 'bg-blue-100 text-blue-700' },
  { value: 'inactive', label: '탈퇴', color: 'bg-red-100 text-red-700' },
]

function Step2_Members({ members, classification, setClassification }) {
  const counts = {
    continue: Object.values(classification).filter(v => v === 'continue').length,
    alumni: Object.values(classification).filter(v => v === 'alumni').length,
    inactive: Object.values(classification).filter(v => v === 'inactive').length,
  }

  const setAll = (value) => {
    const updated = {}
    members.forEach(m => { updated[m.membershipId] = value })
    setClassification(updated)
  }

  return (
    <div className="space-y-4">
      <div>
        <h4 className="text-base font-semibold text-slate-900">멤버 분류</h4>
        <p className="text-sm text-slate-500 mt-1">각 멤버의 다음 기수 상태를 선택하세요.</p>
      </div>

      {/* 요약 + 일괄 버튼 */}
      <div className="flex items-center gap-3 flex-wrap">
        <span className="text-xs text-slate-500">일괄:</span>
        {CLASS_OPTIONS.map(opt => (
          <button
            key={opt.value}
            onClick={() => setAll(opt.value)}
            className={`text-xs px-2.5 py-1 rounded-full cursor-pointer ${opt.color} font-medium hover:opacity-80`}
          >
            전체 {opt.label}
          </button>
        ))}
        <div className="flex-1" />
        <span className="text-xs text-slate-400">
          계속 {counts.continue} · 졸업 {counts.alumni} · 탈퇴 {counts.inactive}
        </span>
      </div>

      {/* 멤버 목록 */}
      <div className="border border-slate-200 rounded-xl overflow-hidden max-h-[40vh] overflow-y-auto">
        {members.map(member => (
          <div key={member.membershipId} className="flex items-center gap-3 px-4 py-2.5 border-b border-slate-100 last:border-b-0 hover:bg-slate-50">
            <div className="shrink-0 w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-600 text-xs font-bold">
              {member.name?.charAt(0) || '?'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-slate-900 truncate">{member.name}</p>
              {member.firstGeneration && (
                <p className="text-xs text-slate-400">{member.firstGeneration}</p>
              )}
            </div>
            <div className="flex gap-1">
              {CLASS_OPTIONS.map(opt => (
                <button
                  key={opt.value}
                  onClick={() => setClassification(prev => ({ ...prev, [member.membershipId]: opt.value }))}
                  className={`text-xs px-2.5 py-1 rounded-full cursor-pointer transition-colors ${
                    classification[member.membershipId] === opt.value
                      ? opt.color + ' font-semibold ring-1 ring-current/20'
                      : 'bg-slate-100 text-slate-400 hover:bg-slate-200'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
        ))}
        {members.length === 0 && (
          <div className="py-8 text-center text-sm text-slate-400">활성 멤버가 없습니다</div>
        )}
      </div>
    </div>
  )
}

/* ─── Step 3: 팀 처리 ─── */
function Step3_Teams({ teams, teamAction, setTeamAction }) {
  if (teams.length === 0) {
    return (
      <div className="space-y-4">
        <h4 className="text-base font-semibold text-slate-900">팀 처리</h4>
        <p className="text-sm text-slate-400 text-center py-8">등록된 팀이 없습니다. 다음 단계로 진행하세요.</p>
      </div>
    )
  }

  const retainCount = Object.values(teamAction).filter(v => v === 'retain').length
  const dissolveCount = Object.values(teamAction).filter(v => v === 'dissolve').length

  return (
    <div className="space-y-4">
      <div>
        <h4 className="text-base font-semibold text-slate-900">팀 처리</h4>
        <p className="text-sm text-slate-500 mt-1">유지하는 팀은 새 기수 태그가 추가되고, 해체 팀은 비활성화됩니다.</p>
      </div>

      <div className="text-xs text-slate-400 text-right">
        유지 {retainCount} · 해체 {dissolveCount}
      </div>

      <div className="border border-slate-200 rounded-xl overflow-hidden max-h-[40vh] overflow-y-auto">
        {teams.map(team => (
          <div key={team.id} className="flex items-center gap-3 px-4 py-3 border-b border-slate-100 last:border-b-0 hover:bg-slate-50">
            <div className="shrink-0 w-8 h-8 rounded-lg bg-blue-50 flex items-center justify-center text-blue-600 text-xs font-bold">
              👥
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-slate-900 truncate">{team.name}</p>
              {team.memberCount != null && (
                <p className="text-xs text-slate-400">{team.memberCount}명</p>
              )}
            </div>
            <div className="flex gap-1">
              <button
                onClick={() => setTeamAction(prev => ({ ...prev, [team.id]: 'retain' }))}
                className={`text-xs px-3 py-1 rounded-full cursor-pointer transition-colors ${
                  teamAction[team.id] === 'retain'
                    ? 'bg-green-100 text-green-700 font-semibold ring-1 ring-green-200'
                    : 'bg-slate-100 text-slate-400 hover:bg-slate-200'
                }`}
              >
                유지
              </button>
              <button
                onClick={() => setTeamAction(prev => ({ ...prev, [team.id]: 'dissolve' }))}
                className={`text-xs px-3 py-1 rounded-full cursor-pointer transition-colors ${
                  teamAction[team.id] === 'dissolve'
                    ? 'bg-red-100 text-red-700 font-semibold ring-1 ring-red-200'
                    : 'bg-slate-100 text-slate-400 hover:bg-slate-200'
                }`}
              >
                해체
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

/* ─── Step 4: 역할 초기화 확인 ─── */
function Step4_RoleReset({ alumniCount, inactiveCount, continueCount }) {
  return (
    <div className="space-y-5">
      <div>
        <h4 className="text-base font-semibold text-slate-900">역할 초기화 확인</h4>
        <p className="text-sm text-slate-500 mt-1">기수 전환 시 다음이 자동으로 수행됩니다.</p>
      </div>

      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 space-y-3">
        <div className="flex items-start gap-2">
          <span className="text-amber-500 mt-0.5">⚠️</span>
          <div className="text-sm text-amber-800">
            <p className="font-semibold mb-2">자동 처리 사항</p>
            <ul className="space-y-1.5 text-sm">
              <li>• 모든 임원(회장, 부회장 등) 직급이 <strong>일반부원</strong>으로 초기화</li>
              <li>• 졸업 처리 멤버({alumniCount}명)는 <strong>졸업자 팀</strong>에 자동 배정</li>
              <li>• 탈퇴 처리 멤버({inactiveCount}명)는 <strong>비활성화</strong></li>
              <li>• 계속 활동 멤버({continueCount}명)의 팀 소속은 유지</li>
              <li>• 해체 팀의 팀장/팀원 태그 제거</li>
            </ul>
          </div>
        </div>
      </div>

      <div className="bg-slate-50 rounded-xl p-4">
        <p className="text-xs text-slate-500">
          다음 단계에서 차기 회장을 지정합니다. 기수 전환 실행 후에는 되돌릴 수 없으니 신중히 진행해주세요.
        </p>
      </div>
    </div>
  )
}

/* ─── Step 5: 차기 회장 지정 ─── */
function Step5_President({ members, continueMemberIds, nextPresidentId, setNextPresidentId }) {
  const eligibleMembers = members.filter(m => continueMemberIds.includes(m.membershipId))

  return (
    <div className="space-y-4">
      <div>
        <h4 className="text-base font-semibold text-slate-900">차기 회장 지정</h4>
        <p className="text-sm text-slate-500 mt-1">계속 활동 멤버 중 차기 회장을 선택하세요.</p>
      </div>

      <div className="border border-slate-200 rounded-xl overflow-hidden max-h-[40vh] overflow-y-auto">
        {eligibleMembers.map(member => (
          <button
            key={member.membershipId}
            onClick={() => setNextPresidentId(member.membershipId)}
            className={`w-full flex items-center gap-3 px-4 py-3 border-b border-slate-100 last:border-b-0 transition-colors cursor-pointer ${
              nextPresidentId === member.membershipId
                ? 'bg-amber-50 ring-1 ring-inset ring-amber-300'
                : 'hover:bg-slate-50'
            }`}
          >
            <div className={`shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${
              nextPresidentId === member.membershipId
                ? 'bg-amber-200 text-amber-800'
                : 'bg-indigo-100 text-indigo-600'
            }`}>
              {member.name?.charAt(0) || '?'}
            </div>
            <div className="flex-1 text-left min-w-0">
              <p className="text-sm font-medium text-slate-900 truncate">{member.name}</p>
            </div>
            {nextPresidentId === member.membershipId && (
              <span className="text-xs px-2 py-0.5 bg-amber-200 text-amber-800 rounded-full font-semibold">회장</span>
            )}
          </button>
        ))}
        {eligibleMembers.length === 0 && (
          <div className="py-8 text-center text-sm text-slate-400">계속 활동으로 분류된 멤버가 없습니다</div>
        )}
      </div>
    </div>
  )
}

/* ─── 완료 화면 ─── */
function StepComplete({ result }) {
  return (
    <div className="text-center space-y-5 py-4">
      <div className="w-16 h-16 rounded-full bg-green-100 flex items-center justify-center mx-auto">
        <svg className="w-8 h-8 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
        </svg>
      </div>
      <div>
        <h4 className="text-lg font-bold text-slate-900">기수 전환 완료</h4>
        <p className="text-sm text-slate-500 mt-1">{result.newGenerationName} 기수가 시작되었습니다.</p>
      </div>
      <div className="bg-slate-50 rounded-xl p-4 text-left text-sm text-slate-600 space-y-1">
        <p>계속 활동: <strong>{result.continuedMembers}명</strong></p>
        <p>졸업 처리: <strong>{result.alumniMembers}명</strong></p>
        <p>탈퇴 처리: <strong>{result.inactiveMembers}명</strong></p>
        <p>팀 유지: <strong>{result.retainedTeams}개</strong></p>
        <p>팀 해체: <strong>{result.dissolvedTeams}개</strong></p>
        {result.nextPresidentName && (
          <p>차기 회장: <strong>{result.nextPresidentName}</strong></p>
        )}
      </div>
    </div>
  )
}
