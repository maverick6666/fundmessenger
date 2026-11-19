import { useEffect, useState } from 'react'
import StockHeader from './StockHeader'
import StockPriceChart from './StockPriceChart'
import StockNewsTab from './StockNewsTab'
import StockThreadTab from './StockThreadTab'

export default function StockDetailPanel({
  clubId,
  stock,
  membershipId,
  isExecutive,
  highlightNewsId = null,
  onListRefresh,
}) {
  const [activeTab, setActiveTab] = useState('news')

  useEffect(() => {
    if (highlightNewsId) {
      setActiveTab('news')
    }
  }, [highlightNewsId])

  if (!stock) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm flex items-center justify-center">
        <div className="text-center text-slate-400">
          <svg className="w-12 h-12 mx-auto mb-3 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"
            />
          </svg>
          <p className="text-sm">종목을 선택하세요</p>
          <p className="text-xs mt-1">종목별 뉴스와 스레드를 확인할 수 있습니다</p>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
      <StockHeader stock={stock} />

      <div>
        <StockPriceChart clubId={clubId} stockId={stock.id} />

        <div className="sticky top-0 z-10 bg-white border-b border-slate-200 flex">
          <button
            onClick={() => setActiveTab('news')}
            className={`flex-1 py-3 text-sm font-semibold text-center transition-colors cursor-pointer ${
              activeTab === 'news'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            뉴스
          </button>
          <button
            onClick={() => setActiveTab('threads')}
            className={`flex-1 py-3 text-sm font-semibold text-center transition-colors cursor-pointer ${
              activeTab === 'threads'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            스레드
          </button>
        </div>

        {activeTab === 'news' && (
          <StockNewsTab
            clubId={clubId}
            stockId={stock.id}
            membershipId={membershipId}
            isExecutive={isExecutive}
            highlightNewsId={highlightNewsId}
          />
        )}
        {activeTab === 'threads' && (
          <StockThreadTab
            clubId={clubId}
            stockId={stock.id}
            membershipId={membershipId}
            isExecutive={isExecutive}
            onThreadsChanged={onListRefresh}
          />
        )}
      </div>
    </div>
  )
}
