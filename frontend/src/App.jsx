import { useEffect, lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import useAuthStore from './store/authStore'
import ErrorBoundary from './components/ErrorBoundary'
import LoginPage from './pages/LoginPage'
import AuthCallbackPage from './pages/AuthCallbackPage'
import OnboardingPage from './pages/OnboardingPage'
import PendingApprovalPage from './pages/PendingApprovalPage'
import ClubLayout from './layouts/ClubLayout'
import ClubHomePage from './pages/ClubHomePage'
import { UnsavedChangesProvider } from './components/UnsavedChangesProvider'
import SessionExpiredModal from './components/SessionExpiredModal'

// 무거운 페이지는 lazy load — 초기 번들 크기 감소
const AdminPage = lazy(() => import('./pages/AdminPage'))
const AdminSelectPage = lazy(() => import('./pages/AdminSelectPage'))
const TeamSpacePage = lazy(() => import('./pages/TeamSpacePage'))
const ClubAdminPage = lazy(() => import('./pages/ClubAdminPage'))
const BoardPage = lazy(() => import('./pages/BoardPage'))
const DrivePage = lazy(() => import('./pages/DrivePage'))
const JongtobangPage = lazy(() => import('./pages/JongtobangPage'))
const MyPage = lazy(() => import('./pages/MyPage'))
const FundDashboardPage = lazy(() => import('./pages/FundDashboardPage'))
const FundRequestsPage = lazy(() => import('./pages/FundRequestsPage'))
const FundPositionsPage = lazy(() => import('./pages/FundPositionsPage'))
const FundStatsPage = lazy(() => import('./pages/FundStatsPage'))
const FundPositionDetailPage = lazy(() => import('./pages/FundPositionDetailPage'))
const FundReportsPage = lazy(() => import('./pages/FundReportsPage'))

/** Suspense fallback — 페이지 전환 시 로딩 스피너 */
function PageLoader() {
  return (
    <div className="min-h-[50vh] flex items-center justify-center">
      <div className="animate-spin w-8 h-8 border-4 border-indigo-500 border-t-transparent rounded-full" />
    </div>
  )
}

/** 인증 필요 라우트 */
function ProtectedRoute({ children, allowedStatus }) {
  const { token, user, loading } = useAuthStore()

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="animate-spin w-8 h-8 border-4 border-primary border-t-transparent rounded-full" />
      </div>
    )
  }

  if (!token) return <Navigate to="/login" replace />

  // 특정 상태만 허용 (SUPER_ADMIN은 APPROVED 라우트도 접근 가능 — 동아리 회원 겸직)
  if (allowedStatus && user?.userStatus !== allowedStatus) {
    const isSuperAdminAccessingClub = user?.userStatus === 'SUPER_ADMIN' && (allowedStatus === 'APPROVED' || allowedStatus === 'NO_CLUB')
    if (!isSuperAdminAccessingClub) {
      switch (user?.userStatus) {
        case 'SUPER_ADMIN': return <Navigate to="/admin" replace />
        case 'NO_CLUB': return <Navigate to="/onboarding" replace />
        case 'PENDING': return <Navigate to="/pending" replace />
        case 'APPROVED': return <Navigate to="/club" replace />
        default: return <Navigate to="/login" replace />
      }
    }
  }

  return children
}

function App() {
  const fetchMe = useAuthStore(s => s.fetchMe)

  useEffect(() => {
    // auth callback 페이지에서는 fetchMe 스킵 — AuthCallbackPage가 직접 처리
    // loading: true 유지하여 다른 컴포넌트가 user 없이 렌더되지 않도록 함
    if (window.location.pathname === '/auth/callback') return
    fetchMe()
  }, [])

  // Service Worker 등록 (PWA + Push)
  useEffect(() => {
    import('./lib/registerSW.js').then(({ registerServiceWorker }) => registerServiceWorker())
  }, [])

  return (
    <ErrorBoundary>
      <SessionExpiredModal />
      <Suspense fallback={<PageLoader />}>
        <Routes>
          {/* 공개 라우트 */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />

          {/* 인증 필요 + 상태별 라우트 */}
          <Route
            path="/onboarding"
            element={
              <ProtectedRoute allowedStatus="NO_CLUB">
                <OnboardingPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pending"
            element={
              <ProtectedRoute allowedStatus="PENDING">
                <PendingApprovalPage />
              </ProtectedRoute>
            }
          />

          {/* 동아리 라우트 (APPROVED 사용자) */}
          <Route
            path="/club"
            element={
              <ProtectedRoute allowedStatus="APPROVED">
                <UnsavedChangesProvider>
                  <ClubLayout />
                </UnsavedChangesProvider>
              </ProtectedRoute>
            }
          >
            <Route index element={<ClubHomePage />} />
            <Route path="teams/:teamId" element={<TeamSpacePage />} />
            <Route path="boards" element={<BoardPage />} />
            {/* 기존 글쓰기/수정 URL → boards로 리다이렉트 (인라인 편집으로 전환) */}
            <Route path="boards/write" element={<Navigate to="/club/boards?writing=1" replace />} />
            <Route path="boards/:boardId/write" element={<Navigate to="/club/boards?writing=1" replace />} />
            <Route path="boards/:boardId/posts/:postId/edit" element={<Navigate to="/club/boards" replace />} />
            <Route path="drive" element={<DrivePage />} />
            <Route path="jongtobang" element={<JongtobangPage />} />
            <Route path="fund/:teamId" element={<FundDashboardPage />} />
            <Route path="fund/:teamId/positions" element={<FundPositionsPage />} />
            <Route path="fund/:teamId/positions/:positionId" element={<FundPositionDetailPage />} />
            <Route path="fund/:teamId/requests" element={<FundRequestsPage />} />
            <Route path="fund/:teamId/reports" element={<FundReportsPage />} />
            <Route path="fund/:teamId/stats" element={<FundStatsPage />} />
            <Route path="my" element={<MyPage />} />
            <Route path="admin" element={<ClubAdminPage />} />
          </Route>

          {/* 슈퍼어드민 선택 */}
          <Route
            path="/admin-select"
            element={
              <ProtectedRoute allowedStatus="SUPER_ADMIN">
                <AdminSelectPage />
              </ProtectedRoute>
            }
          />

          {/* 슈퍼어드민 */}
          <Route
            path="/admin"
            element={
              <ProtectedRoute allowedStatus="SUPER_ADMIN">
                <AdminPage />
              </ProtectedRoute>
            }
          />

          {/* /home → /club 리다이렉트 (기존 호환) */}
          <Route path="/home" element={<Navigate to="/club" replace />} />

          {/* 기본 리다이렉트 */}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  )
}

export default App
