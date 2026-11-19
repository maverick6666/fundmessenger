import { create } from 'zustand'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'

const useClubStore = create((set, get) => ({
  clubInfo: null,
  members: [],
  teams: [],
  joinRequests: [],
  generations: [],
  currentGeneration: null, // null = 현재 기수, {id, name} = 아카이브 모드
  loading: false,
  error: null,

  /** 내 동아리 정보 조회 */
  fetchMyClub: async () => {
    set({ loading: true, error: null })
    try {
      const data = await api.get('/my/club')
      // myTags에서 팀 정보 추출 (하위 호환)
      const myTeams = (data.myTags || [])
        .filter(t => t.tagType === 'TEAM_LEADER' || t.tagType === 'TEAM_MEMBER')
        .map(t => ({ teamId: t.teamId, teamName: t.teamName, teamRole: t.tagType === 'TEAM_LEADER' ? 'LEADER' : 'MEMBER', teamType: t.teamType || null, systemTeam: t.systemTeam || false }))
      set({ clubInfo: { ...data, myTeams }, loading: false })
      return data
    } catch (err) {
      set({ error: err.message, loading: false })
      throw err
    }
  },

  /** 동아리 멤버 목록 */
  fetchMembers: async (clubId) => {
    try {
      const data = await api.get(`/clubs/${clubId}/members`)
      set({ members: data })
      return data
    } catch (err) {
      handleApiError(err, '멤버 목록 조회')
      throw err
    }
  },

  /** 팀 목록 */
  fetchTeams: async (clubId) => {
    try {
      const data = await api.get(`/clubs/${clubId}/teams`)
      set({ teams: data })
      return data
    } catch (err) {
      handleApiError(err, '팀 목록 조회')
      throw err
    }
  },

  /** 가입 요청 목록 */
  fetchJoinRequests: async (clubId) => {
    try {
      const data = await api.get(`/clubs/${clubId}/admin/join-requests`)
      set({ joinRequests: data })
      return data
    } catch (err) {
      handleApiError(err, '가입 요청 조회')
      throw err
    }
  },

  /** 가입 요청 승인 */
  approveRequest: async (clubId, membershipId) => {
    const data = await api.put(`/clubs/${clubId}/admin/join-requests/${membershipId}/approve`)
    set(state => ({ joinRequests: state.joinRequests.filter(r => r.membershipId !== membershipId) }))
    return data
  },

  /** 가입 요청 거절 */
  rejectRequest: async (clubId, membershipId) => {
    const data = await api.put(`/clubs/${clubId}/admin/join-requests/${membershipId}/reject`)
    set(state => ({ joinRequests: state.joinRequests.filter(r => r.membershipId !== membershipId) }))
    return data
  },

  /** 기수 목록 조회 */
  fetchGenerations: async (clubId) => {
    try {
      const data = await api.get(`/clubs/${clubId}/generations`)
      set({ generations: data })
      return data
    } catch (err) {
      handleApiError(err, '기수 목록 조회')
      throw err
    }
  },

  /** 기수 일괄 생성 */
  batchCreateGenerations: async (clubId, startNumber, endNumber) => {
    const data = await api.post(`/clubs/${clubId}/generations/batch`, { startNumber, endNumber })
    set(state => ({ generations: [...state.generations, ...data] }))
    return data
  },

  /** 기수 삭제 */
  deleteGeneration: async (clubId, generationId) => {
    await api.delete(`/clubs/${clubId}/generations/${generationId}`)
    set(state => ({ generations: state.generations.filter(g => g.id !== generationId) }))
  },

  /** 아카이브 모드 전환 */
  setCurrentGeneration: (gen) => set({ currentGeneration: gen }),

  /** 상태 초기화 */
  reset: () => set({ clubInfo: null, members: [], teams: [], joinRequests: [], generations: [], currentGeneration: null, loading: false, error: null }),
}))

export default useClubStore
