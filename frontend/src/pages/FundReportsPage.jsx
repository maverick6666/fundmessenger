import { useState, useEffect, useMemo } from 'react'
import { useParams, useOutletContext } from 'react-router-dom'
import { FileText, BarChart3 } from 'lucide-react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'
import useSidePanelStore from '../store/sidePanelStore'
import FundPageWrapper from '../components/fund/FundPageWrapper'
import DocumentCard from '../components/fund/DocumentCard'

const TABS = [
  { value: 'REPORT', label: '운용보고서', icon: BarChart3 },
  { value: 'DECISION', label: '의사결정서', icon: FileText },
]

export default function FundReportsPage() {
  const { teamId } = useParams()
  const { clubInfo } = useOutletContext()
  const clubId = clubInfo?.clubId
  const tid = Number(teamId)
  const openDocument = useSidePanelStore(s => s.openDocument)

  const [activeTab, setActiveTab] = useState('REPORT')
  const [notes, setNotes] = useState([])
  const [loading, setLoading] = useState(true)

  // Separate counts for each tab (fetched once)
  const [reportCount, setReportCount] = useState(0)
  const [decisionCount, setDecisionCount] = useState(0)

  const fetchNotes = () => {
    if (!clubId) return
    api.get(`/clubs/${clubId}/fund/teams/${tid}/notes?noteType=${activeTab}`)
      .then(data => setNotes(Array.isArray(data) ? data : data?.content || []))
      .catch(() => {})
  }

  // Fetch counts for both tabs on mount
  useEffect(() => {
    if (!clubId) return
    Promise.all([
      api.get(`/clubs/${clubId}/fund/teams/${tid}/notes?noteType=REPORT`).catch(() => []),
      api.get(`/clubs/${clubId}/fund/teams/${tid}/notes?noteType=DECISION`).catch(() => []),
    ]).then(([reports, decisions]) => {
      const r = Array.isArray(reports) ? reports : reports?.content || []
      const d = Array.isArray(decisions) ? decisions : decisions?.content || []
      setReportCount(r.length)
      setDecisionCount(d.length)
    })
  }, [clubId, tid])

  useEffect(() => {
    if (!clubId) return
    setLoading(true)
    api.get(`/clubs/${clubId}/fund/teams/${tid}/notes?noteType=${activeTab}`)
      .then(data => {
        const arr = Array.isArray(data) ? data : data?.content || []
        setNotes(arr)
        // Update count for current tab
        if (activeTab === 'REPORT') setReportCount(arr.length)
        else setDecisionCount(arr.length)
      })
      .catch(e => handleApiError(e, '문서 조회'))
      .finally(() => setLoading(false))
  }, [clubId, tid, activeTab])

  const handleNoteClick = async (note) => {
    if (note.positionId) {
      try {
        const full = await api.get(
          `/clubs/${clubId}/fund/teams/${tid}/positions/${note.positionId}/notes/${note.id}`
        )
        openDocument(full, activeTab === 'DECISION' ? 'decision-note' : 'report', () => fetchNotes())
        return
      } catch {
        // fallback to partial data
      }
    }
    openDocument(note, activeTab === 'DECISION' ? 'decision-note' : 'report', () => fetchNotes())
  }

  const cardType = activeTab === 'DECISION' ? 'decision' : 'report'

  const emptyMessages = {
    REPORT: {
      title: '작성된 운용보고서가 없습니다',
      sub: '포지션 상세에서 운용보고서를 작성할 수 있습니다',
    },
    DECISION: {
      title: '작성된 의사결정서가 없습니다',
      sub: '포지션 상세에서 AI 의사결정서를 생성하거나 직접 작성할 수 있습니다',
    },
  }

  const getTabCount = (tabValue) => {
    if (tabValue === 'REPORT') return reportCount
    return decisionCount
  }

  return (
    <FundPageWrapper teamId={tid}>
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="px-5 py-3 border-b border-slate-100">
          <h2 className="text-sm font-bold text-slate-800 fund-serif">문서</h2>
        </div>

        {/* Tabs with count badges */}
        <div className="flex items-center px-5 py-2 border-b border-slate-100 gap-1">
          {TABS.map(tab => {
            const Icon = tab.icon
            const isActive = activeTab === tab.value
            const count = getTabCount(tab.value)
            return (
              <button
                key={tab.value}
                onClick={() => setActiveTab(tab.value)}
                className={`flex items-center gap-1.5 px-3 py-1 text-xs rounded-full font-medium transition-colors cursor-pointer ${
                  isActive
                    ? 'bg-slate-700 text-white'
                    : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                }`}
              >
                <Icon size={12} />
                {tab.label}
                {count > 0 && (
                  <span className={`px-1 text-xs font-bold rounded-full min-w-[14px] text-center ${
                    isActive ? 'bg-white/20 text-white' : 'bg-slate-300 text-slate-600'
                  }`}>
                    {count}
                  </span>
                )}
              </button>
            )
          })}
          <span className="ml-auto text-xs text-slate-400">{notes.length}건</span>
        </div>

        {/* Content */}
        {loading ? (
          <div className="p-5 space-y-2">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="animate-pulse bg-slate-100 rounded-lg h-20" />
            ))}
          </div>
        ) : notes.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 px-4">
            <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center mb-3">
              {activeTab === 'REPORT'
                ? <BarChart3 size={22} className="text-slate-400" />
                : <FileText size={22} className="text-slate-400" />
              }
            </div>
            <p className="text-sm font-medium text-slate-500">
              {emptyMessages[activeTab].title}
            </p>
            <p className="text-xs text-slate-400 mt-1 text-center max-w-xs">
              {emptyMessages[activeTab].sub}
            </p>
          </div>
        ) : (
          <div className="p-4 flex flex-wrap gap-4">
            {notes.map(note => (
              <DocumentCard
                key={note.id}
                type={cardType}
                data={note}
                onClick={() => handleNoteClick(note)}
              />
            ))}
          </div>
        )}
      </div>
    </FundPageWrapper>
  )
}
