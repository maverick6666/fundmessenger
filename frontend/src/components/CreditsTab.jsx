import { useState, useEffect } from 'react'
import api from '../lib/api'
import handleApiError from '../lib/handleApiError'

export default function CreditsTab({ clubId }) {
  const [summary, setSummary] = useState(null)
  const [history, setHistory] = useState([])
  const [showHistory, setShowHistory] = useState(false)
  const [allocateForm, setAllocateForm] = useState({ teamId: '', amount: '', description: '' })
  const [reclaimForm, setReclaimForm] = useState({ teamId: '', amount: '', description: '' })
  const [mode, setMode] = useState(null) // 'allocate' | 'reclaim' | null
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    fetchSummary()
  }, [clubId])

  const fetchSummary = async () => {
    try {
      setLoading(true)
      const res = await api.request(`/clubs/${clubId}/credits/summary`)
      setSummary(res)
    } catch (e) {
      handleApiError(e, '크레딧 정보 조회')
      setError('크레딧 정보를 불러올 수 없습니다')
    } finally {
      setLoading(false)
    }
  }

  const fetchHistory = async () => {
    try {
      const res = await api.request(`/clubs/${clubId}/credits/history`)
      setHistory(res)
      setShowHistory(true)
    } catch (e) {
      handleApiError(e, '크레딧 내역 조회')
      setError('내역을 불러올 수 없습니다')
    }
  }

  const handleAllocate = async (e) => {
    e.preventDefault()
    if (!allocateForm.teamId || !allocateForm.amount) return
    try {
      setError('')
      const res = await api.request(`/clubs/${clubId}/credits/allocate`, {
        method: 'POST',
        body: JSON.stringify({
          teamId: Number(allocateForm.teamId),
          amount: Number(allocateForm.amount),
          description: allocateForm.description || undefined
        })
      })
      setSummary(res)
      setAllocateForm({ teamId: '', amount: '', description: '' })
      setMode(null)
    } catch (e) {
      handleApiError(e, '크레딧 분배')
      setError(e.message || '분배에 실패했습니다')
    }
  }

  const handleReclaim = async (e) => {
    e.preventDefault()
    if (!reclaimForm.teamId || !reclaimForm.amount) return
    try {
      setError('')
      const res = await api.request(`/clubs/${clubId}/credits/reclaim`, {
        method: 'POST',
        body: JSON.stringify({
          teamId: Number(reclaimForm.teamId),
          amount: Number(reclaimForm.amount),
          description: reclaimForm.description || undefined
        })
      })
      setSummary(res)
      setReclaimForm({ teamId: '', amount: '', description: '' })
      setMode(null)
    } catch (e) {
      handleApiError(e, '크레딧 회수')
      setError(e.message || '회수에 실패했습니다')
    }
  }

  if (loading) return <p className="text-sm text-slate-400">로딩 중...</p>
  if (!summary) return <p className="text-sm text-red-400">{error || '데이터 없음'}</p>

  const typeLabel = { GRANT: '지급', REVOKE: '차감(관리자)', ALLOCATE: '분배', RECLAIM: '회수', DEDUCT: '소비' }
  const typeColor = { GRANT: 'text-green-600', REVOKE: 'text-red-600', ALLOCATE: 'text-blue-600', RECLAIM: 'text-orange-600', DEDUCT: 'text-red-500' }

  return (
    <div className="space-y-6">
      {/* 잔액 요약 */}
      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white rounded-2xl border p-4 text-center">
          <p className="text-xs text-slate-400 mb-1">전체 크레딧</p>
          <p className="text-2xl font-bold text-slate-800">{summary.totalBalance}</p>
        </div>
        <div className="bg-white rounded-2xl border p-4 text-center">
          <p className="text-xs text-slate-400 mb-1">미분배 잔액</p>
          <p className="text-2xl font-bold text-primary">{summary.clubBalance}</p>
        </div>
        <div className="bg-white rounded-2xl border p-4 text-center">
          <p className="text-xs text-slate-400 mb-1">팀 분배 총액</p>
          <p className="text-2xl font-bold text-blue-600">{summary.totalAllocated}</p>
        </div>
      </div>

      {error && <p className="text-sm text-red-500 bg-red-50 rounded-lg px-3 py-2">{error}</p>}

      {/* 팀별 크레딧 */}
      <div className="bg-white rounded-2xl border">
        <div className="flex items-center justify-between px-4 py-3 border-b">
          <h3 className="text-sm font-semibold text-slate-700">팀별 크레딧</h3>
          <div className="flex gap-2">
            <button
              onClick={() => setMode(mode === 'allocate' ? null : 'allocate')}
              className="px-3 py-1.5 text-xs font-medium rounded-lg bg-blue-50 text-blue-600 hover:bg-blue-100 cursor-pointer"
            >
              + 분배
            </button>
            <button
              onClick={() => setMode(mode === 'reclaim' ? null : 'reclaim')}
              className="px-3 py-1.5 text-xs font-medium rounded-lg bg-orange-50 text-orange-600 hover:bg-orange-100 cursor-pointer"
            >
              ↩ 회수
            </button>
          </div>
        </div>

        {/* 분배 폼 */}
        {mode === 'allocate' && (
          <form onSubmit={handleAllocate} className="px-4 py-3 bg-blue-50/50 border-b flex items-end gap-3 flex-wrap">
            <label className="text-xs">
              <span className="text-slate-500 block mb-1">팀</span>
              <select
                value={allocateForm.teamId}
                onChange={e => setAllocateForm(f => ({ ...f, teamId: e.target.value }))}
                className="border rounded-lg px-3 py-1.5 text-sm"
                required
              >
                <option value="">선택</option>
                {summary.teams.map(t => (
                  <option key={t.teamId} value={t.teamId}>{t.teamName} ({t.creditAllocation})</option>
                ))}
              </select>
            </label>
            <label className="text-xs">
              <span className="text-slate-500 block mb-1">분배량</span>
              <input
                type="number" min="1" max={summary.clubBalance}
                value={allocateForm.amount}
                onChange={e => setAllocateForm(f => ({ ...f, amount: e.target.value }))}
                className="border rounded-lg px-3 py-1.5 text-sm w-24"
                placeholder="0"
                required
              />
            </label>
            <label className="text-xs flex-1">
              <span className="text-slate-500 block mb-1">메모 (선택)</span>
              <input
                value={allocateForm.description}
                onChange={e => setAllocateForm(f => ({ ...f, description: e.target.value }))}
                className="border rounded-lg px-3 py-1.5 text-sm w-full"
                placeholder="분배 사유"
              />
            </label>
            <button type="submit" className="px-4 py-1.5 text-xs font-medium rounded-lg bg-primary text-white hover:bg-primary-hover cursor-pointer">
              분배
            </button>
          </form>
        )}

        {/* 회수 폼 */}
        {mode === 'reclaim' && (
          <form onSubmit={handleReclaim} className="px-4 py-3 bg-orange-50/50 border-b flex items-end gap-3 flex-wrap">
            <label className="text-xs">
              <span className="text-slate-500 block mb-1">팀</span>
              <select
                value={reclaimForm.teamId}
                onChange={e => setReclaimForm(f => ({ ...f, teamId: e.target.value }))}
                className="border rounded-lg px-3 py-1.5 text-sm"
                required
              >
                <option value="">선택</option>
                {summary.teams.filter(t => t.creditAllocation > 0).map(t => (
                  <option key={t.teamId} value={t.teamId}>{t.teamName} ({t.creditAllocation})</option>
                ))}
              </select>
            </label>
            <label className="text-xs">
              <span className="text-slate-500 block mb-1">회수량</span>
              <input
                type="number" min="1"
                value={reclaimForm.amount}
                onChange={e => setReclaimForm(f => ({ ...f, amount: e.target.value }))}
                className="border rounded-lg px-3 py-1.5 text-sm w-24"
                placeholder="0"
                required
              />
            </label>
            <label className="text-xs flex-1">
              <span className="text-slate-500 block mb-1">메모 (선택)</span>
              <input
                value={reclaimForm.description}
                onChange={e => setReclaimForm(f => ({ ...f, description: e.target.value }))}
                className="border rounded-lg px-3 py-1.5 text-sm w-full"
                placeholder="회수 사유"
              />
            </label>
            <button type="submit" className="px-4 py-1.5 text-xs font-medium rounded-lg bg-orange-600 text-white hover:bg-orange-700 cursor-pointer">
              회수
            </button>
          </form>
        )}

        {/* 팀 테이블 */}
        {summary.teams.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-slate-400">팀이 없습니다</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-xs text-slate-400 border-b">
                <th className="px-4 py-2 text-left font-medium">팀</th>
                <th className="px-4 py-2 text-center font-medium">인원</th>
                <th className="px-4 py-2 text-center font-medium">할당 크레딧</th>
              </tr>
            </thead>
            <tbody>
              {summary.teams.map(t => (
                <tr key={t.teamId} className="border-b last:border-0 hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium text-slate-700">{t.teamName}</td>
                  <td className="px-4 py-3 text-center text-slate-500">{t.memberCount}명</td>
                  <td className="px-4 py-3 text-center font-semibold text-blue-600">{t.creditAllocation}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 내역 조회 */}
      <div>
        <button
          onClick={showHistory ? () => setShowHistory(false) : fetchHistory}
          className="text-xs text-slate-400 hover:text-slate-600 underline cursor-pointer"
        >
          {showHistory ? '내역 숨기기' : '크레딧 내역 보기'}
        </button>

        {showHistory && history.length > 0 && (
          <div className="mt-3 bg-white rounded-2xl border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-slate-400 border-b">
                  <th className="px-4 py-2 text-left font-medium">유형</th>
                  <th className="px-4 py-2 text-left font-medium">팀</th>
                  <th className="px-4 py-2 text-right font-medium">수량</th>
                  <th className="px-4 py-2 text-left font-medium">메모</th>
                  <th className="px-4 py-2 text-right font-medium">일시</th>
                </tr>
              </thead>
              <tbody>
                {history.map(h => (
                  <tr key={h.id} className="border-b last:border-0">
                    <td className={`px-4 py-2 text-xs font-medium ${typeColor[h.type] || 'text-slate-500'}`}>
                      {typeLabel[h.type] || h.type}
                    </td>
                    <td className="px-4 py-2 text-xs text-slate-500">{h.teamName || '-'}</td>
                    <td className="px-4 py-2 text-xs text-right font-medium">{h.amount}</td>
                    <td className="px-4 py-2 text-xs text-slate-400 max-w-40 truncate">{h.description || '-'}</td>
                    <td className="px-4 py-2 text-xs text-slate-400 text-right">
                      {new Date(h.createdAt).toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {showHistory && history.length === 0 && (
          <p className="mt-3 text-sm text-slate-400">내역이 없습니다</p>
        )}
      </div>
    </div>
  )
}
