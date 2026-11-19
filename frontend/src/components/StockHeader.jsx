const MARKET_BADGE = {
  KOSPI: 'bg-blue-100 text-blue-700',
  KOSDAQ: 'bg-green-100 text-green-700',
  NASDAQ: 'bg-purple-100 text-purple-700',
  NYSE: 'bg-red-100 text-red-700',
}

export default function StockHeader({ stock }) {
  if (!stock) return null

  const timeAgo = stock.priceUpdatedAt
    ? formatTimeAgo(new Date(stock.priceUpdatedAt))
    : null

  return (
    <div className="px-5 py-4 border-b border-slate-100">
      <div className="flex items-center gap-2 mb-1">
        <h2 className="text-lg font-bold text-slate-900">{stock.stockName}</h2>
        <span className={`text-xs px-2 py-0.5 rounded font-medium ${MARKET_BADGE[stock.market] || 'bg-slate-100 text-slate-700'}`}>
          {stock.stockCode}
        </span>
        <span className={`text-xs px-2 py-0.5 rounded ${MARKET_BADGE[stock.market] || 'bg-slate-100 text-slate-700'}`}>
          {stock.market}
        </span>
      </div>
      <div className="flex items-center gap-3">
        {stock.currentPrice != null && (
          <span className="text-xl font-bold text-slate-900">
            {Number(stock.currentPrice).toLocaleString()}원
          </span>
        )}
        {stock.changeRate != null && (
          <span className={`text-sm font-semibold ${
            stock.changeRate > 0 ? 'text-red-500' : stock.changeRate < 0 ? 'text-blue-500' : 'text-slate-500'
          }`}>
            {stock.changeRate > 0 ? '+' : ''}{stock.changeRate?.toFixed(2)}%
          </span>
        )}
        {timeAgo && (
          <span className="text-xs text-slate-400">{timeAgo} 기준</span>
        )}
      </div>
    </div>
  )
}

function formatTimeAgo(date) {
  const diff = Math.floor((Date.now() - date.getTime()) / 1000)
  if (diff < 60) return '방금 전'
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`
  return `${Math.floor(diff / 86400)}일 전`
}
