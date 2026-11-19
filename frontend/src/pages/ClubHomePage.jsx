import { useEffect, useState } from 'react'
import { useOutletContext, useSearchParams } from 'react-router-dom'
import useClubStore from '../store/clubStore'
import NoticeBanner from '../components/NoticeBanner'
import ExecutiveCarousel from '../components/ExecutiveCarousel'
import HomeBoardPanel from '../components/HomeBoardPanel'
import HomeDrivePanel from '../components/HomeDrivePanel'
import HomeCalendarPanel from '../components/HomeCalendarPanel'
import HomeJongtoWidget from '../components/HomeJongtoWidget'
// import HomeShortcuts from '../components/HomeShortcuts'
import ArchiveView from '../components/ArchiveView'
import handleApiError from '../lib/handleApiError'

export default function ClubHomePage() {
  const { clubInfo } = useOutletContext()
  const { teams, generations, fetchTeams, fetchGenerations, currentGeneration, setCurrentGeneration } = useClubStore()
  const [searchParams, setSearchParams] = useSearchParams()
  const [showArchiveMenu, setShowArchiveMenu] = useState(false)

  useEffect(() => {
    if (clubInfo?.clubId) {
      fetchTeams(clubInfo.clubId).catch(e => handleApiError(e, '팀 목록 조회'))
      fetchGenerations(clubInfo.clubId).catch(e => handleApiError(e, '기수 목록 조회'))
    }
  }, [clubInfo?.clubId])

  // URL 쿼리 파라미터로 아카이브 모드 동기화
  useEffect(() => {
    const genId = searchParams.get('gen')
    if (genId && generations.length > 0) {
      const gen = generations.find(g => g.id === Number(genId))
      if (gen) {
        setCurrentGeneration(gen)
        return
      }
    }
    setCurrentGeneration(null)
  }, [searchParams, generations])

  const handleSelectGeneration = (gen) => {
    setSearchParams({ gen: gen.id })
    setShowArchiveMenu(false)
  }

  const handleBackFromArchive = () => {
    setSearchParams({})
    setCurrentGeneration(null)
  }

  const isExecutive = clubInfo?.isExecutive
  const calendarTeams = teams.map(t => ({ teamId: t.id, teamName: t.name }))

  // 아카이브 모드
  if (currentGeneration) {
    return (
      <ArchiveView
        clubId={clubInfo?.clubId}
        generation={currentGeneration}
        onBack={handleBackFromArchive}
      />
    )
  }

  return (
    <div className="flex flex-col gap-4 md:gap-6 -mb-20 md:-mb-34 animate-fade-in-up">
      {/* 상단: 공지 + 임원 — 전체 너비 */}
      <div className="flex flex-col lg:flex-row gap-4 lg:gap-6 lg:h-[110px]">
        <NoticeBanner clubId={clubInfo?.clubId} />
        <ExecutiveCarousel clubId={clubInfo?.clubId} clubInfo={clubInfo} />
      </div>

      {/* 메인: 좌측 콘텐츠 + 우측 사이드바 */}
      <div className="flex flex-col xl:flex-row gap-4 md:gap-6">
        {/* 좌측 */}
        <div className="flex-1 min-w-0 flex flex-col gap-4 md:gap-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 md:gap-6">
            <HomeBoardPanel clubId={clubInfo?.clubId} myTeams={clubInfo?.myTeams} />
            <HomeDrivePanel clubId={clubInfo?.clubId} myTeams={clubInfo?.myTeams} />
          </div>
          <HomeCalendarPanel clubId={clubInfo?.clubId} isExecutive={isExecutive} teams={calendarTeams} myTeams={clubInfo?.myTeams} />
        </div>

        {/* 우측 사이드바 */}
        <div className="w-full xl:w-[300px] xl:shrink-0 flex flex-col gap-4 md:gap-6">
          <HomeJongtoWidget clubId={clubInfo?.clubId} />
        </div>
      </div>

      {/* 아카이브 버튼 (기수 2개 이상일 때만) — 2컬럼 섹션 아래 독립 줄 */}
      {generations.length >= 2 && (
        <div className="relative inline-block">
          <button
            onClick={() => setShowArchiveMenu(prev => !prev)}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-slate-500 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 hover:text-slate-700 transition-colors cursor-pointer"
          >
            <span>📂</span>
            <span>아카이브</span>
          </button>
          {showArchiveMenu && (
            <>
              <div className="fixed inset-0 z-10" onClick={() => setShowArchiveMenu(false)} />
              <div className="absolute left-0 bottom-full mb-1 z-20 bg-white border border-slate-200 rounded-lg shadow-lg py-1 min-w-[160px]">
                {generations.map(g => (
                  <button
                    key={g.id}
                    onClick={() => handleSelectGeneration(g)}
                    className="w-full text-left px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors cursor-pointer"
                  >
                    {g.name}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}
