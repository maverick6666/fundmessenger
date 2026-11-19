import { useState, useEffect, useCallback, useMemo } from 'react'
import { useOutletContext, Navigate } from 'react-router-dom'
import { DndContext, useDraggable, useDroppable, DragOverlay, pointerWithin } from '@dnd-kit/core'
import { MessageSquare, Pencil } from 'lucide-react'
import api from '../lib/api'
import DashboardTab from '../components/DashboardTab'
import SettingsTab from '../components/SettingsTab'
import GenerationManager from '../components/GenerationManager'
import ConfirmModal from '../components/ConfirmModal'
import useConfirm from '../hooks/useConfirm'
import useMessagingStore from '../store/messagingStore'
import useClubStore from '../store/clubStore'
import handleApiError from '../lib/handleApiError'

const TEAM_COLORS = [
  { border: 'border-blue-300', bg: 'bg-blue-50/60', text: 'text-blue-700', badge: 'bg-blue-100 text-blue-700', dot: 'bg-blue-400', hoverBg: 'bg-blue-100/80', folderL: 'border-l-blue-400/50', folderBg: 'bg-blue-50/30' },
  { border: 'border-emerald-300', bg: 'bg-emerald-50/60', text: 'text-emerald-700', badge: 'bg-emerald-100 text-emerald-700', dot: 'bg-emerald-400', hoverBg: 'bg-emerald-100/80', folderL: 'border-l-emerald-400/50', folderBg: 'bg-emerald-50/30' },
  { border: 'border-orange-300', bg: 'bg-orange-50/60', text: 'text-orange-700', badge: 'bg-orange-100 text-orange-700', dot: 'bg-orange-400', hoverBg: 'bg-orange-100/80', folderL: 'border-l-orange-400/50', folderBg: 'bg-orange-50/30' },
  { border: 'border-purple-300', bg: 'bg-purple-50/60', text: 'text-purple-700', badge: 'bg-purple-100 text-purple-700', dot: 'bg-purple-400', hoverBg: 'bg-purple-100/80', folderL: 'border-l-purple-400/50', folderBg: 'bg-purple-50/30' },
  { border: 'border-rose-300', bg: 'bg-rose-50/60', text: 'text-rose-700', badge: 'bg-rose-100 text-rose-700', dot: 'bg-rose-400', hoverBg: 'bg-rose-100/80', folderL: 'border-l-rose-400/50', folderBg: 'bg-rose-50/30' },
  { border: 'border-teal-300', bg: 'bg-teal-50/60', text: 'text-teal-700', badge: 'bg-teal-100 text-teal-700', dot: 'bg-teal-400', hoverBg: 'bg-teal-100/80', folderL: 'border-l-teal-400/50', folderBg: 'bg-teal-50/30' },
  { border: 'border-amber-300', bg: 'bg-amber-50/60', text: 'text-amber-700', badge: 'bg-amber-100 text-amber-700', dot: 'bg-amber-400', hoverBg: 'bg-amber-100/80', folderL: 'border-l-amber-400/50', folderBg: 'bg-amber-50/30' },
  { border: 'border-indigo-300', bg: 'bg-indigo-50/60', text: 'text-indigo-700', badge: 'bg-indigo-100 text-indigo-700', dot: 'bg-indigo-400', hoverBg: 'bg-indigo-100/80', folderL: 'border-l-indigo-400/50', folderBg: 'bg-indigo-50/30' },
]

// ========== 메인 페이지 ==========

