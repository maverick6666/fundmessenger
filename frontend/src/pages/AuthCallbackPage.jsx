import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import useAuthStore from '../store/authStore'

/**
 * 소셜 로그인 콜백 처리 페이지
 * 백엔드에서 /auth/callback?token=xxx&status=xxx 로 리다이렉트됨
 */
export default function AuthCallbackPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const handleAuthCallback = useAuthStore(s => s.handleAuthCallback)

  useEffect(() => {
    const token = params.get('token')
    const status = params.get('status')

    if (!token || !status) {
      navigate('/login', { replace: true })
      return
    }

    // 다른 컴포넌트가 인증 미완료 상태로 판단하지 않도록 loading 유지
    useAuthStore.setState({ loading: true })

    handleAuthCallback(token, status).then((userStatus) => {
      switch (userStatus) {
        case 'SUPER_ADMIN':
          navigate('/admin-select', { replace: true })
          break
        case 'NO_CLUB':
          navigate('/onboarding', { replace: true })
          break
        case 'PENDING':
          navigate('/pending', { replace: true })
          break
        case 'APPROVED': {
          const returnTo = sessionStorage.getItem('fm_returnTo')
          sessionStorage.removeItem('fm_returnTo')
          navigate(returnTo || '/club', { replace: true })
          break
        }
        default:
          navigate('/login', { replace: true })
      }
    }).catch(() => {
      navigate('/login', { replace: true })
    })
  }, [])

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="text-center">
        <div className="animate-spin w-8 h-8 border-4 border-primary border-t-transparent rounded-full mx-auto mb-4" />
        <p className="text-sm text-slate-500">로그인 처리 중...</p>
      </div>
    </div>
  )
}
