import FundSubNav, { FundMobileNav } from './FundSubNav'
import { FundSidePanel } from './SidePanel'

/**
 * 펀드팀 페이지 래퍼
 * 좌측 사이드바 + 메인 콘텐츠 + 우측 SidePanel
 */
export default function FundPageWrapper({ teamId, children }) {
  return (
    <>
      {/* 모바일: 상단 탭 바 */}
      <FundMobileNav teamId={teamId} />

      {/* 데스크톱: 사이드바 + 콘텐츠 */}
      <div className="flex gap-6">
        <FundSubNav teamId={teamId} />
        <div className="flex-1 min-w-0">
          {children}
        </div>
      </div>

      <FundSidePanel />
    </>
  )
}
