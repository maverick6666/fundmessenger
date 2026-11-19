import { create } from 'zustand'
import api from '../lib/api'

/**
 * 인증 상태 관리 (Zustand)
 * - token: JWT 토큰
 * - user: { userId, email, userStatus }
 * - loading: 초기 로딩 중
 */
const useAuthStore = create((set, get) => ({
  token: localStorage.getItem('fm_token'),
  user: null,
  loading: true,

  /** 토큰 설정 + 사용자 정보 조회 */
  setToken: (token) => {
    api.setToken(token)
    set({ token })
  },

  /** 로그아웃 */
  logout: () => {
    api.removeToken()
    set({ token: null, user: null })
  },

  /** 현재 사용자 정보 조회 (앱 시작 시 호출) */
  fetchMe: async () => {
    const token = localStorage.getItem('fm_token')
    if (!token) {
      set({ loading: false, user: null, token: null })
      return
    }
    try {
      const data = await api.get('/auth/me')
      set({
        user: {
          userId: data.userId,
          email: data.email,
          userStatus: data.userStatus,
          superAdmin: data.superAdmin,
          pendingSchoolName: data.pendingSchoolName,
          pendingClubName: data.pendingClubName,
          pendingAppliedAt: data.pendingAppliedAt,
        },
        token: data.token, // 갱신된 토큰
        loading: false,
      })
      api.setToken(data.token)
    } catch {
      api.removeToken()
      set({ token: null, user: null, loading: false })
    }
  },

  /** DEV 로그인 */
  devLogin: async (role) => {
    const data = await api.post('/auth/dev-login', { role })
    api.setToken(data.token)
    set({
      token: data.token,
      user: {
        userId: data.userId,
        email: data.email,
        userStatus: data.userStatus,
        superAdmin: data.superAdmin,
        pendingSchoolName: data.pendingSchoolName,
        pendingClubName: data.pendingClubName,
        pendingAppliedAt: data.pendingAppliedAt,
      },
    })
    return data
  },

  /** 소셜 로그인 콜백 처리
   *  URL에서 전달받은 token/status를 즉시 사용하여 상태 설정.
   *  /auth/me 호출 없이 처리 — api 401 핸들러의 window.location.href 충돌 방지.
   *  전체 사용자 정보는 이후 페이지에서 fetchMe가 로드함.
   */
  handleAuthCallback: async (token, status) => {
    api.setToken(token)
    set({
      token,
      user: { userStatus: status },
      loading: false,
    })
    return status
  },
}))

// 크로스탭 로그아웃 동기화 — 다른 탭에서 토큰 삭제 시 현재 탭도 로그아웃
// 모듈 레벨 핸들러 참조 보관 (HMR 중복 등록 방지)
const _storageHandler = (e) => {
  if (e.key === 'fm_token' && e.newValue === null) {
    useAuthStore.getState().logout()
    window.location.href = '/login'
  }
}
if (typeof window !== 'undefined') {
  window.removeEventListener('storage', _storageHandler)
  window.addEventListener('storage', _storageHandler)
}

export default useAuthStore
