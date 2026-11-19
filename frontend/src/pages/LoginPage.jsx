import { useNavigate } from 'react-router-dom'
import { useState, useEffect, useRef } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { Smartphone, Download, Share, PlusSquare } from 'lucide-react'
import useAuthStore from '../store/authStore'
import ConfirmModal from '../components/ConfirmModal'

import { API_URL } from '../lib/api'

const isDev = import.meta.env.DEV

/** 인앱 브라우저(카카오톡, 인스타, 페이스북 등) 감지 */
function isInAppBrowser() {
  const ua = navigator.userAgent || ''
  return /KAKAOTALK|FBAN|FBAV|Instagram|Line\/|Snapchat|Twitter|Threads|NAVER|ZumApp|Whale\/\d/i.test(ua)
}

/** 인앱 브라우저 → 외부 브라우저로 현재 페이지 열기 */
function openInExternalBrowser() {
  const url = window.location.href
  const ua = navigator.userAgent || ''

  if (/android/i.test(ua)) {
    // Android: intent 스킴으로 Chrome/기본 브라우저 실행
    const intentUrl = `intent://${url.replace(/https?:\/\//, '')}#Intent;scheme=https;end`
    window.location.href = intentUrl
  } else {
    // iOS: Safari로 열기 (window.open은 인앱에서 차단될 수 있으므로 location 사용)
    window.location.href = url
  }
}

const DEV_ACCOUNTS = [
  { id: 1, name: '슈퍼어드민', key: 'SUPER_ADMIN', icon: '🛡️', desc: '시스템 관리자' },
  { id: 2, name: '홍길동 (DEV_1)', key: 'DEV_1', icon: '👑', desc: '연세VALUE 회장' },
  { id: 3, name: 'Kim Seoyeon (DEV_2)', key: 'DEV_2', icon: '🎓', desc: 'ALUMNI' },
  { id: 4, name: 'Lee Junho (DEV_3)', key: 'DEV_3', icon: '👥', desc: 'Value Fund 팀장' },
  { id: 5, name: 'Park Minji (DEV_4)', key: 'DEV_4', icon: '👤', desc: '일반부원' },
  { id: 6, name: 'Choi Hyunwoo (DEV_5)', key: 'DEV_5', icon: '👥', desc: 'Research 팀장' },
  { id: 7, name: '테스트유저6 (DEV_6)', key: 'DEV_6', icon: '🎓', desc: 'ALUMNI' },
  { id: 8, name: 'DEV 계정 7', key: 'DEV_7', icon: '👤', desc: '미소속' },
]

