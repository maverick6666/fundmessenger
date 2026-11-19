import { useNavigate } from 'react-router-dom'
import useAuthStore from '../store/authStore'

export default function PendingApprovalPage() {
  const navigate = useNavigate()
  const logout = useAuthStore(s => s.logout)
  const user = useAuthStore(s => s.user)

  return (
    <div className="min-h-screen bg-[#FAF8F5] flex items-center justify-center p-4">
      <div className="w-full max-w-[400px]">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 shadow-sm p-8 text-center">
          {/* 아이콘 */}
          <div className="inline-flex items-center justify-center w-16 h-16 bg-amber-50 rounded-full mb-5">
            <svg className="w-8 h-8 text-amber-500" fill="none" viewBox="0 0 24 24" strokeWidth="1.5" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
            </svg>
          </div>

          {/* 메시지 */}
          <h2 className="text-lg font-bold text-slate-900 mb-2">승인 대기 중</h2>
          <p className="text-sm text-slate-500 leading-relaxed mb-6">
            가입 신청이 완료되었습니다.<br />
            동아리 회장의 승인을 기다리고 있어요.
          </p>

          {/* 신청 정보 */}
          <div className="bg-slate-50 rounded-xl p-4 mb-6 text-left">
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">학교</span>
                <span className="text-slate-800 font-medium">{user?.pendingSchoolName || '-'}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">동아리</span>
                <span className="text-slate-800 font-medium">{user?.pendingClubName || '-'}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">신청일</span>
                <span className="text-slate-800 font-medium">{user?.pendingAppliedAt || '-'}</span>
              </div>
            </div>
          </div>

          {/* 안내 */}
          <div className="text-xs text-slate-400 space-y-1 mb-6">
            <p>승인되면 자동으로 접속할 수 있습니다.</p>
            <p>문의사항은 동아리 회장단에게 연락해주세요.</p>
          </div>

          {/* 로그아웃 */}
          <button
            onClick={() => { logout(); navigate('/login') }}
            className="text-sm text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
          >
            다른 계정으로 로그인
          </button>
        </div>
      </div>
    </div>
  )
}
