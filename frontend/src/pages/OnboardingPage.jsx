import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuthStore from '../store/authStore'
import api from '../lib/api'

// 로그아웃 아이콘 (SVG)
const LogoutIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <polyline points="16 17 21 12 16 7" />
    <line x1="21" y1="12" x2="9" y2="12" />
  </svg>
)

export default function OnboardingPage() {
  const navigate = useNavigate()
  const fetchMe = useAuthStore(s => s.fetchMe)
  const logout = useAuthStore(s => s.logout)

  const [schools, setSchools] = useState([])
  const [clubs, setClubs] = useState([])
  const [form, setForm] = useState({
    schoolId: '',
    clubId: '',
    name: '',
    studentId: '',
    phone: '',
  })
  const [step, setStep] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // 학교 목록 로드
  const loadSchools = () => {
    setError('')
    api.get('/onboarding/schools')
      .then(setSchools)
      .catch(() => setError('학교 목록을 불러오지 못했습니다'))
  }
  useEffect(() => { loadSchools() }, [])

  // 학교 선택 시 동아리 목록 로드
  useEffect(() => {
    if (!form.schoolId) {
      setClubs([])
      return
    }
    api.get(`/onboarding/schools/${form.schoolId}/clubs`)
      .then(setClubs)
      .catch(() => setError('동아리 목록을 불러오지 못했습니다'))
  }, [form.schoolId])

  const handleChange = (field, value) => {
    setForm(prev => {
      const next = { ...prev, [field]: value }
      if (field === 'schoolId') next.clubId = ''
      return next
    })
    setError('')
  }

  const canProceedStep1 = form.schoolId && form.clubId
  const isValidStudentId = /^\d{6,10}$/.test(form.studentId.trim())
  const canSubmit = form.name.trim() && isValidStudentId && form.phone.trim()

  const handleSubmit = async () => {
    setLoading(true)
    setError('')
    try {
      await api.post('/onboarding/apply', {
        clubId: Number(form.clubId),
        name: form.name.trim(),
        studentId: form.studentId.trim(),
        phone: form.phone.trim(),
      })
      await fetchMe()
      navigate('/pending')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#FAF8F5] flex items-center justify-center p-4 animate-fade-in-up">
      <div className="w-full max-w-[440px]">
        {/* 헤더 */}
        <div className="text-center mb-8">
          <h1 className="text-xl font-bold text-slate-900">가입 신청</h1>
          <p className="text-sm text-slate-500 mt-1">
            {step === 1 ? '소속 학교와 동아리를 선택해주세요' : '개인 정보를 입력해주세요'}
          </p>
        </div>

        {/* 다른 계정으로 로그인 */}
        <button
          onClick={() => { logout(); navigate('/login') }}
          className="flex items-center justify-center gap-1.5 mx-auto mb-6 text-xs text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
        >
          <LogoutIcon />
          다른 계정으로 로그인
        </button>

        {/* 스텝 인디케이터 */}
        <div className="flex items-center justify-center gap-2 mb-8">
          <div className={`flex items-center justify-center w-8 h-8 rounded-full text-sm font-bold ${
            step >= 1 ? 'bg-primary text-white' : 'bg-slate-200 text-slate-500'
          }`}>1</div>
          <div className={`w-12 h-0.5 ${step >= 2 ? 'bg-primary' : 'bg-slate-200'}`} />
          <div className={`flex items-center justify-center w-8 h-8 rounded-full text-sm font-bold ${
            step >= 2 ? 'bg-primary text-white' : 'bg-slate-200 text-slate-500'
          }`}>2</div>
        </div>

        {/* 에러 메시지 */}
        {error && (
          <div className="mb-4 px-4 py-3 bg-red-50 border border-red-200 rounded-xl text-sm text-red-600 flex items-center justify-between">
            <span>{error}</span>
            {schools.length === 0 && (
              <button onClick={loadSchools} className="ml-3 text-xs font-medium text-red-700 hover:text-red-900 underline cursor-pointer">재시도</button>
            )}
          </div>
        )}

        {/* 카드 */}
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 shadow-sm p-6">
          {step === 1 ? (
            /* Step 1: 학교/동아리 선택 */
            <div className="space-y-5">
              {/* 학교 선택 */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">학교</label>
                <select
                  value={form.schoolId}
                  onChange={(e) => handleChange('schoolId', e.target.value)}
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary appearance-none cursor-pointer"
                >
                  <option value="">학교를 선택하세요</option>
                  {schools.map(school => (
                    <option key={school.id} value={school.id}>{school.name}</option>
                  ))}
                </select>
              </div>

              {/* 동아리 선택 */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">동아리</label>
                {!form.schoolId ? (
                  <div className="px-4 py-3 bg-slate-50 border border-slate-200 shadow-sm rounded-xl text-sm text-slate-400">
                    먼저 학교를 선택하세요
                  </div>
                ) : clubs.length === 0 ? (
                  <div className="px-4 py-3 bg-slate-50 border border-slate-200 shadow-sm rounded-xl text-sm text-slate-400">
                    등록된 동아리가 없습니다
                  </div>
                ) : (
                  <div className="space-y-2">
                    {clubs.map(club => (
                      <button
                        key={club.id}
                        onClick={() => handleChange('clubId', String(club.id))}
                        className={`w-full px-4 py-3 rounded-xl text-sm text-left transition-all cursor-pointer ${
                          form.clubId === String(club.id)
                            ? 'bg-primary/5 border-2 border-primary text-primary font-medium'
                            : 'bg-slate-50 border border-slate-200 text-slate-700 hover:bg-slate-100'
                        }`}
                      >
                        {club.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <button
                onClick={() => setStep(2)}
                disabled={!canProceedStep1}
                className={`w-full py-3 rounded-xl text-sm font-medium transition-all cursor-pointer ${
                  canProceedStep1
                    ? 'bg-primary text-white hover:bg-primary-hover'
                    : 'bg-slate-100 text-slate-400 cursor-not-allowed'
                }`}
              >
                다음
              </button>
            </div>
          ) : (
            /* Step 2: 개인정보 입력 */
            <div className="space-y-5">
              {/* 선택된 학교/동아리 표시 */}
              <div className="flex items-center gap-2 px-3 py-2 bg-blue-50 rounded-lg">
                <span className="text-xs text-blue-600">
                  {schools.find(s => String(s.id) === form.schoolId)?.name} · {clubs.find(c => String(c.id) === form.clubId)?.name}
                </span>
                <button
                  onClick={() => setStep(1)}
                  className="text-xs text-blue-400 hover:text-blue-600 ml-auto cursor-pointer"
                >
                  변경
                </button>
              </div>

              {/* 안내 메시지 */}
              <div className="px-3 py-2.5 bg-amber-50 border border-amber-200 rounded-lg">
                <p className="text-xs text-amber-700 leading-relaxed">
                  아래 정보는 동아리 회장이 가입자를 식별하는 데 사용됩니다.
                  정확하지 않은 정보는 승인이 반려될 수 있습니다.
                </p>
              </div>

              {/* 이름 */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">이름 (실명)</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => handleChange('name', e.target.value)}
                  placeholder="홍길동"
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                />
              </div>

              {/* 학번 */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">학번</label>
                <input
                  type="text"
                  value={form.studentId}
                  onChange={(e) => handleChange('studentId', e.target.value.replace(/\D/g, ''))}
                  placeholder="2024XXXXX"
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                />
                {form.studentId && !isValidStudentId && (
                  <p className="text-xs text-slate-500 mt-1">학번은 6~10자리 숫자여야 합니다</p>
                )}
              </div>

              {/* 전화번호 */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-2">전화번호</label>
                <input
                  type="tel"
                  value={form.phone}
                  onChange={(e) => handleChange('phone', e.target.value)}
                  placeholder="010-0000-0000"
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                />
              </div>

              {/* 버튼들 */}
              <div className="flex gap-3">
                <button
                  onClick={() => setStep(1)}
                  className="px-6 py-3 rounded-xl text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 transition-colors cursor-pointer"
                >
                  이전
                </button>
                <button
                  onClick={handleSubmit}
                  disabled={!canSubmit || loading}
                  className={`flex-1 py-3 rounded-xl text-sm font-medium transition-all cursor-pointer ${
                    canSubmit && !loading
                      ? 'bg-primary text-white hover:bg-primary-hover'
                      : 'bg-slate-100 text-slate-400 cursor-not-allowed'
                  }`}
                >
                  {loading ? '처리 중...' : '가입 신청'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