export default function LoginPage() {
  const navigate = useNavigate()
  const devLogin = useAuthStore(s => s.devLogin)
  const user = useAuthStore(s => s.user)
  const token = useAuthStore(s => s.token)
  const [devLoading, setDevLoading] = useState(null)
  const [alertModal, setAlertModal] = useState({ open: false, message: '' })
  const [inApp] = useState(() => isInAppBrowser())

  /** 이미 로그인된 사용자는 적절한 페이지로 리다이렉트 */
  useEffect(() => {
    if (token && user?.userStatus) {
      switch (user.userStatus) {
        case 'SUPER_ADMIN': navigate('/admin', { replace: true }); break
        case 'NO_CLUB': navigate('/onboarding', { replace: true }); break
        case 'PENDING': navigate('/pending', { replace: true }); break
        case 'APPROVED': navigate('/club', { replace: true }); break
      }
    }
  }, [token, user, navigate])

  /** 소셜 로그인 → 백엔드 OAuth 리다이렉트 */
  const handleSocialLogin = (provider) => {
    if (inApp) {
      openInExternalBrowser()
      return
    }
    window.location.href = `${API_URL}/auth/oauth2/${provider}`
  }

  /** DEV 모드 로그인 */
  const handleDevLogin = async (account) => {
    setDevLoading(account.id)
    try {
      const result = await devLogin(account.key)
      switch (result.userStatus) {
        case 'SUPER_ADMIN': navigate('/admin'); break
        case 'NO_CLUB': navigate('/onboarding'); break
        case 'PENDING': navigate('/pending'); break
        case 'APPROVED': navigate('/club'); break
        default: navigate('/club')
      }
    } catch (err) {
      setAlertModal({ open: true, message: 'DEV 로그인 실패: ' + err.message })
    } finally {
      setDevLoading(null)
    }
  }

  return (
    <div className="min-h-screen bg-[#FAF8F5] flex items-center justify-center p-4 animate-fade-in-up">
      <div className="w-full max-w-[400px]">
        {/* 로고 영역 */}
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-primary rounded-2xl mb-4">
            <span className="text-white text-2xl font-bold">F</span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900 scandi-heading">펀드메신저</h1>
          <p className="text-sm text-slate-500 mt-1">투자 동아리를 위한 올인원 플랫폼</p>
        </div>

        {/* 인앱 브라우저 경고 */}
        {inApp && (
          <div className="mb-4 bg-amber-50 border border-amber-200 rounded-2xl p-4 text-center">
            <p className="text-sm font-semibold text-amber-800 mb-1">인앱 브라우저에서는 로그인이 제한됩니다</p>
            <p className="text-xs text-amber-600 mb-3">카카오톡/인스타그램 등의 내장 브라우저에서는 Google 로그인이 차단됩니다.</p>
            <button
              onClick={openInExternalBrowser}
              className="inline-flex items-center gap-1.5 px-4 py-2 bg-amber-500 text-white text-sm font-medium rounded-lg hover:bg-amber-600 transition-colors cursor-pointer"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" /></svg>
              외부 브라우저로 열기
            </button>
          </div>
        )}

        {/* 소셜 로그인 카드 */}
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 shadow-sm p-6">
          <p className="text-sm font-medium text-slate-700 mb-4 text-center">소셜 계정으로 시작하기</p>

          <div className="space-y-3">
            {/* 구글 */}
            <button
              onClick={() => handleSocialLogin('google')}
              className="w-full flex items-center justify-center gap-3 px-4 py-3 bg-white border border-slate-200 rounded-xl hover:bg-slate-50 transition-colors cursor-pointer"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/>
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
              </svg>
              <span className="text-sm font-medium text-slate-700">Google로 계속하기</span>
            </button>

            {/* 카카오 — 검수 통과 후 활성화 */}
            {/* <button
              onClick={() => handleSocialLogin('kakao')}
              className="w-full flex items-center justify-center gap-3 px-4 py-3 bg-kakao rounded-xl hover:brightness-95 transition-all cursor-pointer"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="#191919">
                <path d="M12 3C6.477 3 2 6.463 2 10.691c0 2.72 1.807 5.109 4.527 6.444l-.934 3.467c-.08.298.254.544.52.384l4.088-2.694c.591.082 1.195.126 1.799.126 5.523 0 10-3.463 10-7.727C22 6.463 17.523 3 12 3z"/>
              </svg>
              <span className="text-sm font-medium text-kakao-text">카카오로 계속하기</span>
            </button> */}

            {/* 네이버 — 검수 통과 후 활성화 */}
            {/* <button
              onClick={() => handleSocialLogin('naver')}
              className="w-full flex items-center justify-center gap-3 px-4 py-3 bg-naver rounded-xl hover:brightness-95 transition-all cursor-pointer"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="white">
                <path d="M16.273 12.845L7.376 0H0v24h7.727V11.155L16.624 24H24V0h-7.727z"/>
              </svg>
              <span className="text-sm font-medium text-white">네이버로 계속하기</span>
            </button> */}
          </div>
        </div>

        {/* 앱 설치 안내 */}
        <InstallGuide />

        {/* DEV 모드 계정 선택기 */}
        {isDev && (
          <div className="mt-6 bg-amber-50 border border-amber-200 rounded-2xl p-4">
            <div className="flex items-center gap-2 mb-3">
              <span className="text-xs font-mono bg-amber-200 text-amber-800 px-2 py-0.5 rounded">DEV</span>
              <span className="text-xs text-amber-700">개발용 테스트 계정</span>
            </div>
            <div className="space-y-1">
              {DEV_ACCOUNTS.map((account) => (
                <button
                  key={account.id}
                  onClick={() => handleDevLogin(account)}
                  disabled={devLoading !== null}
                  className="w-full flex items-center gap-2 px-3 py-2 text-left text-sm rounded-lg hover:bg-amber-100 transition-colors cursor-pointer disabled:opacity-50"
                >
                  <span className="w-5 text-center">{devLoading === account.id ? '⏳' : account.icon}</span>
                  <span className="font-medium text-slate-800">{account.name}</span>
                  <span className="text-xs text-amber-600 ml-auto">{account.desc}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* 하단 */}
        <p className="text-xs text-slate-400 text-center mt-6">
          로그인 시 서비스 이용약관과 개인정보처리방침에 동의합니다.
        </p>

        {alertModal.open && (
          <ConfirmModal title="알림" message={alertModal.message} onConfirm={() => setAlertModal({ open: false, message: '' })} onClose={() => setAlertModal({ open: false, message: '' })} confirmLabel="확인" cancelLabel="" />
        )}
      </div>
    </div>
  )
}

/** 앱 설치 안내 — 데스크톱: QR, 모바일: Android/iOS 별 안내 */
function InstallGuide() {
  const [tab, setTab] = useState('ios') // 'ios' | 'android'
  const [mode, setMode] = useState(null) // 'mobile' | 'desktop'
  const [installPrompt, setInstallPrompt] = useState(null)
  const [dismissed, setDismissed] = useState(false)

  useEffect(() => {
    if (window.matchMedia('(display-mode: standalone)').matches || navigator.standalone) {
      setDismissed(true)
      return
    }

    const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent)
    const isAndroid = /Android/.test(navigator.userAgent)
    const isMobile = isIOS || isAndroid

    if (isMobile) {
      setMode('mobile')
      if (isAndroid) setTab('android')
    } else {
      setMode('desktop')
    }

    const handler = (e) => {
      e.preventDefault()
      setInstallPrompt(e)
    }
    window.addEventListener('beforeinstallprompt', handler)
    return () => window.removeEventListener('beforeinstallprompt', handler)
  }, [])

  const handleAndroidInstall = async () => {
    if (installPrompt) {
      installPrompt.prompt()
      await installPrompt.userChoice
      setInstallPrompt(null)
    }
  }

  if (dismissed) return null
  if (!mode) return null

  // 데스크톱: QR코드
  if (mode === 'desktop') {
    return (
      <div className="mt-4 bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="flex items-center justify-between px-5 pt-4 pb-2">
          <div>
            <h3 className="text-sm font-semibold text-slate-800">앱 설치 가이드</h3>
            <p className="text-xs text-slate-400 mt-0.5">홈 화면에 추가하세요</p>
          </div>
          <button onClick={() => setDismissed(true)} className="text-slate-300 hover:text-slate-500 transition-colors cursor-pointer p-1">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
          </button>
        </div>
        <div className="px-5 pb-5 text-center">
          <p className="text-xs text-slate-500 mb-3">휴대폰 카메라로 QR 코드를 스캔하세요</p>
          <div className="inline-block p-3 bg-white rounded-xl border border-slate-100">
            <QRCodeSVG value={window.location.origin} size={140} bgColor="#ffffff" fgColor="#334155" level="M" />
          </div>
          <p className="text-xs text-slate-400 mt-2">설치하면 푸시 알림을 받을 수 있어요</p>
        </div>
      </div>
    )
  }

  // 모바일: iOS/Android 탭
  return (
    <div className="mt-4 bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* 헤더 */}
      <div className="flex items-center justify-between px-5 pt-4 pb-2">
        <div>
          <h3 className="text-sm font-semibold text-slate-800">앱 설치 가이드</h3>
          <p className="text-xs text-slate-400 mt-0.5">홈 화면에 추가하세요</p>
        </div>
        <button onClick={() => setDismissed(true)} className="text-slate-300 hover:text-slate-500 transition-colors cursor-pointer p-1">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
        </button>
      </div>

      {/* 탭 */}
      <div className="flex mx-5 mb-3 bg-slate-100 rounded-lg p-0.5">
        <button
          onClick={() => setTab('ios')}
          className={`flex-1 text-xs py-2 rounded-md font-medium transition-all cursor-pointer ${
            tab === 'ios' ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          iOS (iPhone/iPad)
        </button>
        <button
          onClick={() => setTab('android')}
          className={`flex-1 text-xs py-2 rounded-md font-medium transition-all cursor-pointer ${
            tab === 'android' ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-400 hover:text-slate-600'
          }`}
        >
          Android
        </button>
      </div>

      {/* 콘텐츠 */}
      <div className="px-5 pb-5">
        {tab === 'ios' ? (
          <div className="space-y-3">
            <div className="flex items-start gap-3">
              <div className="w-7 h-7 bg-blue-50 rounded-lg flex items-center justify-center shrink-0 mt-0.5">
                <span className="text-xs font-bold text-blue-500">1</span>
              </div>
              <div>
                <p className="text-sm font-medium text-slate-700">Safari 브라우저 실행</p>
                <p className="text-xs text-slate-400">반드시 Safari 브라우저에서 페이지를 열어주세요.</p>
              </div>
            </div>
            <div className="flex items-start gap-3">
              <div className="w-7 h-7 bg-blue-50 rounded-lg flex items-center justify-center shrink-0 mt-0.5">
                <Share className="w-3.5 h-3.5 text-blue-500" />
              </div>
              <div>
                <p className="text-sm font-medium text-slate-700">'공유' 버튼 탭하기</p>
                <p className="text-xs text-slate-400">하단 메뉴 바의 중앙에 있는 공유 아이콘을 눌러 메뉴를 여세요.</p>
              </div>
            </div>
            <div className="flex items-start gap-3">
              <div className="w-7 h-7 bg-blue-50 rounded-lg flex items-center justify-center shrink-0 mt-0.5">
                <PlusSquare className="w-3.5 h-3.5 text-blue-500" />
              </div>
              <div>
                <p className="text-sm font-medium text-slate-700">'홈 화면에 추가' 선택</p>
                <p className="text-xs text-slate-400">메뉴를 아래로 스크롤하여 '홈 화면에 추가'를 찾아 선택하세요.</p>
              </div>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            {installPrompt ? (
              <button
                onClick={handleAndroidInstall}
                className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-primary text-white rounded-xl hover:bg-primary/90 transition-colors cursor-pointer"
              >
                <Download className="w-4 h-4" />
                <span className="text-sm font-medium">홈 화면에 설치</span>
              </button>
            ) : (
              <>
                <div className="flex items-start gap-3">
                  <div className="w-7 h-7 bg-green-50 rounded-lg flex items-center justify-center shrink-0 mt-0.5">
                    <span className="text-xs font-bold text-green-600">1</span>
                  </div>
                  <div>
                    <p className="text-sm font-medium text-slate-700">Chrome 브라우저 실행</p>
                    <p className="text-xs text-slate-400">Chrome 브라우저에서 페이지를 열어주세요.</p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <div className="w-7 h-7 bg-green-50 rounded-lg flex items-center justify-center shrink-0 mt-0.5 text-xs font-bold text-green-600">⋮</div>
                  <div>
                    <p className="text-sm font-medium text-slate-700">설정 메뉴 열기</p>
                    <p className="text-xs text-slate-400">우측 상단의 점 3개 아이콘을 눌러 메뉴를 여세요.</p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <div className="w-7 h-7 bg-green-50 rounded-lg flex items-center justify-center shrink-0 mt-0.5">
                    <Download className="w-3.5 h-3.5 text-green-600" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-slate-700">'앱 설치' 또는 '홈 화면에 추가'</p>
                    <p className="text-xs text-slate-400">메뉴에서 설치 옵션을 선택하여 앱처럼 사용하세요.</p>
                  </div>
                </div>
              </>
            )}
          </div>
        )}

        <p className="text-xs text-slate-400 mt-3 text-center">설치하면 푸시 알림을 받을 수 있어요</p>
      </div>
    </div>
  )
}