export default function ClubAdminPage() {
  const { clubInfo } = useOutletContext()
  const [tab, setTab] = useState('dashboard')

  // 비권한 사용자 즉시 차단 (렌더 전)
  if (!clubInfo) return null
  if (!clubInfo.isExecutive) {
    return <Navigate to="/club" replace />
  }

  return (
    <div>
      <div className="flex gap-1 mb-6 flex-wrap">
        {[
          { key: 'dashboard', label: '대시보드' },
          { key: 'manage', label: '팀관리' },
          { key: 'operations', label: '운영 설정' },
        ].map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors cursor-pointer ${
              tab === t.key
                ? 'bg-primary text-white'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'dashboard' && <DashboardTab clubId={clubInfo.clubId} />}
      {tab === 'manage' && <ManageTab clubId={clubInfo.clubId} />}
      {tab === 'operations' && <OperationsTab clubId={clubInfo.clubId} />}
    </div>
  )
}

// ========== 관리 탭 (통합 멤버+팀 뷰) ==========

function ManageTab({ clubId }) {
  const { confirm, modal } = useConfirm()
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })

  // — 데이터 —
  const [members, setMembers] = useState([])
  const [teams, setTeams] = useState([])
  const [joinRequests, setJoinRequests] = useState([])
  const [loading, setLoading] = useState(true)

  // — 회장단 캐러셀 —
  const [carouselMembers, setCarouselMembers] = useState([])
  const [carouselCollapsed, setCarouselCollapsed] = useState(false)
  const [carouselAddOpen, setCarouselAddOpen] = useState(false)
  const [carouselAddRole, setCarouselAddRole] = useState('멤버')
  const [carouselAddMembershipId, setCarouselAddMembershipId] = useState('')
  const [editingCarouselId, setEditingCarouselId] = useState(null)
  const [editCarouselRole, setEditCarouselRole] = useState('')

  // — 팀 인라인 관리 —
  const [selectedTeamId, setSelectedTeamId] = useState(null)

  // — 팀 생성 —
  const [creating, setCreating] = useState(false)
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [newTeamType, setNewTeamType] = useState('CUSTOM')

  // — 액션 로딩 —
  const [actionLoading, setActionLoading] = useState(null)

  // — 선택 모드 (null | 'appointLeader' | 'deactivate' | 'delete') —
  const [selectionMode, setSelectionMode] = useState(null)
  const [checkedIds, setCheckedIds] = useState(new Set())

  // — 팀명 인라인 수정 —
  const [editingTeamName, setEditingTeamName] = useState(false)
  const [editTeamNameValue, setEditTeamNameValue] = useState('')

  // — 팀 해체 모드 —
  const [dissolveMode, setDissolveMode] = useState(false)
  const [checkedTeamIds, setCheckedTeamIds] = useState(new Set())

  // — 펀드팀 지정 모드 —
  const [fundMode, setFundMode] = useState(false)
  const [fundCheckedIds, setFundCheckedIds] = useState(new Set())

  // — 팀 카드 접기/펼치기 —
  const [collapsedTeams, setCollapsedTeams] = useState(() => {
    try { return JSON.parse(localStorage.getItem('admin-team-collapsed') || '{}') } catch { return {} }
  })

  // — DnD —
  const [activeDragMember, setActiveDragMember] = useState(null)
  const [activeDragCount, setActiveDragCount] = useState(1)

  // ===== 데이터 fetch =====

  // tags에서 teams 배열 파생 (하위 호환)
  const enrichMembers = (membersData) => membersData.map(m => ({
    ...m,
    teams: (m.tags || [])
      .filter(t => t.tagType === 'TEAM_LEADER' || t.tagType === 'TEAM_MEMBER')
      .map(t => ({ teamId: t.teamId, teamName: t.teamName, teamRole: t.tagType === 'TEAM_LEADER' ? 'LEADER' : 'MEMBER', tagId: t.tagId })),
  }))

  const fetchCarousel = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/admin/carousel`)
      setCarouselMembers(data || [])
    } catch { /* 권한 없으면 무시 */ }
  }

  const fetchAll = async () => {
    setLoading(true)
    try {
      const [membersData, teamsData, requestsData] = await Promise.all([
        api.get(`/clubs/${clubId}/members`),
        api.get(`/clubs/${clubId}/teams`),
        api.get(`/clubs/${clubId}/admin/join-requests`),
      ])
      setMembers(enrichMembers(membersData))
      setTeams(teamsData)
      setJoinRequests(requestsData)
      fetchCarousel()
    } catch (err) {
      handleApiError(err, '관리자 데이터 조회')
    } finally {
      setLoading(false)
    }
  }

  const refreshData = async () => {
    try {
      const [membersData, teamsData] = await Promise.all([
        api.get(`/clubs/${clubId}/members`),
        api.get(`/clubs/${clubId}/teams`),
      ])
      setMembers(enrichMembers(membersData))
      setTeams(teamsData)
      fetchCarousel()
    } catch (err) {
      handleApiError(err, '데이터 새로고침')
    }
  }

  useEffect(() => { fetchAll() }, [clubId])

  // ===== 전체보기 데이터 그룹핑 =====

  const teamGroups = useMemo(() => {
    return teams.map((team, idx) => ({
      ...team,
      color: TEAM_COLORS[idx % TEAM_COLORS.length],
      members: members
        .filter(m => m.teams?.some(t => t.teamId === team.id))
        .map(m => ({
          ...m,
          teamInfo: m.teams.find(t => t.teamId === team.id),
        }))
        .sort((a, b) => {
          const aLeader = a.teamInfo?.teamRole === 'LEADER' ? 0 : 1
          const bLeader = b.teamInfo?.teamRole === 'LEADER' ? 0 : 1
          return aLeader - bLeader
        }),
    }))
  }, [teams, members])

  const unassignedMembers = useMemo(() => {
    return members.filter(m => !m.teams || m.teams.length === 0)
  }, [members])


  // ===== 핸들러: 체크박스 =====

  const toggleCheck = (membershipId) => {
    setCheckedIds(prev => {
      const next = new Set(prev)
      if (next.has(membershipId)) next.delete(membershipId)
      else next.add(membershipId)
      return next
    })
  }

  const clearChecks = () => { setCheckedIds(new Set()); setSelectionMode(null) }

  const enterSelectionMode = (mode) => {
    setSelectionMode(mode)
    setCheckedIds(new Set())
  }

  // ===== 핸들러: 가입 승인/거절 =====

  const handleApprove = async (membershipId) => {
    setActionLoading(membershipId)
    try {
      await api.put(`/clubs/${clubId}/admin/join-requests/${membershipId}/approve`)
      setJoinRequests(prev => prev.filter(r => r.membershipId !== membershipId))
      const membersData = await api.get(`/clubs/${clubId}/members`)
      setMembers(enrichMembers(membersData))
    } catch (err) {
      setAlertModal({ open: true, message: '승인 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setActionLoading(null)
    }
  }

  const handleReject = async (membershipId, name) => {
    const ok = await confirm(`${name}의 가입 요청을 거절하시겠습니까?`, { confirmLabel: '거절', danger: true })
    if (!ok) return
    setActionLoading(membershipId)
    try {
      await api.put(`/clubs/${clubId}/admin/join-requests/${membershipId}/reject`)
      setJoinRequests(prev => prev.filter(r => r.membershipId !== membershipId))
    } catch (err) {
      setAlertModal({ open: true, message: '거절 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setActionLoading(null)
    }
  }

  // ===== 핸들러: 팀 생성/삭제 =====

  const TEAM_TYPE_OPTIONS = [
    { value: 'CUSTOM', label: '일반 팀' },
    { value: 'FUND', label: '펀드팀' },
  ]

  const handleCreateTeam = async (teamType = newTeamType) => {
    setCreating(true)
    try {
      const defaultName = TEAM_TYPE_OPTIONS.find(o => o.value === teamType)?.label || '새 팀'
      await api.post(`/clubs/${clubId}/admin/teams`, { name: defaultName, teamType })
      setShowCreateForm(false)
      setNewTeamType('CUSTOM')
      await refreshData()
    } catch (err) {
      setAlertModal({ open: true, message: '팀 생성 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setCreating(false)
    }
  }

  const handleDissolveTeams = async () => {
    if (checkedTeamIds.size === 0) return
    const teamNames = teams.filter(t => checkedTeamIds.has(t.id)).map(t => t.name)
    const ok = await confirm(
      `${teamNames.join(', ')} 팀을 해체하시겠습니까?\n팀원 배정이 모두 해제되고, 콘텐츠는 읽기전용으로 보존됩니다.`,
      { confirmLabel: '해체', danger: true }
    )
    if (!ok) return
    try {
      const errors = []
      for (const teamId of checkedTeamIds) {
        try {
          await api.delete(`/clubs/${clubId}/admin/teams/${teamId}`)
        } catch (err) {
          errors.push(err.message || `팀 ${teamId} 해체 실패`)
        }
      }
      setCheckedTeamIds(new Set())
      setDissolveMode(false)
      await refreshData()
      if (errors.length > 0) {
        setAlertModal({ open: true, message: errors.join('\n') })
      }
    } catch (err) {
      setAlertModal({ open: true, message: '해체 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  const toggleTeamCheck = (teamId) => {
    setCheckedTeamIds(prev => {
      const next = new Set(prev)
      if (next.has(teamId)) next.delete(teamId)
      else next.add(teamId)
      return next
    })
  }

  const toggleFundCheck = (teamId) => {
    setFundCheckedIds(prev => {
      const next = new Set(prev)
      if (next.has(teamId)) next.delete(teamId)
      else next.add(teamId)
      return next
    })
  }

  const handleApplyFundTeams = async () => {
    if (fundCheckedIds.size === 0) return
    try {
      for (const teamId of fundCheckedIds) {
        const team = teams.find(t => t.id === teamId)
        const isFund = team?.teamType === 'FUND'
        await api.put(`/clubs/${clubId}/admin/teams/${teamId}/fund`, { fund: !isFund })
      }
      setFundCheckedIds(new Set())
      setFundMode(false)
      await refreshData()
    } catch (err) {
      setAlertModal({ open: true, message: '펀드팀 지정 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  const handleRemoveLeader = async (teamId) => {
    if (!teamId) return
    const ok = await confirm('팀장을 해제하시겠습니까? 팀장이 팀원으로 전환됩니다.', { confirmLabel: '해제', danger: true })
    if (!ok) return
    try {
      await api.delete(`/clubs/${clubId}/admin/teams/${teamId}/leader`)
      await refreshData()
    } catch (err) {
      setAlertModal({ open: true, message: '팀장 해제 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  // ===== 핸들러: 팀명 인라인 수정 =====

  const handleStartEditTeamName = (currentName) => {
    setEditTeamNameValue(currentName || '')
    setEditingTeamName(true)
  }

  const handleSaveTeamName = async () => {
    const trimmed = editTeamNameValue.trim()
    if (!trimmed || !selectedTeamId) {
      setEditingTeamName(false)
      return
    }
    // 팀명이 변경되지 않았으면 무시
    const team = teams.find(t => t.id === selectedTeamId)
    if (trimmed === team?.name) {
      setEditingTeamName(false)
      return
    }
    try {
      await api.put(`/clubs/${clubId}/admin/teams/${selectedTeamId}/name`, { name: trimmed })
      setEditingTeamName(false)
      await refreshData()
    } catch (err) {
      setAlertModal({ open: true, message: '팀명 수정 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  // ===== 핸들러: 멤버 비활성화 =====

  const handleDeactivateMembers = async () => {
    const ids = [...checkedIds]
    if (ids.length === 0) return
    const ok = await confirm(
      `선택한 ${ids.length}명을 비활성화하시겠습니까? 모든 태그가 제거되고 INACTIVE 상태로 전환됩니다.`,
      { confirmLabel: '비활성화', variant: 'danger' }
    )
    if (!ok) return
    try {
      for (const id of ids) {
        await api.put(`/clubs/${clubId}/admin/members/${id}/deactivate`)
      }
      setCheckedIds(new Set())
      await refreshData()
    } catch (err) {
      setAlertModal({ open: true, message: '비활성화 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  // ===== 핸들러: 멤버 삭제 =====

  const handleDeleteMembers = async () => {
    const ids = [...checkedIds]
    if (ids.length === 0) return
    const ok = await confirm(
      `선택한 ${ids.length}명을 완전히 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`,
      { confirmLabel: '삭제', variant: 'danger' }
    )
    if (!ok) return
    try {
      for (const id of ids) {
        await api.delete(`/clubs/${clubId}/admin/members/${id}`)
      }
      setCheckedIds(new Set())
      await refreshData()
    } catch (err) {
      setAlertModal({ open: true, message: '삭제 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  // ===== 핸들러: 팀장으로 임명 (팀 자동 생성) =====

  const handleAppointLeader = async () => {
    const ids = [...checkedIds]
    if (ids.length !== 1) return
    const membershipId = ids[0]
    const member = members.find(m => m.membershipId === membershipId)
    if (!member) return

    const ok = await confirm(
      `${member.name}을(를) 팀장으로 임명하고 새 팀을 생성합니다. 동일 이름의 팀이 있으면 자동 번호가 붙습니다.`,
      { confirmLabel: '임명' }
    )
    if (!ok) return

    setCreating(true)
    try {
      await api.post(`/clubs/${clubId}/admin/teams`, {
        name: `${member.name} 팀`,
        leaderMembershipId: membershipId,
      })
      setCheckedIds(new Set())
      await refreshData()
    } catch (err) {
      setAlertModal({ open: true, message: '팀 생성 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setCreating(false)
    }
  }

  // ===== 핸들러: 기존 팀의 팀원을 팀장으로 변경 =====

  const handlePromoteToLeader = async (teamId, membershipId, memberName) => {
    if (!teamId) return
    const ok = await confirm(
      `${memberName}을(를) 이 팀의 팀장으로 임명하시겠습니까? 기존 팀장은 팀원으로 전환됩니다.`,
      { confirmLabel: '임명' }
    )
    if (!ok) return
    try {
      await api.put(`/clubs/${clubId}/admin/teams/${teamId}/leader`, {
        clubMembershipId: membershipId,
      })
      await refreshData()
    } catch (err) {
      setAlertModal({ open: true, message: '팀장 변경 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  // ===== 핸들러: 팀원 제거 (전체보기 — tagId 사용) =====

  const handleRemoveFromTeam = async (teamId, tagId, name) => {
    const ok = await confirm(`${name}을(를) 팀에서 제거하시겠습니까?`, { confirmLabel: '제거', danger: true })
    if (!ok) return
    try {
      await api.delete(`/clubs/${clubId}/admin/teams/${teamId}/members/${tagId}`)
      await refreshData()
    } catch (err) {
      setAlertModal({ open: true, message: '제거 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  // ===== DnD 통합 핸들러 =====

  const handleDragStart = (event) => {
    const idStr = String(event.active.id)
    let membershipId

    if (idStr.startsWith('fv:')) {
      // 전체보기: "fv:{membershipId}:{sourceTeamId|0}"
      membershipId = Number(idStr.split(':')[1])
    } else if (idStr.startsWith('tv:')) {
      // 팀뷰: "tv:{membershipId}"
      membershipId = Number(idStr.split(':')[1])
    } else if (idStr.startsWith('fc:')) {
      // 회장단: "fc:{membershipId}:{carouselId}"
      membershipId = Number(idStr.split(':')[1])
    } else {
      return
    }

    const member = members.find(m => m.membershipId === membershipId)
    setActiveDragMember(member || null)

    // 체크된 멤버 중 하나를 드래그하면 배치 드래그
    if (checkedIds.has(membershipId) && checkedIds.size > 1) {
      setActiveDragCount(checkedIds.size)
    } else {
      setActiveDragCount(1)
    }
  }

  const handleDragEnd = async (event) => {
    const dragMember = activeDragMember
    const dragCount = activeDragCount
    setActiveDragMember(null)
    setActiveDragCount(1)

    const { active, over } = event
    if (!over || !dragMember) return

    const activeIdStr = String(active.id)
    const overIdStr = String(over.id)
    const membershipId = Number(activeIdStr.split(':')[1])

    // 배치 대상: 체크된 멤버 포함 or 단일
    const idsToMove = (checkedIds.has(membershipId) && checkedIds.size > 0)
      ? [...checkedIds]
      : [membershipId]

    // === 회장단 캐러셀 DnD ===
    if (overIdStr === 'fv-carousel') {
      // 회장단에 드롭 → 캐러셀 멤버 추가
      const existingIds = new Set(carouselMembers.map(cm => cm.id))
      const idsToAdd = idsToMove.filter(id => !existingIds.has(id))
      if (idsToAdd.length > 0) {
        try {
          for (const id of idsToAdd) {
            await api.post(`/clubs/${clubId}/admin/carousel`, {
              membershipId: id,
              roleName: '멤버',
            })
          }
          setCheckedIds(new Set())
          await fetchCarousel()
        } catch (err) {
          setAlertModal({ open: true, message: '회장단 추가 실패: ' + (err.message || '알 수 없는 오류') })
        }
      }
      return
    }

    // 회장단에서 밖으로 드래그 → 캐러셀에서 제거
    if (activeIdStr.startsWith('fc:') && (overIdStr === 'fv-unassigned' || overIdStr === 'member-source-panel' || overIdStr.startsWith('fv-team:'))) {
      const carouselId = Number(activeIdStr.split(':')[2])
      if (carouselId) {
        try {
          await api.delete(`/clubs/${clubId}/admin/carousel/${carouselId}`)
          await fetchCarousel()
        } catch (err) {
          setAlertModal({ open: true, message: '회장단 제거 실패: ' + (err.message || '알 수 없는 오류') })
        }
      }
      // 다른 팀에 드롭된 경우 해당 팀에도 추가
      if (overIdStr.startsWith('fv-team:')) {
        const targetTeamId = Number(overIdStr.replace('fv-team:', ''))
        const m = members.find(mm => mm.membershipId === membershipId)
        if (m && !m.teams?.some(t => t.teamId === targetTeamId)) {
          try {
            await api.post(`/clubs/${clubId}/admin/teams/${targetTeamId}/members`, {
              clubMembershipIds: [membershipId],
            })
            await refreshData()
          } catch {}
        }
      }
      return
    }

    // === 전체보기 DnD ===
    if (overIdStr.startsWith('fv-team:')) {
      // 팀 그룹에 드롭 → 추가
      const targetTeamId = Number(overIdStr.replace('fv-team:', ''))
      const idsToAdd = idsToMove.filter(id => {
        const m = members.find(mm => mm.membershipId === id)
        return !m?.teams?.some(t => t.teamId === targetTeamId)
      })

      if (idsToAdd.length > 0) {
        try {
          await api.post(`/clubs/${clubId}/admin/teams/${targetTeamId}/members`, {
            clubMembershipIds: idsToAdd,
          })
          setCheckedIds(new Set())
          await refreshData()
        } catch (err) {
          setAlertModal({ open: true, message: '멤버 추가 실패: ' + (err.message || '알 수 없는 오류') })
        }
      }
    } else if (overIdStr === 'fv-unassigned' || overIdStr === 'member-source-panel') {
      // 미배정/우측 패널에 드롭 → 팀에서 제거
      const sourceTeamId = activeIdStr.startsWith('fv:') ? Number(activeIdStr.split(':')[2]) : null
      if (sourceTeamId && sourceTeamId > 0) {
        const member = members.find(m => m.membershipId === membershipId)
        const teamInfo = member?.teams?.find(t => t.teamId === sourceTeamId)
        if (teamInfo?.tagId) {
          try {
            await api.delete(`/clubs/${clubId}/admin/teams/${sourceTeamId}/members/${teamInfo.tagId}`)
            await refreshData()
          } catch (err) {
            setAlertModal({ open: true, message: '팀 제거 실패: ' + (err.message || '알 수 없는 오류') })
          }
        }
      }
    } else if (overIdStr === 'new-team-drop') {
      // 새 팀 생성 드롭존
      try {
        await api.post(`/clubs/${clubId}/admin/teams`, {
          name: `${dragMember.name} 팀`,
          leaderMembershipId: membershipId,
        })
        setCheckedIds(new Set())
        await refreshData()
      } catch (err) {
        setAlertModal({ open: true, message: '팀 생성 실패: ' + (err.message || '알 수 없는 오류') })
      }
    }
  }

  const handleDragCancel = () => {
    setActiveDragMember(null)
    setActiveDragCount(1)
  }

  // ===== 핸들러: 회장단 캐러셀 =====

  const handleAddCarouselMember = async () => {
    if (!carouselAddMembershipId) return
    try {
      await api.post(`/clubs/${clubId}/admin/carousel`, {
        membershipId: Number(carouselAddMembershipId),
        roleName: carouselAddRole.trim() || '멤버',
      })
      setCarouselAddOpen(false)
      setCarouselAddMembershipId('')
      setCarouselAddRole('멤버')
      await fetchCarousel()
    } catch (err) {
      setAlertModal({ open: true, message: '회장단 추가 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  const handleRemoveCarouselMember = async (carouselId, name) => {
    const ok = await confirm(`${name}을(를) 회장단에서 제거하시겠습니까?`, { confirmLabel: '제거', danger: true })
    if (!ok) return
    try {
      await api.delete(`/clubs/${clubId}/admin/carousel/${carouselId}`)
      await fetchCarousel()
    } catch (err) {
      setAlertModal({ open: true, message: '제거 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  const handleSaveCarouselRole = async (carouselId) => {
    try {
      await api.put(`/clubs/${clubId}/admin/carousel/${carouselId}`, { roleName: editCarouselRole.trim() || '멤버' })
      setEditingCarouselId(null)
      await fetchCarousel()
    } catch (err) {
      setAlertModal({ open: true, message: '직책 수정 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  // 회장단에 추가 가능한 멤버 (이미 회장단에 없는 멤버)
  const carouselCandidates = useMemo(() => {
    const existingIds = new Set(carouselMembers.map(cm => cm.id))
    return members.filter(m => !existingIds.has(m.membershipId))
  }, [members, carouselMembers])

  const toggleTeamCollapsed = (teamId) => {
    setCollapsedTeams(prev => {
      const next = { ...prev, [teamId]: !prev[teamId] }
      try { localStorage.setItem('admin-team-collapsed', JSON.stringify(next)) } catch {}
      return next
    })
  }

  // ===== 로딩 =====

  if (loading) {
    return (
      <div className="py-12 text-center">
        <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full mx-auto" />
      </div>
    )
  }

  return (
    <DndContext
      collisionDetection={pointerWithin}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={handleDragCancel}
    >
      {modal}
      {alertModal.open && (
        <ConfirmModal title="오류" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
      <div className="flex gap-6">
        {/* ===== 좌측: 메인 영역 (팀 카드) ===== */}
        <div className="flex-[3] min-w-0 space-y-4">
          {/* 헤더 + 액션 */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h2 className="text-sm font-bold text-slate-900">팀 관리</h2>
              <span className="text-xs text-slate-400">{teams.length}개 팀 · {members.length}명</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setShowCreateForm(prev => !prev)}
                disabled={creating}
                className="text-xs text-primary hover:text-primary/80 cursor-pointer disabled:opacity-50"
              >
                {creating ? '생성 중...' : '+ 팀 추가'}
              </button>
              <button
                onClick={() => { setFundMode(prev => !prev); setFundCheckedIds(new Set()); if (dissolveMode) { setDissolveMode(false); setCheckedTeamIds(new Set()) } }}
                className={`text-xs cursor-pointer transition-colors ${
                  fundMode ? 'text-emerald-600 font-medium' : 'text-slate-400 hover:text-emerald-500'
                }`}
              >
                펀드팀 지정
              </button>
              <button
                onClick={() => { setDissolveMode(prev => !prev); setCheckedTeamIds(new Set()); if (fundMode) { setFundMode(false); setFundCheckedIds(new Set()) } }}
                className={`text-xs cursor-pointer transition-colors ${
                  dissolveMode ? 'text-red-500 font-medium' : 'text-slate-400 hover:text-red-400'
                }`}
              >
                팀 해체
              </button>
              <button onClick={fetchAll} className="text-xs text-primary hover:text-primary/80 cursor-pointer">
                새로고침
              </button>
            </div>
          </div>

          {/* 팀 생성 폼 */}
          {showCreateForm && (
            <div className="p-3 bg-slate-50 rounded-lg border border-slate-200 flex items-end gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">팀 유형</label>
                <select
                  value={newTeamType}
                  onChange={e => setNewTeamType(e.target.value)}
                  className="text-sm border border-slate-300 rounded-md px-2 py-1.5 bg-white focus:outline-none focus:ring-1 focus:ring-primary"
                >
                  {TEAM_TYPE_OPTIONS.map(opt => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>
              <button
                onClick={() => handleCreateTeam(newTeamType)}
                disabled={creating}
                className="text-xs bg-primary text-white px-4 py-1.5 rounded-md hover:bg-primary/90 disabled:opacity-50 cursor-pointer"
              >
                {creating ? '생성 중...' : '생성'}
              </button>
              <button
                onClick={() => { setShowCreateForm(false); setNewTeamType('CUSTOM') }}
                className="text-xs text-slate-500 hover:text-slate-700 cursor-pointer"
              >
                취소
              </button>
            </div>
          )}

          {/* 해체 모드 액션 */}
          {dissolveMode && (
            <div className="flex items-center gap-2 p-3 bg-red-50 rounded-lg border border-red-200">
              <span className="text-xs text-red-600">해체할 팀을 선택하세요</span>
              <div className="flex-1" />
              <button
                onClick={handleDissolveTeams}
                disabled={checkedTeamIds.size === 0}
                className="px-3 py-1.5 text-xs font-medium text-white bg-red-500 rounded-lg hover:bg-red-600 transition-colors cursor-pointer disabled:opacity-40"
              >
                {checkedTeamIds.size > 0 ? `${checkedTeamIds.size}개 팀 해체` : '팀 선택'}
              </button>
              <button
                onClick={() => { setDissolveMode(false); setCheckedTeamIds(new Set()) }}
                className="text-xs text-slate-500 hover:text-slate-700 cursor-pointer"
              >
                취소
              </button>
            </div>
          )}

          {/* 펀드팀 지정 모드 액션 */}
          {fundMode && (
            <div className="flex items-center gap-2 p-3 bg-emerald-50 rounded-lg border border-emerald-200">
              <span className="text-xs text-emerald-600">펀드팀으로 지정/해제할 팀을 선택하세요</span>
              <div className="flex-1" />
              <button
                onClick={handleApplyFundTeams}
                disabled={fundCheckedIds.size === 0}
                className="px-3 py-1.5 text-xs font-medium text-white bg-emerald-500 rounded-lg hover:bg-emerald-600 transition-colors cursor-pointer disabled:opacity-40"
              >
                {fundCheckedIds.size > 0 ? `${fundCheckedIds.size}개 팀 적용` : '팀 선택'}
              </button>
              <button
                onClick={() => { setFundMode(false); setFundCheckedIds(new Set()) }}
                className="text-xs text-slate-500 hover:text-slate-700 cursor-pointer"
              >
                취소
              </button>
            </div>
          )}

          {/* ===== 회장단 카드 (최상단 고정, DnD 드롭 영역) ===== */}
          <CarouselSection
            carouselMembers={carouselMembers}
            collapsed={carouselCollapsed}
            onToggleCollapse={() => setCarouselCollapsed(p => !p)}
            addOpen={carouselAddOpen}
            onToggleAdd={() => setCarouselAddOpen(p => !p)}
            addMembershipId={carouselAddMembershipId}
            setAddMembershipId={setCarouselAddMembershipId}
            addRole={carouselAddRole}
            setAddRole={setCarouselAddRole}
            candidates={carouselCandidates}
            onAdd={handleAddCarouselMember}
            onRemove={handleRemoveCarouselMember}
            editingId={editingCarouselId}
            editRole={editCarouselRole}
            setEditRole={setEditCarouselRole}
            onStartEdit={(id, role) => { setEditingCarouselId(id); setEditCarouselRole(role || '') }}
            onSaveRole={handleSaveCarouselRole}
            onCancelEdit={() => setEditingCarouselId(null)}
            dndDisabled={!!selectionMode || !!dissolveMode || !!fundMode}
          />

          {/* 팀 카드 목록 */}
          {teamGroups.length > 0 ? (
            <div className="space-y-3">
              {teamGroups.map(group => (
                <TeamGroupSection
                  key={group.id}
                  group={group}
                  checkedIds={checkedIds}
                  onCheck={toggleCheck}
                  onRemoveFromTeam={handleRemoveFromTeam}
                  collapsed={!!collapsedTeams[group.id]}
                  onToggleCollapse={() => toggleTeamCollapsed(group.id)}
                  dissolveMode={dissolveMode}
                  dissolveChecked={checkedTeamIds.has(group.id)}
                  onDissolveCheck={() => toggleTeamCheck(group.id)}
                  onPromoteToLeader={handlePromoteToLeader}
                  onRemoveLeader={handleRemoveLeader}
                  onStartEditTeamName={handleStartEditTeamName}
                  editingTeamName={editingTeamName}
                  editTeamNameValue={editTeamNameValue}
                  setEditTeamNameValue={setEditTeamNameValue}
                  onSaveTeamName={handleSaveTeamName}
                  setEditingTeamName={setEditingTeamName}
                  selectedTeamId={selectedTeamId}
                  setSelectedTeamId={setSelectedTeamId}
                  selectionMode={selectionMode}
                  fundMode={fundMode}
                  fundChecked={fundCheckedIds.has(group.id)}
                  onFundCheck={() => toggleFundCheck(group.id)}
                />
              ))}
            </div>
          ) : (
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
              <p className="text-sm text-slate-400">팀이 없습니다. 팀을 추가하세요.</p>
            </div>
          )}

          {/* 새 팀 생성 드롭 존 */}
          <NewTeamDropZone />
        </div>

        {/* ===== 우측: 인원 명단 패널 (전체가 드롭 영역) ===== */}
        <RightPanelDropZone className="flex-[2] min-w-0 space-y-4">
          {/* 멤버 액션 */}
          {selectionMode ? (
            <div className="p-3 bg-primary/5 rounded-lg border border-primary/20 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs text-primary font-medium">
                  {selectionMode === 'appointLeader' && '팀장 임명할 멤버 선택 (1명)'}
                  {selectionMode === 'deactivate' && '비활성화할 멤버 선택'}
                  {selectionMode === 'delete' && '삭제할 멤버 선택'}
                </span>
                <button onClick={clearChecks} className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer">취소</button>
              </div>
              {checkedIds.size > 0 && (
                <div className="flex items-center gap-2">
                  <span className="text-xs text-slate-600">{checkedIds.size}명 선택됨</span>
                  <button
                    onClick={() => {
                      if (selectionMode === 'appointLeader') handleAppointLeader()
                      else if (selectionMode === 'deactivate') handleDeactivateMembers()
                      else if (selectionMode === 'delete') handleDeleteMembers()
                    }}
                    disabled={selectionMode === 'appointLeader' && checkedIds.size !== 1}
                    className={`px-3 py-1 text-xs font-medium rounded cursor-pointer disabled:opacity-40 ${
                      selectionMode === 'delete' ? 'text-white bg-red-500 hover:bg-red-600' :
                      selectionMode === 'deactivate' ? 'text-amber-700 bg-amber-100 hover:bg-amber-200' :
                      'text-white bg-primary hover:bg-primary/90'
                    }`}
                  >
                    {selectionMode === 'appointLeader' && (creating ? '처리 중...' : '임명')}
                    {selectionMode === 'deactivate' && '비활성화'}
                    {selectionMode === 'delete' && '삭제'}
                  </button>
                </div>
              )}
            </div>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              <button
                onClick={() => enterSelectionMode('appointLeader')}
                className="px-2.5 py-1.5 text-xs font-medium text-primary bg-primary/5 border border-primary/20 rounded-lg hover:bg-primary/10 cursor-pointer"
              >
                팀장 임명
              </button>
              <button
                onClick={() => enterSelectionMode('deactivate')}
                className="px-2.5 py-1.5 text-xs font-medium text-amber-600 bg-amber-50 border border-amber-200 rounded-lg hover:bg-amber-100 cursor-pointer"
              >
                비활성화
              </button>
              <button
                onClick={() => enterSelectionMode('delete')}
                className="px-2.5 py-1.5 text-xs font-medium text-red-600 bg-red-50 border border-red-200 rounded-lg hover:bg-red-100 cursor-pointer"
              >
                삭제
              </button>
            </div>
          )}

          {/* 1. 가입 신청 대기 */}
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="text-xs font-semibold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-full">가입 대기</span>
              <span className="text-xs text-slate-400">{joinRequests.length}명</span>
            </div>
            {joinRequests.length > 0 ? (
              <div className="bg-white rounded-2xl border border-slate-200 shadow-sm divide-y divide-slate-50">
                {joinRequests.map(r => (
                  <div key={r.membershipId} className="flex items-center justify-between px-3 py-2.5">
                    <div className="flex items-center gap-2 min-w-0">
                      <div className="w-7 h-7 bg-blue-50 rounded-full flex items-center justify-center text-xs font-medium text-blue-600 shrink-0">
                        {r.name?.charAt(0)}
                      </div>
                      <div className="min-w-0">
                        <span className="text-sm font-medium text-slate-900 truncate block">{r.name}</span>
                        <span className="text-xs text-slate-400">{r.studentId}</span>
                      </div>
                    </div>
                    <div className="flex gap-1 shrink-0">
                      <button
                        onClick={() => handleApprove(r.membershipId)}
                        disabled={actionLoading === r.membershipId}
                        className="px-2 py-1 text-xs font-medium text-white bg-primary rounded hover:bg-primary/90 cursor-pointer disabled:opacity-50"
                      >
                        승인
                      </button>
                      <button
                        onClick={() => handleReject(r.membershipId, r.name)}
                        disabled={actionLoading === r.membershipId}
                        className="px-2 py-1 text-xs font-medium text-red-600 bg-red-50 rounded hover:bg-red-100 cursor-pointer disabled:opacity-50"
                      >
                        거절
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-3 text-center">
                <p className="text-xs text-slate-400">가입 요청 없음</p>
              </div>
            )}
          </div>

          {/* 2. 미소속/전체 멤버 명단 */}
          <MemberSourcePanel
            members={members}
            unassignedMembers={unassignedMembers}
            checkedIds={checkedIds}
            onCheck={toggleCheck}
            selectionMode={selectionMode}
            dissolveMode={dissolveMode}
            fundMode={fundMode}
          />
        </RightPanelDropZone>
      </div>

      {/* DnD 오버레이 */}
      <DragOverlay>
        {activeDragMember && (
          <div className="bg-white rounded-lg shadow-lg border border-primary/30 px-4 py-2.5 flex items-center gap-3 opacity-90">
            <div className="w-7 h-7 bg-primary/10 rounded-full flex items-center justify-center text-xs font-medium text-primary">
              {activeDragMember.name?.charAt(0)}
            </div>
            <span className="text-sm font-medium text-slate-900">{activeDragMember.name}</span>
            {activeDragCount > 1 && (
              <span className="text-xs px-1.5 py-0.5 bg-primary text-white rounded-full font-medium">+{activeDragCount - 1}</span>
            )}
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}

// ========== 팀 그룹 섹션 (접기/펼치기 + 인라인 관리) ==========

function TeamGroupSection({ group, checkedIds, onCheck, onRemoveFromTeam, collapsed, onToggleCollapse, dissolveMode, dissolveChecked, onDissolveCheck, onPromoteToLeader, onRemoveLeader, onStartEditTeamName, editingTeamName, editTeamNameValue, setEditTeamNameValue, onSaveTeamName, setEditingTeamName, selectedTeamId, setSelectedTeamId, selectionMode, fundMode, fundChecked, onFundCheck }) {
  const { isOver, setNodeRef } = useDroppable({ id: `fv-team:${group.id}` })

  return (
    <div
      ref={setNodeRef}
      className={`rounded-xl border-2 transition-all ${group.color.border} ${group.color.bg} ${
        isOver ? 'ring-2 ring-primary/30 scale-[1.01]' : ''
      }`}
    >
      {/* 팀 헤더 */}
      <div className="flex items-center gap-2 px-4 py-3">
        {fundMode && (
          <input
            type="checkbox"
            checked={fundChecked}
            onChange={onFundCheck}
            className="w-3.5 h-3.5 shrink-0 accent-emerald-500 cursor-pointer"
          />
        )}
        {dissolveMode && (
          <input
            type="checkbox"
            checked={dissolveChecked}
            onChange={onDissolveCheck}
            className="w-3.5 h-3.5 shrink-0 accent-red-500 cursor-pointer"
          />
        )}
        <button onClick={onToggleCollapse} className="cursor-pointer p-0.5 shrink-0">
          <svg className={`w-3 h-3 text-slate-400 transition-transform ${collapsed ? '' : 'rotate-90'}`} fill="currentColor" viewBox="0 0 20 20">
            <path d="M6 4l8 6-8 6V4z" />
          </svg>
        </button>
        <div className={`w-2.5 h-2.5 rounded-full shrink-0 ${group.color.dot}`} />
        {editingTeamName && selectedTeamId === group.id ? (
          <input
            autoFocus
            value={editTeamNameValue}
            onChange={e => setEditTeamNameValue(e.target.value)}
            onBlur={onSaveTeamName}
            onKeyDown={e => { if (e.key === 'Enter') onSaveTeamName(); if (e.key === 'Escape') setEditingTeamName(false) }}
            className={`text-sm font-bold border-b-2 border-primary outline-none bg-transparent ${group.color.text}`}
            onClick={e => e.stopPropagation()}
          />
        ) : (
          <>
            <span className={`text-sm font-bold ${group.color.text}`}>
              {group.name}
            </span>
            <button
              onClick={() => { setSelectedTeamId(group.id); onStartEditTeamName(group.name) }}
              className="p-0.5 rounded hover:bg-slate-100 transition-colors cursor-pointer"
              title="팀명 수정"
            >
              <Pencil className="w-3 h-3 text-slate-400" />
            </button>
          </>
        )}
        <span className="text-xs text-slate-400">({group.members.length}명)</span>
        {group.teamType && group.teamType !== 'CUSTOM' && (
          <span className="text-xs px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 shrink-0">
            {group.teamType === 'FUND' ? '펀드팀' : ''}
          </span>
        )}
        {group.leaderName && (
          <span className="text-xs text-slate-400">· 팀장: {group.leaderName}</span>
        )}
      </div>

      {/* 팀원 목록 (접힘 가능) */}
      {!collapsed && (
        <div className="px-2 pb-2">
          {group.members.length === 0 ? (
            <p className="text-xs text-slate-400 py-3 px-2">팀원이 없습니다. 멤버를 드래그하여 추가하세요.</p>
          ) : (
            <div className="grid grid-cols-1 gap-0.5">
              {group.members.map(m => (
                <FullViewMemberCard
                  key={`${group.id}-${m.membershipId}`}
                  member={m}
                  sourceTeamId={group.id}
                  checked={checkedIds.has(m.membershipId)}
                  onCheck={onCheck}
                  color={group.color}
                  isTeamLeader={m.teamInfo?.teamRole === 'LEADER'}
                  onRemove={() => onRemoveFromTeam(group.id, m.teamInfo?.tagId, m.name)}
                  onPromoteToLeader={() => onPromoteToLeader(group.id, m.membershipId, m.name)}
                  onRemoveLeader={() => onRemoveLeader(group.id)}
                  selectionMode={selectionMode}
                  dndDisabled={!!selectionMode || !!dissolveMode || !!fundMode}
                />
              ))}
            </div>
          )}

          {/* 드롭 힌트 */}
          {isOver && (
            <div className="mt-2 py-2 border-2 border-dashed border-primary/30 rounded-lg text-center">
              <p className="text-xs text-primary font-medium">여기에 놓아서 팀에 추가</p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ========== 우측 패널 — 멤버 소스 ==========

function MemberSourcePanel({ members, unassignedMembers, checkedIds, onCheck, selectionMode, dissolveMode, fundMode }) {
  const [filter, setFilter] = useState('unassigned')
  const [expanded, setExpanded] = useState(true)

  const displayMembers = filter === 'unassigned' ? unassignedMembers : members

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <button
          onClick={() => setExpanded(prev => !prev)}
          className="flex items-center gap-1.5 cursor-pointer"
        >
          <svg className={`w-3 h-3 text-slate-400 transition-transform ${expanded ? 'rotate-90' : ''}`} fill="currentColor" viewBox="0 0 20 20">
            <path d="M6 4l8 6-8 6V4z" />
          </svg>
          <span className="text-xs font-semibold text-slate-600">멤버 명단</span>
        </button>
        <div className="flex gap-1">
          <button
            onClick={() => setFilter('unassigned')}
            className={`text-xs px-2 py-0.5 rounded-full cursor-pointer transition-colors ${
              filter === 'unassigned'
                ? 'bg-primary/10 text-primary font-medium'
                : 'text-slate-400 hover:text-slate-600'
            }`}
          >
            미소속 ({unassignedMembers.length})
          </button>
          <button
            onClick={() => setFilter('all')}
            className={`text-xs px-2 py-0.5 rounded-full cursor-pointer transition-colors ${
              filter === 'all'
                ? 'bg-primary/10 text-primary font-medium'
                : 'text-slate-400 hover:text-slate-600'
            }`}
          >
            전체 ({members.length})
          </button>
        </div>
      </div>

      {expanded && (
        <div className="bg-white rounded-2xl border-2 border-slate-100">
          {displayMembers.length === 0 ? (
            <div className="p-4 text-center">
              <p className="text-xs text-slate-400">
                {filter === 'unassigned' ? '모든 멤버가 팀에 배정됨' : '멤버가 없습니다'}
              </p>
            </div>
          ) : (
            <div className="divide-y divide-slate-50 max-h-[calc(100vh-400px)] overflow-y-auto">
              {displayMembers.map(m => (
                <FullViewMemberCard
                  key={`panel-${m.membershipId}`}
                  member={m}
                  sourceTeamId={0}
                  checked={checkedIds.has(m.membershipId)}
                  onCheck={onCheck}
                  compact
                  selectionMode={selectionMode}
                  dndDisabled={!!selectionMode || !!dissolveMode || !!fundMode}
                />
              ))}
            </div>
          )}
        </div>
      )}

      <p className="text-xs text-slate-300 mt-1">드래그하여 팀에 추가</p>
    </div>
  )
}

// ========== 새 팀 생성 드롭존 ==========

function RightPanelDropZone({ children, className }) {
  const { isOver, setNodeRef } = useDroppable({ id: 'member-source-panel' })

  return (
    <div
      ref={setNodeRef}
      className={`${className} ${isOver ? 'ring-2 ring-orange-300 rounded-xl' : ''}`}
    >
      {children}
      {isOver && (
        <div className="py-2 border-2 border-dashed border-orange-300 rounded-lg text-center mt-2">
          <p className="text-xs text-orange-600 font-medium">놓아서 팀에서 제거</p>
        </div>
      )}
    </div>
  )
}

function NewTeamDropZone() {
  const { isOver, setNodeRef } = useDroppable({ id: 'new-team-drop' })

  return (
    <div
      ref={setNodeRef}
      className={`py-4 border-2 border-dashed rounded-xl text-center transition-all ${
        isOver
          ? 'border-primary bg-primary/5'
          : 'border-slate-200 hover:border-slate-300'
      }`}
    >
      <p className={`text-xs font-medium ${isOver ? 'text-primary' : 'text-slate-400'}`}>
        {isOver ? '놓아서 새 팀 생성 (팀장으로 임명)' : '여기에 드래그하여 새 팀 생성'}
      </p>
    </div>
  )
}


// ========== 멤버 카드 (드래그 가능 + 체크박스) ==========

function FullViewMemberCard({ member, sourceTeamId, checked, onCheck, color, isTeamLeader, onRemove, onPromoteToLeader, onRemoveLeader, compact, selectionMode, dndDisabled }) {
  const m = member
  const dragId = `fv:${m.membershipId}:${sourceTeamId}`
  const startDmWith = useMessagingStore(s => s.startDmWith)
  const clubInfo = useClubStore(s => s.clubInfo)

  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: dragId,
    disabled: !!dndDisabled,
  })

  const style = transform ? {
    transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
  } : undefined

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...(dndDisabled ? {} : { ...listeners, ...attributes })}
      className={`flex items-center justify-between px-3 py-2 rounded-lg transition-colors ${
        dndDisabled ? 'cursor-default' : 'cursor-grab active:cursor-grabbing'
      } ${
        isDragging ? 'opacity-30' : ''
      } ${
        checked ? 'bg-primary/5' : 'hover:bg-white/60'
      }`}
      onClick={selectionMode ? () => onCheck(m.membershipId) : undefined}
    >
      <div className="flex items-center gap-2 flex-1 min-w-0">
        {/* 체크박스 (선택 모드에서만 표시) */}
        {selectionMode && (
          <label className="flex items-center justify-center w-8 h-8 shrink-0 cursor-pointer">
            <input
              type="checkbox"
              checked={checked}
              onChange={() => onCheck(m.membershipId)}
              className="w-4 h-4 rounded border-slate-300 text-primary focus:ring-primary/20 cursor-pointer"
            />
          </label>
        )}

        {/* 아바타 */}
        <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium shrink-0 ${
          m.isExecutive
            ? 'bg-amber-50 text-amber-600'
            : 'bg-slate-100 text-slate-600'
        }`}>
          {m.name?.charAt(0)}
        </div>

        <div className="flex flex-wrap items-center gap-1 flex-1 min-w-0">
          <span className={`font-medium text-slate-900 shrink-0 ${compact ? 'text-sm' : 'text-sm'}`}>{m.name}</span>
          {!compact && <span className="text-xs text-slate-400 shrink-0">{m.studentId}</span>}

          {/* 팀 리더 뱃지 (해당 팀 내 역할만 표시) */}
          {isTeamLeader && color && (
            <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium shrink-0 ${color.badge}`}>
              ★ 리더
            </span>
          )}
        </div>
      </div>

      {/* 우측: 액션 버튼 */}
      <div className="flex items-center gap-1 shrink-0 ml-1">
        {!compact && m.membershipId !== clubInfo?.membershipId && (
          <button
            onClick={(e) => { e.stopPropagation(); startDmWith(clubInfo?.clubId, m.membershipId) }}
            onPointerDown={(e) => e.stopPropagation()}
            className="p-1 hover:bg-slate-100 rounded cursor-pointer"
            title="DM 보내기"
          >
            <MessageSquare className="w-3.5 h-3.5 text-slate-400" />
          </button>
        )}
        {isTeamLeader && onRemoveLeader && (
          <button
            onClick={(e) => { e.stopPropagation(); onRemoveLeader() }}
            onPointerDown={(e) => e.stopPropagation()}
            className="text-xs text-amber-500 hover:text-amber-700 cursor-pointer px-1"
            title="팀장 해제"
          >
            해제
          </button>
        )}
        {!isTeamLeader && onPromoteToLeader && (
          <button
            onClick={(e) => { e.stopPropagation(); onPromoteToLeader() }}
            onPointerDown={(e) => e.stopPropagation()}
            className="text-xs text-blue-500 hover:text-blue-700 cursor-pointer px-1"
            title="팀장 임명"
          >
            임명
          </button>
        )}
        {onRemove && (
          <button
            onClick={(e) => { e.stopPropagation(); onRemove() }}
            onPointerDown={(e) => e.stopPropagation()}
            className="text-xs text-slate-300 hover:text-red-500 cursor-pointer"
            title="팀에서 제거"
          >
            &times;
          </button>
        )}
      </div>
    </div>
  )
}

// ========== 권한관리 탭 (직책 CRUD) ==========

const WRITE_PERM_LABEL = {
  EVERYONE: '모든 멤버',
  EXECUTIVE_ONLY: '임원만',
}

// ========== 접기/펼치기 섹션 ==========

function CollapsibleSection({ id, title, count, children, defaultOpen = false }) {
  const storageKey = 'admin-sections'
  const [expanded, setExpanded] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(storageKey) || '{}')
      return saved[id] !== undefined ? saved[id] : defaultOpen
    } catch { return defaultOpen }
  })

  const toggle = () => {
    setExpanded(prev => {
      const next = !prev
      try {
        const saved = JSON.parse(localStorage.getItem(storageKey) || '{}')
        saved[id] = next
        localStorage.setItem(storageKey, JSON.stringify(saved))
      } catch {}
      return next
    })
  }

  return (
    <div className="border border-slate-200 shadow-sm rounded-xl bg-white overflow-hidden">
      <button
        onClick={toggle}
        className="w-full flex items-center gap-2 px-5 py-3.5 text-left hover:bg-slate-50/50 transition-colors cursor-pointer"
      >
        <svg className={`w-3 h-3 text-slate-400 transition-transform ${expanded ? 'rotate-90' : ''}`} fill="currentColor" viewBox="0 0 20 20">
          <path d="M6 4l8 6-8 6V4z" />
        </svg>
        <span className="text-sm font-semibold text-slate-900">{title}</span>
        {count != null && (
          <span className="text-xs px-1.5 py-0.5 bg-slate-100 text-slate-500 rounded-full font-medium">{count}</span>
        )}
      </button>
      {expanded && (
        <div className="px-5 pb-5 pt-1">
          {children}
        </div>
      )}
    </div>
  )
}

// ========== 드라이브 폴더 관리 섹션 ==========
function DriveFolderSection({ clubId }) {
  const { confirm, modal } = useConfirm()
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })
  const [folders, setFolders] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ name: '', description: '', displayOrder: 0 })
  const [formLoading, setFormLoading] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [editForm, setEditForm] = useState({ name: '', description: '', displayOrder: 0 })
  const [editLoading, setEditLoading] = useState(false)

  const fetchFolders = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/drive/folders`)
      setFolders(data || [])
    } catch (err) {
      handleApiError(err, '드라이브 폴더 조회')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchFolders() }, [clubId])

  const handleCreate = async () => {
    if (!form.name.trim()) return
    setFormLoading(true)
    try {
      await api.post(`/clubs/${clubId}/drive/folders`, {
        name: form.name.trim(),
        description: form.description.trim() || null,
        displayOrder: form.displayOrder || 0,
      })
      setForm({ name: '', description: '', displayOrder: 0 })
      setShowForm(false)
      fetchFolders()
    } catch (err) {
      setAlertModal({ open: true, message: '폴더 생성 실패: ' + (err.message || '') })
    } finally {
      setFormLoading(false)
    }
  }

  const startEdit = (f) => {
    setEditingId(f.id)
    setEditForm({ name: f.name, description: f.description || '', displayOrder: f.displayOrder || 0 })
  }

  const handleUpdate = async (folderId) => {
    if (!editForm.name.trim()) return
    setEditLoading(true)
    try {
      await api.put(`/clubs/${clubId}/drive/folders/${folderId}`, {
        name: editForm.name.trim(),
        description: editForm.description.trim() || null,
        displayOrder: editForm.displayOrder || 0,
      })
      setEditingId(null)
      fetchFolders()
    } catch (err) {
      setAlertModal({ open: true, message: '폴더 수정 실패: ' + (err.message || '') })
    } finally {
      setEditLoading(false)
    }
  }

  const handleDelete = async (f) => {
    const ok = await confirm(`"${f.name}" 폴더를 삭제하시겠습니까? 폴더 안의 파일은 유지됩니다.`, { confirmLabel: '삭제', danger: true })
    if (!ok) return
    try {
      await api.delete(`/clubs/${clubId}/drive/folders/${f.id}`)
      fetchFolders()
    } catch (err) {
      setAlertModal({ open: true, message: '폴더 삭제 실패: ' + (err.message || '') })
    }
  }

  return (
    <CollapsibleSection id="drive-folders" title="드라이브 폴더" count={folders.length}>
      <div>
        <div className="flex items-center justify-between mb-4">
          <p className="text-xs text-slate-400">드라이브 파일을 분류할 폴더를 관리합니다.</p>
          <button
            onClick={() => setShowForm(!showForm)}
            className="text-xs text-primary hover:text-primary/80 cursor-pointer"
          >
            + 폴더 추가
          </button>
        </div>

        {showForm && (
          <div className="bg-white rounded-2xl border border-slate-200 p-4 mb-4 space-y-3">
            <input
              type="text"
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="폴더 이름"
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
              onKeyDown={e => { if (e.key === 'Enter') handleCreate() }}
            />
            <input
              type="text"
              value={form.description}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              placeholder="설명 (선택, 최대 200자)"
              maxLength={200}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => { setShowForm(false); setForm({ name: '', description: '', displayOrder: 0 }) }}
                className="px-3 py-2 text-sm text-slate-500 hover:text-slate-700 cursor-pointer"
              >
                취소
              </button>
              <button
                onClick={handleCreate}
                disabled={!form.name.trim() || formLoading}
                className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50"
              >
                {formLoading ? '추가 중...' : '추가'}
              </button>
            </div>
          </div>
        )}

        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-100">
                <th className="text-left text-xs font-semibold text-slate-500 px-4 py-3">폴더 이름</th>
                <th className="text-left text-xs font-semibold text-slate-500 px-4 py-3">설명</th>
                <th className="text-center text-xs font-semibold text-slate-500 px-4 py-3 w-16">파일 수</th>
                <th className="text-center text-xs font-semibold text-slate-500 px-4 py-3 w-16">순서</th>
                <th className="text-right text-xs font-semibold text-slate-500 px-4 py-3 w-24">조작</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {loading ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-xs text-slate-400">로딩 중...</td></tr>
              ) : folders.length === 0 ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-xs text-slate-400">드라이브 폴더가 없습니다</td></tr>
              ) : folders.map(f => editingId === f.id ? (
                <tr key={f.id} className="bg-primary/5">
                  <td className="px-4 py-2">
                    <input value={editForm.name} onChange={e => setEditForm(ef => ({ ...ef, name: e.target.value }))} className="w-full px-2 py-1 text-sm border border-slate-200 rounded focus:outline-none focus:ring-1 focus:ring-primary/30" />
                  </td>
                  <td className="px-4 py-2">
                    <input value={editForm.description} onChange={e => setEditForm(ef => ({ ...ef, description: e.target.value }))} placeholder="설명" maxLength={200} className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:outline-none focus:ring-1 focus:ring-primary/30" />
                  </td>
                  <td className="px-4 py-2 text-center text-xs text-slate-400">{f.fileCount}</td>
                  <td className="px-4 py-2">
                    <input type="number" value={editForm.displayOrder} onChange={e => setEditForm(ef => ({ ...ef, displayOrder: Number(e.target.value) }))} className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:outline-none text-center" />
                  </td>
                  <td className="px-4 py-2 text-right">
                    <div className="flex justify-end gap-1">
                      <button onClick={() => handleUpdate(f.id)} disabled={editLoading || !editForm.name.trim()} className="text-xs text-primary hover:text-primary/80 cursor-pointer disabled:opacity-50">{editLoading ? '...' : '저장'}</button>
                      <button onClick={() => setEditingId(null)} className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer">취소</button>
                    </div>
                  </td>
                </tr>
              ) : (
                <tr key={f.id} className="hover:bg-slate-50 transition-colors">
                  <td className="px-4 py-3">
                    <span className="text-sm font-medium text-slate-900">{f.name}</span>
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-xs text-slate-400">{f.description || '-'}</span>
                  </td>
                  <td className="px-4 py-3 text-center text-xs text-slate-400">{f.fileCount}</td>
                  <td className="px-4 py-3 text-center text-xs text-slate-400">{f.displayOrder || 0}</td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <button onClick={() => startEdit(f)} className="text-xs text-slate-400 hover:text-primary cursor-pointer">수정</button>
                      <button onClick={() => handleDelete(f)} className="text-xs text-slate-400 hover:text-red-500 cursor-pointer">삭제</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      {modal}
      {alertModal.open && (
        <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
    </CollapsibleSection>
  )
}

// ========== 운영 설정 탭 (통합: 동아리 정보 + 역할태그 + 게시판 + 카테고리 + 기수 + AI) ==========

function OperationsTab({ clubId }) {
  return (
    <div className="flex gap-6">
      {/* 좌측: 설정 섹션들 */}
      <div className="flex-[3] min-w-0 space-y-3">
        <CollapsibleSection id="club-info" title="동아리 정보" defaultOpen={true}>
          <SettingsTab clubId={clubId} embedded />
        </CollapsibleSection>
        <PositionsTab clubId={clubId} />
        <CollapsibleSection id="system-tags" title="시스템 태그">
          <SystemTagsSection clubId={clubId} />
        </CollapsibleSection>
        <CollapsibleSection id="generations" title="기수">
          <GenerationManager clubId={clubId} embedded />
        </CollapsibleSection>
        <CollapsibleSection id="access-stats" title="접속 현황">
          <AccessStatsSection clubId={clubId} />
        </CollapsibleSection>
      </div>

      {/* 우측: 멤버 명단 (EXECUTIVE 토글) */}
      <div className="flex-[2] min-w-0">
        <MemberExecutivePanel clubId={clubId} />
      </div>
    </div>
  )
}

function AccessStatsSection({ clubId }) {
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchStats = async () => {
      try {
        setLoading(true)
        const data = await api.get(`/clubs/${clubId}/access-log/stats`)
        setStats(data)
      } catch (err) {
        handleApiError(err, '접속 통계 조회')
      } finally {
        setLoading(false)
      }
    }
    fetchStats()
  }, [clubId])

  if (loading) return <p className="text-sm text-slate-400">로딩 중...</p>
  if (!stats) return <p className="text-xs text-slate-400">접속 데이터가 없습니다</p>

  return (
    <div className="space-y-4">
      {/* 주간/월간 접속률 카드 */}
      <div className="grid grid-cols-2 gap-3">
        <div className="bg-blue-50 rounded-lg p-4 text-center">
          <p className="text-xs text-blue-600 font-medium mb-1">이번 주 접속률</p>
          <p className="text-2xl font-bold text-blue-700">{stats.weeklyRate}%</p>
        </div>
        <div className="bg-emerald-50 rounded-lg p-4 text-center">
          <p className="text-xs text-emerald-600 font-medium mb-1">이번 달 접속률</p>
          <p className="text-2xl font-bold text-emerald-700">{stats.monthlyRate}%</p>
        </div>
      </div>

      {/* 멤버별 접속일 테이블 */}
      {stats.memberStats && stats.memberStats.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-slate-500 mb-2">멤버별 이번 달 접속 현황</p>
          <div className="border border-slate-200 shadow-sm rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 text-slate-500 text-xs">
                  <th className="text-left px-3 py-2 font-medium">이름</th>
                  <th className="text-center px-3 py-2 font-medium">접속일</th>
                  <th className="text-left px-3 py-2 font-medium min-w-[120px]">접속률</th>
                </tr>
              </thead>
              <tbody>
                {stats.memberStats.map((m, i) => {
                  const rate = m.totalDays > 0 ? Math.round(m.accessDays / m.totalDays * 100) : 0
                  return (
                    <tr key={i} className="border-t border-slate-50">
                      <td className="px-3 py-2 text-slate-900">{m.memberName}</td>
                      <td className="px-3 py-2 text-center text-slate-600">
                        {m.accessDays}/{m.totalDays}일
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 bg-slate-100 rounded-full h-2 overflow-hidden">
                            <div
                              className={`h-full rounded-full transition-all ${
                                rate >= 70 ? 'bg-emerald-400' : rate >= 40 ? 'bg-amber-400' : 'bg-red-400'
                              }`}
                              style={{ width: `${rate}%` }}
                            />
                          </div>
                          <span className="text-xs text-slate-500 w-8 text-right">{rate}%</span>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}

function MemberExecutivePanel({ clubId }) {
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [togglingId, setTogglingId] = useState(null)

  useEffect(() => {
    const fetch = async () => {
      try {
        setLoading(true)
        const data = await api.get(`/clubs/${clubId}/members`)
        setMembers(data || [])
      } catch (err) {
        handleApiError(err, '멤버 조회')
      } finally {
        setLoading(false)
      }
    }
    fetch()
  }, [clubId])

  const handleToggle = async (membershipId, currentValue) => {
    setTogglingId(membershipId)
    try {
      await api.put(`/clubs/${clubId}/admin/members/${membershipId}/executive`, { executive: !currentValue })
      setMembers(prev => prev.map(m =>
        m.membershipId === membershipId ? { ...m, isExecutive: !currentValue } : m
      ))
    } catch (err) {
      handleApiError(err, '권한 변경')
    } finally {
      setTogglingId(null)
    }
  }

  if (loading) return <p className="text-sm text-slate-400 py-4">로딩 중...</p>

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">멤버 명단</h3>
        <span className="text-xs text-slate-400">{members.length}명</span>
      </div>
      <p className="text-xs text-slate-400 mb-2">관리자 권한을 토글하면 관리 탭 접근, 공지 작성 등 전역 관리 기능을 부여/해제합니다.</p>
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm divide-y divide-slate-50 max-h-[calc(100vh-250px)] overflow-y-auto">
        {members.map(m => (
          <div key={m.membershipId} className="flex items-center justify-between px-3 py-2.5">
            <div className="flex items-center gap-2 min-w-0">
              <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium shrink-0 ${
                m.isExecutive ? 'bg-amber-50 text-amber-600' : 'bg-slate-100 text-slate-600'
              }`}>
                {m.name?.charAt(0)}
              </div>
              <div className="min-w-0">
                <span className="text-sm font-medium text-slate-900 truncate block">{m.name}</span>
                <span className="text-xs text-slate-400">{m.studentId}</span>
              </div>
            </div>
            <button
              onClick={() => handleToggle(m.membershipId, m.isExecutive)}
              disabled={togglingId === m.membershipId}
              className={`relative w-10 h-5 rounded-full transition-colors cursor-pointer shrink-0 ${
                m.isExecutive ? 'bg-amber-500' : 'bg-slate-300'
              } ${togglingId === m.membershipId ? 'opacity-50' : ''}`}
              title={m.isExecutive ? '관리자 권한 해제' : '관리자 권한 부여'}
            >
              <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform shadow ${
                m.isExecutive ? 'translate-x-5' : ''
              }`} />
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

function SystemTagsSection({ clubId }) {
  const [systemTags, setSystemTags] = useState([])
  const [loading, setLoading] = useState(true)
  const [togglingId, setTogglingId] = useState(null)

  const fetchSystemTags = async () => {
    try {
      setLoading(true)
      const data = await api.get(`/clubs/${clubId}/admin/semi-categories/system`)
      setSystemTags(data || [])
    } catch (err) {
      handleApiError(err, '시스템 태그 조회')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchSystemTags() }, [clubId])

  const handleToggle = async (tagId) => {
    setTogglingId(tagId)
    try {
      const updated = await api.request(`/clubs/${clubId}/admin/semi-categories/${tagId}/toggle`, { method: 'PATCH' })
      setSystemTags(prev => prev.map(t => t.id === tagId ? updated : t))
    } catch (err) {
      setAlertModal({ open: true, message: '태그 토글 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setTogglingId(null)
    }
  }

  if (loading) return <p className="text-sm text-slate-400">로딩 중...</p>
  if (systemTags.length === 0) return <p className="text-xs text-slate-400">시스템 태그가 없습니다</p>

  return (
    <div>
      <p className="text-xs text-slate-400 mb-3">시스템이 자동 생성한 태그입니다. 비활성화하면 게시판/드라이브 필터에서 숨겨집니다.</p>
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden divide-y divide-slate-50">
        {systemTags.map(tag => (
          <div key={tag.id} className="flex items-center justify-between px-4 py-3">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-slate-900">{tag.name}</span>
              <span className="text-xs px-1.5 py-0.5 bg-slate-100 text-slate-400 rounded-full">시스템</span>
            </div>
            <button
              onClick={() => handleToggle(tag.id)}
              disabled={togglingId === tag.id}
              className={`relative w-10 h-5 rounded-full transition-colors cursor-pointer ${
                tag.enabled ? 'bg-blue-500' : 'bg-slate-300'
              } ${togglingId === tag.id ? 'opacity-50' : ''}`}
            >
              <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform shadow ${
                tag.enabled ? 'translate-x-5' : ''
              }`} />
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

function PositionsTab({ clubId }) {
  const { confirm, modal } = useConfirm()
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })
  const [loading, setLoading] = useState(true)

  // 역할 태그 관리
  const [tagDefs, setTagDefs] = useState([])
  const [showTagForm, setShowTagForm] = useState(false)
  const [tagForm, setTagForm] = useState({ name: '', permissionLevel: 'REGULAR' })
  const [tagFormLoading, setTagFormLoading] = useState(false)
  const [editingTagId, setEditingTagId] = useState(null)
  const [editTagForm, setEditTagForm] = useState({ name: '', permissionLevel: 'REGULAR' })
  const [editTagLoading, setEditTagLoading] = useState(false)

  // 게시판 관리
  const [boards, setBoards] = useState([])
  const [teams, setTeams] = useState([])
  const [showBoardForm, setShowBoardForm] = useState(false)
  const [boardForm, setBoardForm] = useState({ name: '', writePermission: 'EVERYONE', description: '' })
  const [boardFormLoading, setBoardFormLoading] = useState(false)
  const [editingBoardId, setEditingBoardId] = useState(null)
  const [editBoardForm, setEditBoardForm] = useState({ name: '', writePermission: 'EVERYONE', description: '', displayOrder: 0 })
  const [editBoardLoading, setEditBoardLoading] = useState(false)

  // 세미카테고리 관리
  const [categories, setCategories] = useState([])
  const [showCatForm, setShowCatForm] = useState(false)
  const [catForm, setCatForm] = useState({ name: '', displayOrder: 0 })
  const [catFormLoading, setCatFormLoading] = useState(false)
  const [editingCatId, setEditingCatId] = useState(null)
  const [editCatForm, setEditCatForm] = useState({ name: '', displayOrder: 0 })
  const [editCatLoading, setEditCatLoading] = useState(false)

  // AI 글작성 권한 설정
  const [aiAccessMode, setAiAccessMode] = useState('ALL')
  const [aiAccessSaving, setAiAccessSaving] = useState(false)

  // ===== 폴더링 (DnD 아이폰 패턴) =====
  const [folders, setFolders] = useState([])
  const [editingFolderId, setEditingFolderId] = useState(null)
  const [editFolderName, setEditFolderName] = useState('')
  const [collapsedFolders, setCollapsedFolders] = useState(() => {
    try { return JSON.parse(localStorage.getItem('admin-folder-collapsed') || '{}') } catch { return {} }
  })
  const [activeDragId, setActiveDragId] = useState(null)
  const [activeDragFolderId, setActiveDragFolderId] = useState(null) // 드래그 중인 아이템의 소속 폴더

  const fetchTagDefs = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/admin/tags`)
      setTagDefs(data || [])
    } catch (err) {
      handleApiError(err, '태그 정의 조회')
    }
  }

  const fetchBoards = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/boards`)
      setBoards(data)
    } catch (err) {
      handleApiError(err, '게시판 조회')
    }
  }

  const fetchCategories = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/semi-categories`)
      setCategories(data || [])
    } catch (err) {
      handleApiError(err, '카테고리 조회')
    }
  }

  const fetchTeams = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/teams`)
      setTeams(data)
    } catch (err) {
      handleApiError(err, '팀 조회')
    }
  }

  const fetchAiAccessMode = async () => {
    try {
      const data = await api.get(`/clubs/${clubId}/settings`)
      setAiAccessMode(data.aiAccessMode || 'EXECUTIVE_ONLY')
    } catch (err) {
      handleApiError(err, 'AI 설정 조회')
    }
  }

  const handleAiAccessModeChange = async (mode) => {
    setAiAccessSaving(true)
    try {
      await api.put(`/clubs/${clubId}/settings`, { aiAccessMode: mode })
      setAiAccessMode(mode)
    } catch (err) {
      handleApiError(err, 'AI 설정 저장')
    } finally {
      setAiAccessSaving(false)
    }
  }

  // ===== 폴더 CRUD =====
  const fetchFolders = async () => {
    try {
      const [tagF, boardF, catF] = await Promise.all([
        api.get(`/clubs/${clubId}/admin/folders?type=TAG`),
        api.get(`/clubs/${clubId}/admin/folders?type=BOARD`),
        api.get(`/clubs/${clubId}/admin/folders?type=CATEGORY`),
      ])
      setFolders([...(tagF || []), ...(boardF || []), ...(catF || [])])
    } catch (err) { handleApiError(err, '폴더 조회') }
  }

  const createFolderFromDrop = async (folderType, itemIds) => {
    try {
      await api.post(`/clubs/${clubId}/admin/folders`, { name: '새 그룹', folderType, itemIds })
      await Promise.all([fetchTagDefs(), fetchBoards(), fetchCategories(), fetchFolders()])
    } catch (err) { handleApiError(err, '폴더 생성') }
  }

  const handleRenameFolder = async (folderId) => {
    if (!editFolderName.trim()) return
    try {
      await api.put(`/clubs/${clubId}/admin/folders/${folderId}/name`, { name: editFolderName.trim() })
      setEditingFolderId(null)
      setEditFolderName('')
      fetchFolders()
    } catch (err) { handleApiError(err, '폴더 이름 변경') }
  }

  const handleDeleteFolder = async (folder) => {
    const ok = await confirm(`"${folder.name}" 폴더를 삭제하시겠습니까? 폴더 안의 항목은 최상위로 이동합니다.`, { confirmLabel: '삭제', danger: true })
    if (!ok) return
    try {
      await api.delete(`/clubs/${clubId}/admin/folders/${folder.id}`)
      fetchFolders()
      // 폴더에 속해있던 아이템들이 최상위로 이동하므로 데이터 새로고침
      await Promise.all([fetchTagDefs(), fetchBoards(), fetchCategories()])
    } catch (err) { handleApiError(err, '폴더 삭제') }
  }

  const toggleFolderCollapsed = (folderId) => {
    setCollapsedFolders(prev => {
      const next = { ...prev, [folderId]: !prev[folderId] }
      try { localStorage.setItem('admin-folder-collapsed', JSON.stringify(next)) } catch {}
      return next
    })
  }

  const handleAssignToFolder = async (folderId, itemIds) => {
    try {
      await api.put(`/clubs/${clubId}/admin/folders/${folderId}/items`, { itemIds })
      await Promise.all([fetchTagDefs(), fetchBoards(), fetchCategories(), fetchFolders()])
    } catch (err) { handleApiError(err, '폴더 배정') }
  }

  const handleUnassignFromFolder = async (itemIds, folderType) => {
    try {
      await api.put(`/clubs/${clubId}/admin/folders/unassign`, { itemIds, folderType })
      await Promise.all([fetchTagDefs(), fetchBoards(), fetchCategories(), fetchFolders()])
    } catch (err) { handleApiError(err, '폴더 해제') }
  }

  // 폴더 타입별 필터
  const getFoldersForType = (type) => folders.filter(f => f.folderType === type)

  // 아이템을 폴더별로 그룹핑 (root + 각 폴더)
  const groupByFolder = (items, folderType) => {
    const typeFolders = getFoldersForType(folderType)
    const rootItems = items.filter(item => !item.folderId)
    const folderGroups = typeFolders.map(f => ({
      folder: f,
      items: items.filter(item => item.folderId === f.id)
    }))
    return { rootItems, folderGroups }
  }

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      await Promise.all([fetchTagDefs(), fetchBoards(), fetchCategories(), fetchTeams(), fetchAiAccessMode(), fetchFolders()])
      setLoading(false)
    }
    load()
  }, [clubId])

  // ===== 게시판 CRUD =====
  const handleCreateBoard = async () => {
    if (!boardForm.name.trim()) return
    setBoardFormLoading(true)
    try {
      const body = { name: boardForm.name.trim(), writePermission: boardForm.writePermission, description: boardForm.description.trim() || null }
      await api.post(`/clubs/${clubId}/admin/boards`, body)
      setBoardForm({ name: '', writePermission: 'EVERYONE', description: '' })
      setShowBoardForm(false)
      fetchBoards()
    } catch (err) {
      setAlertModal({ open: true, message: '게시판 생성 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setBoardFormLoading(false)
    }
  }

  const startEditBoard = (board) => {
    setEditingBoardId(board.id)
    setEditBoardForm({ name: board.name, writePermission: board.writePermission || 'EVERYONE', description: board.description || '', displayOrder: board.displayOrder || 0, teamId: board.teamId ? String(board.teamId) : '' })
  }

  const handleUpdateBoard = async (boardId) => {
    if (!editBoardForm.name.trim()) return
    setEditBoardLoading(true)
    try {
      const body = {
        name: editBoardForm.name.trim(),
        writePermission: editBoardForm.writePermission,
        description: editBoardForm.description.trim() || null,
        displayOrder: editBoardForm.displayOrder,
      }
      // teamId 변경 전송: 빈 문자열 → 0 (팀 해제), 숫자 → 해당 팀
      const originalBoard = boards.find(b => b.id === boardId)
      const origTeamId = originalBoard?.teamId ? String(originalBoard.teamId) : ''
      if (editBoardForm.teamId !== origTeamId) {
        body.teamId = editBoardForm.teamId ? Number(editBoardForm.teamId) : 0
      }
      await api.put(`/clubs/${clubId}/admin/boards/${boardId}`, body)
      setEditingBoardId(null)
      fetchBoards()
    } catch (err) {
      setAlertModal({ open: true, message: '게시판 수정 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setEditBoardLoading(false)
    }
  }

  const handleDeleteBoard = async (board) => {
    const ok = await confirm(`"${board.name}" 게시판을 삭제하시겠습니까? 모든 글이 함께 삭제됩니다.`, { confirmLabel: '삭제', danger: true })
    if (!ok) return
    try {
      await api.delete(`/clubs/${clubId}/admin/boards/${board.id}`)
      fetchBoards()
    } catch (err) {
      setAlertModal({ open: true, message: '게시판 삭제 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  // ===== 세미카테고리 CRUD =====
  const handleCreateCategory = async () => {
    if (!catForm.name.trim()) return
    setCatFormLoading(true)
    try {
      await api.post(`/clubs/${clubId}/admin/semi-categories`, { name: catForm.name.trim(), displayOrder: catForm.displayOrder || 0 })
      setCatForm({ name: '', displayOrder: 0 })
      setShowCatForm(false)
      fetchCategories()
    } catch (err) {
      setAlertModal({ open: true, message: '카테고리 생성 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setCatFormLoading(false)
    }
  }

  const startEditCategory = (cat) => {
    setEditingCatId(cat.id)
    setEditCatForm({ name: cat.name, displayOrder: cat.displayOrder || 0 })
  }

  const handleUpdateCategory = async (catId) => {
    if (!editCatForm.name.trim()) return
    setEditCatLoading(true)
    try {
      await api.put(`/clubs/${clubId}/admin/semi-categories/${catId}`, { name: editCatForm.name.trim(), displayOrder: editCatForm.displayOrder || 0 })
      setEditingCatId(null)
      fetchCategories()
    } catch (err) {
      setAlertModal({ open: true, message: '카테고리 수정 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setEditCatLoading(false)
    }
  }

  const handleDeleteCategory = async (cat) => {
    const ok = await confirm(`"${cat.name}" 카테고리를 삭제하시겠습니까? 해당 카테고리에 태그된 글의 카테고리가 해제됩니다.`, { confirmLabel: '삭제', danger: true })
    if (!ok) return
    try {
      await api.delete(`/clubs/${clubId}/admin/semi-categories/${cat.id}`)
      fetchCategories()
    } catch (err) {
      setAlertModal({ open: true, message: '카테고리 삭제 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  // ===== DnD 폴더 통합 핸들러 =====
  const handleDragStartForSection = (event, folderType) => {
    setActiveDragId(event.active.id)
    // 드래그 중인 아이템이 폴더에 속해있는지 추적 (섹션별로 검색해야 ID 충돌 방지)
    const itemId = Number(String(event.active.id).replace('drag-', ''))
    const items = folderType === 'TAG' ? tagDefs : folderType === 'BOARD' ? boards : categories
    const item = items.find(i => i.id === itemId)
    setActiveDragFolderId(item?.folderId || null)
  }

  const handleSectionDragEnd = async (event, folderType) => {
    const draggedFolderId = activeDragFolderId
    setActiveDragId(null)
    setActiveDragFolderId(null)
    const { active, over } = event
    if (!over || active.id === over.id) return

    const activeItemId = Number(String(active.id).replace('drag-', ''))
    const overId = String(over.id)

    if (overId.startsWith('folder-')) {
      const folderId = Number(overId.replace('folder-', ''))
      await handleAssignToFolder(folderId, [activeItemId])
    } else if (overId === `root-${folderType}`) {
      // 루트 영역에 드롭 → 폴더 해제 (폴더 소속일 때만)
      if (draggedFolderId) {
        await handleUnassignFromFolder([activeItemId], folderType)
      }
    } else if (overId.startsWith('item-')) {
      const overItemId = Number(overId.replace('item-', ''))
      const items = folderType === 'TAG' ? tagDefs : folderType === 'BOARD' ? boards : categories
      const activeItem = items.find(i => i.id === activeItemId)
      const overItem = items.find(i => i.id === overItemId)
      if (!activeItem || !overItem) return
      if (overItem.folderId) {
        await handleAssignToFolder(overItem.folderId, [activeItemId])
      } else if (draggedFolderId && !overItem.folderId) {
        // 폴더 아이템을 루트 아이템 위에 드롭 → 폴더 해제
        await handleUnassignFromFolder([activeItemId], folderType)
      } else {
        await createFolderFromDrop(folderType, [activeItemId, overItemId])
      }
    }
  }

  const getDragItemName = (dragId, folderType) => {
    const id = Number(String(dragId).replace('drag-', ''))
    const items = folderType === 'TAG' ? tagDefs : folderType === 'BOARD' ? boards : categories
    return items.find(i => i.id === id)?.name || ''
  }

  // ===== 태그 CRUD =====
  const handleCreateTag = async () => {
    if (!tagForm.name.trim()) return
    setTagFormLoading(true)
    try {
      await api.post(`/clubs/${clubId}/admin/tags`, { name: tagForm.name.trim(), permissionLevel: tagForm.permissionLevel })
      setTagForm({ name: '', permissionLevel: 'REGULAR' })
      setShowTagForm(false)
      fetchTagDefs()
    } catch (err) {
      setAlertModal({ open: true, message: '태그 생성 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setTagFormLoading(false)
    }
  }

  const startEditTag = (td) => {
    setEditingTagId(td.id)
    setEditTagForm({ name: td.name, permissionLevel: td.permissionLevel })
  }

  const handleUpdateTag = async (tagId) => {
    setEditTagLoading(true)
    try {
      await api.put(`/clubs/${clubId}/admin/tags/${tagId}`, {
        name: editTagForm.name.trim(),
        permissionLevel: editTagForm.permissionLevel,
      })
      setEditingTagId(null)
      fetchTagDefs()
    } catch (err) {
      setAlertModal({ open: true, message: '태그 수정 실패: ' + (err.message || '알 수 없는 오류') })
    } finally {
      setEditTagLoading(false)
    }
  }

  const handleToggleTagPermission = async (td) => {
    const newLevel = td.permissionLevel === 'EXECUTIVE' ? 'REGULAR' : 'EXECUTIVE'
    try {
      await api.put(`/clubs/${clubId}/admin/tags/${td.id}`, { permissionLevel: newLevel })
      fetchTagDefs()
    } catch (err) {
      setAlertModal({ open: true, message: '권한 변경 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  const handleDeleteTag = async (td) => {
    const ok = await confirm(`"${td.name}" 태그를 삭제하시겠습니까? 이 태그를 보유한 모든 멤버에서 제거됩니다.`, { confirmLabel: '삭제', danger: true })
    if (!ok) return
    try {
      await api.delete(`/clubs/${clubId}/admin/tags/${td.id}`)
      fetchTagDefs()
    } catch (err) {
      setAlertModal({ open: true, message: '태그 삭제 실패: ' + (err.message || '알 수 없는 오류') })
    }
  }

  if (loading) {
    return (
      <div className="py-12 text-center">
        <div className="animate-spin w-6 h-6 border-3 border-primary border-t-transparent rounded-full mx-auto" />
      </div>
    )
  }

  return (
    <>
      {modal}
      {alertModal.open && (
        <ConfirmModal title="오류" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
      )}
      {/* 역할 태그 섹션 삭제됨 — 멤버 명단(우측 패널)으로 대체 */}
      {false && <CollapsibleSection id="__removed__" title="">
        <div>
        <div>
          <p className="text-xs text-slate-400">드래그하여 태그끼리 겹치면 그룹이 생성됩니다.</p>
          <button
            onClick={() => setShowTagForm(!showTagForm)}
            className="text-xs text-primary hover:text-primary/80 cursor-pointer"
          >
            + 커스텀 태그 추가
          </button>
        </div>

        {showTagForm && (
          <div className="bg-white rounded-2xl border border-slate-200 p-4 mb-4 flex items-end gap-3">
            <div className="flex-1">
              <label className="text-xs text-slate-500 mb-1 block">태그 이름</label>
              <input
                value={tagForm.name}
                onChange={e => setTagForm(f => ({ ...f, name: e.target.value }))}
                placeholder="예: 부회장, 총무, 서기"
                className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                onKeyDown={e => { if (e.key === 'Enter') handleCreateTag() }}
              />
            </div>
            <div>
              <label className="text-xs text-slate-500 mb-1 block">권한</label>
              <select
                value={tagForm.permissionLevel}
                onChange={e => setTagForm(f => ({ ...f, permissionLevel: e.target.value }))}
                className="px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="REGULAR">일반 (REGULAR)</option>
                <option value="EXECUTIVE">임원 (EXECUTIVE)</option>
              </select>
            </div>
            <button
              onClick={handleCreateTag}
              disabled={tagFormLoading || !tagForm.name.trim()}
              className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50"
            >
              {tagFormLoading ? '추가 중...' : '추가'}
            </button>
            <button
              onClick={() => { setShowTagForm(false); setTagForm({ name: '', permissionLevel: 'REGULAR' }) }}
              className="px-3 py-2 text-sm text-slate-500 hover:text-slate-700 cursor-pointer"
            >
              취소
            </button>
          </div>
        )}

        <DndContext collisionDetection={pointerWithin} onDragStart={(e) => handleDragStartForSection(e, 'TAG')} onDragEnd={(e) => handleSectionDragEnd(e, 'TAG')}>
        <RootDropZone id="root-TAG">
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-100">
                <th className="w-8"></th>
                <th className="text-left text-xs font-semibold text-slate-500 px-4 py-3">태그 이름</th>
                <th className="text-left text-xs font-semibold text-slate-500 px-4 py-3 w-20">유형</th>
                <th className="text-center text-xs font-semibold text-slate-500 px-4 py-3 w-24">권한</th>
                <th className="text-center text-xs font-semibold text-slate-500 px-4 py-3 w-16">멤버</th>
                <th className="text-right text-xs font-semibold text-slate-500 px-4 py-3 w-24">조작</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {tagDefs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-xs text-slate-400">태그 정의가 없습니다</td>
                </tr>
              ) : (() => {
                const { rootItems, folderGroups } = groupByFolder(tagDefs, 'TAG')
                const renderTagRow = (td, inFolder, folderColor) => editingTagId === td.id ? (
                <tr key={td.id} className="bg-primary/5">
                  <td className="w-8"></td>
                  <td className="px-4 py-2">
                    {td.tagType === 'CUSTOM' ? (
                      <input
                        value={editTagForm.name}
                        onChange={e => setEditTagForm(f => ({ ...f, name: e.target.value }))}
                        className="w-full px-2 py-1 text-sm border border-slate-200 rounded focus:outline-none focus:ring-1 focus:ring-primary/30"
                        onKeyDown={e => { if (e.key === 'Enter') handleUpdateTag(td.id) }}
                      />
                    ) : (
                      <span className="text-sm text-slate-900">{td.name}</span>
                    )}
                  </td>
                  <td className="px-4 py-2"><TagTypeBadge type={td.tagType} /></td>
                  <td className="px-4 py-2 text-center">
                    <select
                      value={editTagForm.permissionLevel}
                      onChange={e => setEditTagForm(f => ({ ...f, permissionLevel: e.target.value }))}
                      className="text-xs px-2 py-1 border border-slate-200 rounded focus:outline-none"
                    >
                      <option value="REGULAR">REGULAR</option>
                      <option value="EXECUTIVE">EXECUTIVE</option>
                    </select>
                  </td>
                  <td className="px-4 py-2 text-center text-xs text-slate-400">{td.memberCount}</td>
                  <td className="px-4 py-2 text-right">
                    <div className="flex justify-end gap-1">
                      <button onClick={() => handleUpdateTag(td.id)} disabled={editTagLoading} className="text-xs text-primary hover:text-primary/80 cursor-pointer disabled:opacity-50">
                        {editTagLoading ? '...' : '저장'}
                      </button>
                      <button onClick={() => setEditingTagId(null)} className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer">취소</button>
                    </div>
                  </td>
                </tr>
              ) : (
                <DndItemRow key={td.id} dragId={`drag-${td.id}`} dropId={`item-${td.id}`} isInFolder={inFolder} isDimmed={!!activeDragFolderId && td.folderId !== activeDragFolderId} folderColor={folderColor}>
                  <td className="px-4 py-3">
                    <span className="text-sm font-medium text-slate-900">{td.name}</span>
                    {td.teamName && <span className="text-xs text-slate-400 ml-1.5">({td.teamName})</span>}
                  </td>
                  <td className="px-4 py-3"><TagTypeBadge type={td.tagType} /></td>
                  <td className="px-4 py-3 text-center">
                    <div className="flex items-center justify-center gap-1.5">
                      <button
                        onClick={() => handleToggleTagPermission(td)}
                        className={`relative inline-flex h-[18px] w-8 items-center rounded-full transition-colors cursor-pointer ${
                          td.permissionLevel === 'EXECUTIVE' ? 'bg-amber-500' : 'bg-slate-300'
                        }`}
                        title={td.permissionLevel === 'EXECUTIVE' ? '관리자 권한 (클릭하여 해제)' : '일반 권한 (클릭하여 관리자로)'}
                      >
                        <span className={`inline-block h-3.5 w-3.5 rounded-full bg-white shadow-sm transition-transform ${
                          td.permissionLevel === 'EXECUTIVE' ? 'translate-x-[15px]' : 'translate-x-[2px]'
                        }`} />
                      </button>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-center text-xs text-slate-400">{td.memberCount}명</td>
                  <td className="px-4 py-3 text-right" onPointerDown={e => e.stopPropagation()}>
                    <div className="flex justify-end gap-2">
                      {(td.tagType === 'CUSTOM' || (td.tagType !== 'SYSTEM')) && (
                        <button onClick={() => startEditTag(td)} className="text-xs text-slate-400 hover:text-primary cursor-pointer">수정</button>
                      )}
                      {td.tagType === 'CUSTOM' && (
                        <button onClick={() => handleDeleteTag(td)} className="text-xs text-slate-400 hover:text-red-500 cursor-pointer">삭제</button>
                      )}
                    </div>
                  </td>
                </DndItemRow>
              )
                return [
                  ...folderGroups.map(({ folder, items }, idx) => {
                    const color = TEAM_COLORS[idx % TEAM_COLORS.length]
                    return [
                    <GroupHeaderRow
                      key={`folder-${folder.id}`}
                      folder={folder}
                      colSpan={6}
                      itemCount={items.length}
                      collapsed={!!collapsedFolders[folder.id]}
                      onToggle={toggleFolderCollapsed}
                      editingFolderId={editingFolderId}
                      editFolderName={editFolderName}
                      setEditingFolderId={setEditingFolderId}
                      setEditFolderName={setEditFolderName}
                      onRename={handleRenameFolder}
                      onDelete={handleDeleteFolder}
                      isActiveDragSource={activeDragFolderId === folder.id}
                      folderColor={color}
                    />,
                    ...(!collapsedFolders[folder.id] ? items.map(td => renderTagRow(td, true, color)) : [])
                  ]}),
                  ...rootItems.map(td => renderTagRow(td, false)),
                ].flat()
              })()}
            </tbody>
          </table>
        </div>
        </RootDropZone>
        <DragOverlay>
          {activeDragId && <div className="px-3 py-2 bg-white rounded-lg shadow-lg border border-primary/30 text-sm font-medium">{getDragItemName(activeDragId, 'TAG')}</div>}
        </DragOverlay>
        </DndContext>
        </div>
      </CollapsibleSection>}

      {/* ===== AI 설정 ===== */}
      <CollapsibleSection id="ai-settings" title="AI 설정 (베타)">
        <div>
        <div className="mb-4">
          <p className="text-xs text-slate-400">AI 글작성 기능(베타)에 접근할 수 있는 멤버를 설정합니다.</p>
        </div>
        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center gap-3">
            <label className="text-xs text-slate-500 shrink-0">접근 모드</label>
            <select
              value={aiAccessMode}
              onChange={(e) => handleAiAccessModeChange(e.target.value)}
              disabled={aiAccessSaving}
              className="text-sm border border-slate-200 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-200 cursor-pointer"
            >
              <option value="EXECUTIVE_ONLY">임원급만 (EXECUTIVE)</option>
              <option value="ALL">전원 허용</option>
              <option value="NONE">비허용</option>
            </select>
            {aiAccessSaving && (
              <div className="animate-spin w-3.5 h-3.5 border-2 border-slate-300 border-t-gray-600 rounded-full" />
            )}
          </div>
          <p className="text-xs text-slate-400 mt-2">
            {aiAccessMode === 'EXECUTIVE_ONLY' && '임원급(EXECUTIVE) 권한을 가진 멤버만 AI 글작성을 사용할 수 있습니다.'}
            {aiAccessMode === 'ALL' && '모든 활성 멤버가 AI 글작성을 사용할 수 있습니다.'}
            {aiAccessMode === 'NONE' && 'AI 글작성 기능이 비활성화되었습니다.'}
          </p>
        </div>
        </div>
      </CollapsibleSection>

      {/* ===== 게시판 관리 ===== */}
      <CollapsibleSection id="boards" title="게시판" count={boards.length}>
        <div>
        <div className="flex items-center justify-between mb-4">
          <p className="text-xs text-slate-400">드래그하여 게시판끼리 겹치면 그룹이 생성됩니다.</p>
          <button
            onClick={() => setShowBoardForm(!showBoardForm)}
            className="text-xs text-primary hover:text-primary/80 cursor-pointer"
          >
            + 게시판 추가
          </button>
        </div>

        {showBoardForm && (
          <div className="bg-white rounded-2xl border border-slate-200 p-4 mb-4 space-y-3">
            <input
              type="text"
              value={boardForm.name}
              onChange={e => setBoardForm(f => ({ ...f, name: e.target.value }))}
              placeholder="게시판 이름"
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
            <input
              type="text"
              value={boardForm.description}
              onChange={e => setBoardForm(f => ({ ...f, description: e.target.value }))}
              placeholder="설명 (선택, 최대 200자)"
              maxLength={200}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
            <div className="flex items-center gap-3">
              <select
                value={boardForm.writePermission}
                onChange={e => setBoardForm(f => ({ ...f, writePermission: e.target.value }))}
                className="flex-1 px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="EVERYONE">글쓰기: 모든 멤버</option>
                <option value="EXECUTIVE_ONLY">글쓰기: 임원만</option>
              </select>
            </div>
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => { setShowBoardForm(false); setBoardForm({ name: '', writePermission: 'EVERYONE', description: '' }) }}
                className="px-3 py-2 text-sm text-slate-500 hover:text-slate-700 cursor-pointer"
              >
                취소
              </button>
              <button
                onClick={handleCreateBoard}
                disabled={!boardForm.name.trim() || boardFormLoading}
                className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50"
              >
                {boardFormLoading ? '추가 중...' : '추가'}
              </button>
            </div>
          </div>
        )}

        <DndContext collisionDetection={pointerWithin} onDragStart={(e) => handleDragStartForSection(e, 'BOARD')} onDragEnd={(e) => handleSectionDragEnd(e, 'BOARD')}>
          <RootDropZone id="root-BOARD">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-100">
                  <th className="w-8"></th>
                  <th className="text-left text-xs font-semibold text-slate-500 px-4 py-3">게시판 이름</th>
                  <th className="text-left text-xs font-semibold text-slate-500 px-4 py-3">유형</th>
                  <th className="text-left text-xs font-semibold text-slate-500 px-4 py-3 w-24">글쓰기 권한</th>
                  <th className="text-center text-xs font-semibold text-slate-500 px-4 py-3 w-14">글</th>
                  <th className="text-right text-xs font-semibold text-slate-500 px-4 py-3 w-24">조작</th>
                </tr>
              </thead>
                <tbody className="divide-y divide-slate-50">
                  {boards.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-4 py-8 text-center text-xs text-slate-400">게시판이 없습니다</td>
                    </tr>
                  ) : (() => {
                    const { rootItems, folderGroups } = groupByFolder(boards, 'BOARD')
                    const renderBoardRow = (b, inFolder, folderColor) => editingBoardId === b.id ? (
                    <tr key={b.id} className="bg-primary/5">
                      <td className="w-8"></td>
                      <td className="px-4 py-2">
                        <input value={editBoardForm.name} onChange={e => setEditBoardForm(f => ({ ...f, name: e.target.value }))} className="w-full px-2 py-1 text-sm border border-slate-200 rounded focus:outline-none focus:ring-1 focus:ring-primary/30" />
                        <input value={editBoardForm.description} onChange={e => setEditBoardForm(f => ({ ...f, description: e.target.value }))} placeholder="설명" maxLength={200} className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:outline-none focus:ring-1 focus:ring-primary/30 mt-1" />
                      </td>
                      <td className="px-4 py-2"><BoardTypeBadge type={b.boardType} /></td>
                      <td className="px-4 py-2">
                        <select value={editBoardForm.writePermission} onChange={e => setEditBoardForm(f => ({ ...f, writePermission: e.target.value }))} className="text-xs px-1 py-1 border border-slate-200 rounded focus:outline-none">
                            <option value="EVERYONE">모든 멤버</option>
                            <option value="EXECUTIVE_ONLY">임원만</option>
                          </select>
                      </td>
                      <td className="px-4 py-2 text-center text-xs text-slate-400">{b.postCount}</td>
                      <td className="px-4 py-2 text-right">
                        <div className="flex justify-end gap-1">
                          <button onClick={() => handleUpdateBoard(b.id)} disabled={editBoardLoading || !editBoardForm.name.trim()} className="text-xs text-primary hover:text-primary/80 cursor-pointer disabled:opacity-50">{editBoardLoading ? '...' : '저장'}</button>
                          <button onClick={() => setEditingBoardId(null)} className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer">취소</button>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    <DndItemRow key={b.id} dragId={`drag-${b.id}`} dropId={`item-${b.id}`} isInFolder={inFolder} isDimmed={!!activeDragFolderId && b.folderId !== activeDragFolderId} folderColor={folderColor}>
                      <td className="px-4 py-3">
                        <span className="text-sm font-medium text-slate-900">{b.name}</span>
                        {b.description && <p className="text-xs text-slate-400 mt-0.5 truncate max-w-[200px]">{b.description}</p>}
                      </td>
                      <td className="px-4 py-3">
                        <BoardTypeBadge type={b.boardType} />
                        {!BOARD_TYPE_STYLES[b.boardType] && <span className="text-xs text-slate-300">-</span>}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`text-xs px-1.5 py-0.5 rounded-full ${b.writePermission === 'EXECUTIVE_ONLY' ? 'bg-amber-50 text-amber-600' : 'bg-slate-100 text-slate-500'}`}>
                          {WRITE_PERM_LABEL[b.writePermission] || '모든 멤버'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center text-xs text-slate-400">{b.postCount}</td>
                      <td className="px-4 py-3 text-right" onPointerDown={e => e.stopPropagation()}>
                        <div className="flex justify-end gap-2">
                          <button onClick={() => startEditBoard(b)} className="text-xs text-slate-400 hover:text-primary cursor-pointer">수정</button>
                          {b.boardType !== 'AI' && <button onClick={() => handleDeleteBoard(b)} className="text-xs text-slate-400 hover:text-red-500 cursor-pointer">삭제</button>}
                        </div>
                      </td>
                    </DndItemRow>
                  )
                    return [
                      ...folderGroups.map(({ folder, items }, idx) => {
                        const color = TEAM_COLORS[idx % TEAM_COLORS.length]
                        return [
                        <GroupHeaderRow
                          key={`folder-${folder.id}`} folder={folder} colSpan={7} itemCount={items.length}
                          collapsed={!!collapsedFolders[folder.id]} onToggle={toggleFolderCollapsed}
                          editingFolderId={editingFolderId} editFolderName={editFolderName}
                          setEditingFolderId={setEditingFolderId} setEditFolderName={setEditFolderName}
                          onRename={handleRenameFolder} onDelete={handleDeleteFolder}
                          isActiveDragSource={activeDragFolderId === folder.id}
                          folderColor={color}
                        />,
                        ...(!collapsedFolders[folder.id] ? items.map(b => renderBoardRow(b, true, color)) : [])
                      ]}),
                      ...rootItems.map(b => renderBoardRow(b, false)),
                    ].flat()
                  })()}
                </tbody>
            </table>
          </div>
          </RootDropZone>
          <DragOverlay>
            {activeDragId && <div className="px-3 py-2 bg-white rounded-lg shadow-lg border border-primary/30 text-sm font-medium">{getDragItemName(activeDragId, 'BOARD')}</div>}
          </DragOverlay>
        </DndContext>
        </div>
      </CollapsibleSection>

      {/* ===== 드라이브 폴더 관리 ===== */}
      <DriveFolderSection clubId={clubId} />

      {/* ===== 세미카테고리 관리 ===== */}
      <CollapsibleSection id="categories" title="콘텐츠 태그" count={categories.length}>
        <div>
        <div className="flex items-center justify-between mb-4">
          <p className="text-xs text-slate-400">드래그하여 카테고리끼리 겹치면 그룹이 생성됩니다.</p>
          <button
            onClick={() => setShowCatForm(!showCatForm)}
            className="text-xs text-primary hover:text-primary/80 cursor-pointer"
          >
            + 카테고리 추가
          </button>
        </div>

        {showCatForm && (
          <div className="bg-white rounded-2xl border border-slate-200 p-4 mb-4 flex items-end gap-3">
            <div className="flex-1">
              <label className="text-xs text-slate-500 mb-1 block">카테고리 이름</label>
              <input
                value={catForm.name}
                onChange={e => setCatForm(f => ({ ...f, name: e.target.value }))}
                placeholder="예: 1주차 세미, 기업분석"
                className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                onKeyDown={e => { if (e.key === 'Enter') handleCreateCategory() }}
              />
            </div>
            <button
              onClick={handleCreateCategory}
              disabled={catFormLoading || !catForm.name.trim()}
              className="px-4 py-2 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary/90 transition-colors cursor-pointer disabled:opacity-50"
            >
              {catFormLoading ? '추가 중...' : '추가'}
            </button>
            <button
              onClick={() => { setShowCatForm(false); setCatForm({ name: '', displayOrder: 0 }) }}
              className="px-3 py-2 text-sm text-slate-500 hover:text-slate-700 cursor-pointer"
            >
              취소
            </button>
          </div>
        )}

        <DndContext collisionDetection={pointerWithin} onDragStart={(e) => handleDragStartForSection(e, 'CATEGORY')} onDragEnd={(e) => handleSectionDragEnd(e, 'CATEGORY')}>
          <RootDropZone id="root-CATEGORY">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-100">
                  <th className="w-8"></th>
                  <th className="text-left text-xs font-semibold text-slate-500 px-4 py-3">카테고리 이름</th>
                  <th className="text-right text-xs font-semibold text-slate-500 px-4 py-3 w-24">조작</th>
                </tr>
              </thead>
                <tbody className="divide-y divide-slate-50">
                  {categories.length === 0 ? (
                    <tr>
                      <td colSpan={3} className="px-4 py-8 text-center text-xs text-slate-400">카테고리가 없습니다</td>
                    </tr>
                  ) : (() => {
                    const { rootItems, folderGroups } = groupByFolder(categories, 'CATEGORY')
                    const renderCatRow = (c, inFolder, folderColor) => editingCatId === c.id ? (
                    <tr key={c.id} className="bg-primary/5">
                      <td className="w-8"></td>
                      <td className="px-4 py-2">
                        <input value={editCatForm.name} onChange={e => setEditCatForm(f => ({ ...f, name: e.target.value }))} className="w-full px-2 py-1 text-sm border border-slate-200 rounded focus:outline-none focus:ring-1 focus:ring-primary/30" onKeyDown={e => { if (e.key === 'Enter') handleUpdateCategory(c.id) }} />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <div className="flex justify-end gap-1">
                          <button onClick={() => handleUpdateCategory(c.id)} disabled={editCatLoading || !editCatForm.name.trim()} className="text-xs text-primary hover:text-primary/80 cursor-pointer disabled:opacity-50">{editCatLoading ? '...' : '저장'}</button>
                          <button onClick={() => setEditingCatId(null)} className="text-xs text-slate-400 hover:text-slate-600 cursor-pointer">취소</button>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    <DndItemRow key={c.id} dragId={`drag-${c.id}`} dropId={`item-${c.id}`} isInFolder={inFolder} isDimmed={!!activeDragFolderId && c.folderId !== activeDragFolderId} folderColor={folderColor}>
                      <td className="px-4 py-3">
                        <span className="text-sm font-medium text-slate-900">{c.name}</span>
                      </td>
                      <td className="px-4 py-3 text-right" onPointerDown={e => e.stopPropagation()}>
                        <div className="flex justify-end gap-2">
                          <button onClick={() => startEditCategory(c)} className="text-xs text-slate-400 hover:text-primary cursor-pointer">수정</button>
                          <button onClick={() => handleDeleteCategory(c)} className="text-xs text-slate-400 hover:text-red-500 cursor-pointer">삭제</button>
                        </div>
                      </td>
                    </DndItemRow>
                  )
                    return [
                      ...folderGroups.map(({ folder, items }, idx) => {
                        const color = TEAM_COLORS[idx % TEAM_COLORS.length]
                        return [
                        <GroupHeaderRow
                          key={`folder-${folder.id}`} folder={folder} colSpan={3} itemCount={items.length}
                          collapsed={!!collapsedFolders[folder.id]} onToggle={toggleFolderCollapsed}
                          editingFolderId={editingFolderId} editFolderName={editFolderName}
                          setEditingFolderId={setEditingFolderId} setEditFolderName={setEditFolderName}
                          onRename={handleRenameFolder} onDelete={handleDeleteFolder}
                          isActiveDragSource={activeDragFolderId === folder.id}
                          folderColor={color}
                        />,
                        ...(!collapsedFolders[folder.id] ? items.map(c => renderCatRow(c, true, color)) : [])
                      ]}),
                      ...rootItems.map(c => renderCatRow(c, false)),
                    ].flat()
                  })()}
                </tbody>
            </table>
          </div>
          </RootDropZone>
          <DragOverlay>
            {activeDragId && <div className="px-3 py-2 bg-white rounded-lg shadow-lg border border-primary/30 text-sm font-medium">{getDragItemName(activeDragId, 'CATEGORY')}</div>}
          </DragOverlay>
        </DndContext>
        </div>
      </CollapsibleSection>

    </>
  )
}

// ========== DnD 보조 컴포넌트 ==========

// ========== 태그 유형 뱃지 ==========

const TAG_TYPE_STYLES = {
  SYSTEM: { label: '시스템', className: 'bg-slate-100 text-slate-500' },
  TEAM_LEADER: { label: '팀장', className: 'bg-blue-50 text-blue-600' },
  TEAM_MEMBER: { label: '팀원', className: 'bg-green-50 text-green-600' },
  CUSTOM: { label: '커스텀', className: 'bg-purple-50 text-purple-600' },
}

function TagTypeBadge({ type }) {
  const style = TAG_TYPE_STYLES[type]
  if (!style) return null
  return (
    <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${style.className}`}>
      {style.label}
    </span>
  )
}

// ========== DnD 보조 컴포넌트 ==========

const BOARD_TYPE_STYLES = {
}

function BoardTypeBadge({ type }) {
  const style = BOARD_TYPE_STYLES[type]
  if (!style) return null
  return (
    <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${style.className}`}>
      {style.label}
    </span>
  )
}

// ========== DnD 폴더링 컴포넌트 ==========

function RootDropZone({ id, children }) {
  const { setNodeRef, isOver } = useDroppable({ id })
  return (
    <div ref={setNodeRef} className={`transition-colors ${isOver ? 'ring-2 ring-primary/20 ring-inset rounded-xl' : ''}`}>
      {children}
    </div>
  )
}

function DndItemRow({ dragId, dropId, children, isInFolder, isDimmed, folderColor }) {
  const { attributes, listeners, setNodeRef: setDragRef, isDragging } = useDraggable({ id: dragId })
  const { setNodeRef: setDropRef, isOver } = useDroppable({ id: dropId })

  return (
    <tr
      ref={(node) => { setDragRef(node); setDropRef(node) }}
      className={`
        ${isDragging ? 'opacity-30' : ''}
        ${isOver && !isDragging ? 'ring-2 ring-primary/40 ring-inset bg-primary/5' : ''}
        ${isInFolder && folderColor ? folderColor.folderBg : ''}
        ${isDimmed ? 'opacity-40' : ''}
        hover:bg-slate-50/50 transition-all
      `}
    >
      <td
        className={`pl-2 w-8 cursor-grab active:cursor-grabbing ${isInFolder && folderColor ? `border-l-[3px] ${folderColor.folderL}` : ''}`}
        {...attributes}
        {...listeners}
      >
        <span className="text-slate-300 px-1">⠿</span>
      </td>
      {children}
    </tr>
  )
}

function GroupHeaderRow({ folder, colSpan, itemCount, collapsed, onToggle, editingFolderId, editFolderName, setEditingFolderId, setEditFolderName, onRename, onDelete, isActiveDragSource, folderColor }) {
  const isEditing = editingFolderId === folder.id
  const { setNodeRef, isOver } = useDroppable({ id: `folder-${folder.id}` })

  return (
    <tr ref={setNodeRef} className={`border-y ${isActiveDragSource ? 'border-primary/50 bg-primary/5 ring-2 ring-primary/30 ring-inset' : 'border-slate-200/60'} ${isOver ? 'bg-primary/10' : !isActiveDragSource ? 'bg-slate-50/80' : ''} transition-colors`}>
      <td colSpan={colSpan} className={`px-4 py-2 border-l-[3px] ${folderColor ? folderColor.folderL : 'border-l-blue-400/50'}`}>
        <div className="flex items-center gap-2">
          <button onClick={() => onToggle(folder.id)} className="cursor-pointer p-0.5">
            <svg className={`w-3 h-3 text-slate-400 transition-transform ${collapsed ? '' : 'rotate-90'}`} fill="currentColor" viewBox="0 0 20 20">
              <path d="M6 4l8 6-8 6V4z" />
            </svg>
          </button>
          {isEditing ? (
            <input
              value={editFolderName}
              onChange={e => setEditFolderName(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') onRename(folder.id); if (e.key === 'Escape') setEditingFolderId(null) }}
              onBlur={() => onRename(folder.id)}
              autoFocus
              className="text-sm font-semibold text-slate-700 bg-transparent border-b border-slate-400 focus:outline-none px-0 py-0 w-40"
            />
          ) : (
            <span
              className="text-sm font-semibold text-slate-700 cursor-pointer hover:text-slate-900"
              onDoubleClick={() => { setEditingFolderId(folder.id); setEditFolderName(folder.name) }}
              title="더블클릭하여 이름 변경"
            >
              {folder.name}
            </span>
          )}
          <span className="text-xs text-slate-400">({itemCount})</span>
          <div className="flex-1" />
          {!isEditing && (
            <button
              onClick={() => onDelete(folder)}
              className="text-xs text-slate-400 hover:text-red-500 cursor-pointer px-1 opacity-0 group-hover:opacity-100 transition-opacity"
              style={{ opacity: 1 }}
              title="그룹 해제"
            >
              ✕
            </button>
          )}
        </div>
      </td>
    </tr>
  )
}

// ========== 회장단 캐러셀 섹션 (DnD 드롭 영역) ==========

function CarouselSection({ carouselMembers, collapsed, onToggleCollapse, addOpen, onToggleAdd, addMembershipId, setAddMembershipId, addRole, setAddRole, candidates, onAdd, onRemove, editingId, editRole, setEditRole, onStartEdit, onSaveRole, onCancelEdit, dndDisabled }) {
  const { isOver, setNodeRef } = useDroppable({ id: 'fv-carousel' })

  return (
    <div
      ref={setNodeRef}
      className={`rounded-xl border-2 transition-all border-amber-300 bg-amber-50/40 ${
        isOver ? 'ring-2 ring-amber-400/40 scale-[1.01]' : ''
      }`}
    >
      {/* 헤더 */}
      <div className="flex items-center gap-2 px-4 py-3">
        <button onClick={onToggleCollapse} className="cursor-pointer p-0.5 shrink-0">
          <svg className={`w-3 h-3 text-slate-400 transition-transform ${collapsed ? '' : 'rotate-90'}`} fill="currentColor" viewBox="0 0 20 20">
            <path d="M6 4l8 6-8 6V4z" />
          </svg>
        </button>
        <div className="w-2.5 h-2.5 rounded-full shrink-0 bg-amber-400" />
        <span className="text-sm font-bold text-amber-700">회장단</span>
        <span className="text-xs text-slate-400">({carouselMembers.length}명)</span>
        <span className="text-xs px-1.5 py-0.5 rounded bg-amber-100 text-amber-600 shrink-0">홈 캐러셀</span>
        <div className="flex-1" />
        {!collapsed && (
          <button
            onClick={onToggleAdd}
            className="text-xs text-amber-600 hover:text-amber-700 cursor-pointer font-medium"
          >
            {addOpen ? '취소' : '+ 추가'}
          </button>
        )}
      </div>

      {/* 멤버 추가 폼 */}
      {!collapsed && addOpen && (
        <div className="px-4 pb-2">
          <div className="flex items-end gap-2 p-2 bg-white rounded-lg border border-amber-200">
            <div className="flex-1 min-w-0">
              <label className="block text-xs text-slate-500 mb-0.5">멤버</label>
              <select
                value={addMembershipId}
                onChange={e => setAddMembershipId(e.target.value)}
                className="w-full text-xs border border-slate-200 rounded px-2 py-1 bg-white focus:outline-none focus:ring-1 focus:ring-amber-300"
              >
                <option value="">선택...</option>
                {candidates.map(m => (
                  <option key={m.membershipId} value={m.membershipId}>{m.name}</option>
                ))}
              </select>
            </div>
            <div className="w-24 shrink-0">
              <label className="block text-xs text-slate-500 mb-0.5">직책</label>
              <input
                value={addRole}
                onChange={e => setAddRole(e.target.value)}
                placeholder="회장"
                className="w-full text-xs border border-slate-200 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-amber-300"
                maxLength={20}
              />
            </div>
            <button
              onClick={onAdd}
              disabled={!addMembershipId}
              className="px-3 py-1 text-xs font-medium text-white bg-amber-500 rounded hover:bg-amber-600 cursor-pointer disabled:opacity-40 shrink-0"
            >
              추가
            </button>
          </div>
        </div>
      )}

      {/* 멤버 목록 */}
      {!collapsed && (
        <div className="px-2 pb-2">
          {carouselMembers.length === 0 ? (
            <p className="text-xs text-slate-400 py-3 px-2">회장단이 비어 있습니다. 멤버를 드래그하여 추가하세요.</p>
          ) : (
            <div className="grid grid-cols-1 gap-0.5">
              {carouselMembers.map(cm => (
                <CarouselMemberRow
                  key={cm.carouselId}
                  cm={cm}
                  onRemove={onRemove}
                  editingId={editingId}
                  editRole={editRole}
                  setEditRole={setEditRole}
                  onStartEdit={onStartEdit}
                  onSaveRole={onSaveRole}
                  onCancelEdit={onCancelEdit}
                  dndDisabled={dndDisabled}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function CarouselMemberRow({ cm, onRemove, editingId, editRole, setEditRole, onStartEdit, onSaveRole, onCancelEdit, dndDisabled }) {
  const dragId = `fc:${cm.id}:${cm.carouselId}`
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: dragId,
    disabled: !!dndDisabled,
  })

  const style = transform ? {
    transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
  } : undefined

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...(dndDisabled ? {} : { ...listeners, ...attributes })}
      className={`flex items-center gap-2 px-3 py-2 rounded-lg bg-white/70 hover:bg-white transition-colors group ${
        dndDisabled ? 'cursor-default' : 'cursor-grab active:cursor-grabbing'
      } ${isDragging ? 'opacity-30' : ''}`}
    >
      <div className="w-7 h-7 rounded-full bg-amber-50 flex items-center justify-center text-xs font-medium text-amber-600 shrink-0">
        {cm.name?.charAt(0)}
      </div>
      <div className="flex-1 min-w-0 flex items-center gap-2">
        <span className="text-sm font-medium text-slate-900">{cm.name}</span>
        {editingId === cm.carouselId ? (
          <input
            autoFocus
            value={editRole}
            onChange={e => setEditRole(e.target.value)}
            onBlur={() => onSaveRole(cm.carouselId)}
            onKeyDown={e => { if (e.key === 'Enter') onSaveRole(cm.carouselId); if (e.key === 'Escape') onCancelEdit() }}
            onClick={e => e.stopPropagation()}
            onPointerDown={e => e.stopPropagation()}
            className="text-xs px-1.5 py-0.5 border border-amber-300 rounded bg-white focus:outline-none focus:ring-1 focus:ring-amber-400 w-20"
            maxLength={20}
          />
        ) : (
          <span
            className="text-xs px-1.5 py-0.5 bg-amber-100 text-amber-700 rounded cursor-pointer hover:bg-amber-200 transition-colors"
            onDoubleClick={() => onStartEdit(cm.carouselId, cm.roleName)}
            onPointerDown={e => e.stopPropagation()}
            title="더블클릭하여 직책 수정"
          >
            {cm.roleName || '멤버'}
          </span>
        )}
        {cm.firstGeneration && (
          <span className="text-xs text-slate-400">{cm.firstGeneration}</span>
        )}
      </div>
      <button
        onClick={() => onRemove(cm.carouselId, cm.name)}
        onPointerDown={e => e.stopPropagation()}
        className="shrink-0 p-1 rounded text-slate-300 hover:text-red-500 hover:bg-red-50 transition-colors cursor-pointer opacity-0 group-hover:opacity-100"
        title="회장단에서 제거"
      >
        <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  )
}
