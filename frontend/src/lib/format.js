/**
 * 공유 포맷 유틸리티
 * - 기존: formatSize, formatTime
 * - 펀드팀 포팅: formatNumber, formatQuantity, formatPrice, formatCurrency 등
 */

// ── 기존 유틸리티 ──

export function formatSize(bytes) {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
}

export function formatTime(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const diffMin = Math.floor((Date.now() - d) / 60000)
  if (diffMin < 1) return '방금'
  if (diffMin < 60) return `${diffMin}분 전`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr}시간 전`
  return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}

// ── 펀드팀 포맷 유틸리티 (v1 formatters.js 포팅) ──

/** 한국어 로캘 숫자 포맷 */
export function formatNumber(value, decimals = 0) {
  if (value == null) return '-'
  return new Intl.NumberFormat('ko-KR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value)
}

/** 수량 포맷 — trailing zero 제거 */
export function formatQuantity(value) {
  if (value == null) return '-'
  const num = parseFloat(value)
  if (Number.isInteger(num)) return num.toLocaleString('ko-KR')
  return parseFloat(num.toFixed(8)).toLocaleString('ko-KR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 8,
  })
}

/** 시장별 가격 포맷 (₩/$/USDT) */
export function formatPrice(value, market = 'KRX') {
  if (value == null) return '-'
  const num = parseFloat(value)
  const isUS = market === 'NASDAQ' || market === 'NYSE' || market === 'USD'
  const isCrypto = market === 'CRYPTO' || market === 'USDT'

  if (isUS) {
    const formatted = parseFloat(num.toFixed(3))
    return '$' + formatted.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 3 })
  }
  if (isCrypto) {
    const formatted = parseFloat(num.toFixed(4))
    return formatted.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 }) + ' USDT'
  }
  return '₩' + Math.round(num).toLocaleString('ko-KR')
}

/** Intl.NumberFormat 기반 통화 포맷 */
export function formatCurrency(value, market = 'KRX') {
  if (value == null) return '-'
  const isUS = market === 'NASDAQ' || market === 'NYSE' || market === 'USD'
  const isCrypto = market === 'CRYPTO' || market === 'USDT'

  if (isUS) {
    return new Intl.NumberFormat('en-US', {
      style: 'currency', currency: 'USD',
      minimumFractionDigits: 2, maximumFractionDigits: 3,
    }).format(value)
  }
  if (isCrypto) {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2, maximumFractionDigits: 4,
    }).format(value) + ' USDT'
  }
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency', currency: 'KRW',
    minimumFractionDigits: 0, maximumFractionDigits: 0,
  }).format(value)
}

/** 통화 단위 반환 */
export function getCurrencyUnit(market) {
  if (market === 'NASDAQ' || market === 'NYSE') return ' USD'
  if (market === 'CRYPTO') return ' USDT'
  return ' 원'
}

/** 비율(0-1) → 백분율. 예: 0.025 → "2.50%" */
export function formatPercent(value, decimals = 2) {
  if (value == null) return '-'
  return `${(value * 100).toFixed(decimals)}%`
}

/** 수익률 표시. 예: -2.5 → "-2.50%", 3.1 → "+3.10%" */
export function formatProfitRate(value, decimals = 2) {
  if (value == null) return '-'
  return `${value >= 0 ? '+' : ''}${Number(value).toFixed(decimals)}%`
}

/** 보유기간 표시 */
export function formatHours(hours) {
  if (hours == null) return '-'
  if (hours < 1) return '1시간 미만'
  hours = Math.floor(hours)
  if (hours < 24) return `${hours}시간`
  const days = Math.floor(hours / 24)
  const rem = hours % 24
  return rem === 0 ? `${days}일` : `${days}일 ${rem}시간`
}

/** 보유기간 계산 (시간 단위 float) */
export function calcHoldingHours(openedAt) {
  if (!openedAt) return null
  return (Date.now() - new Date(openedAt).getTime()) / (1000 * 60 * 60)
}

/** 상태 라벨 매핑 */
export function getStatusLabel(status) {
  const labels = {
    pending: '대기중', approved: '승인됨', rejected: '거부됨',
    discussion: '토론중', open: '진행중', closed: '종료',
  }
  return labels[status] || status
}

/** 요청 타입 라벨 */
export function getRequestTypeLabel(type) {
  return type === 'buy' ? '매수' : '매도'
}

/** 숫자 입력 정제 — trailing zero 제거 */
export function cleanNumberInput(value) {
  if (value == null || value === '') return ''
  const num = parseFloat(value)
  if (isNaN(num)) return value
  return String(num)
}

/** 수익/손실 Tailwind 색상 클래스 */
export function getProfitLossColor(value) {
  if (value == null || value === 0) return 'text-slate-600'
  return value > 0 ? 'text-red-600' : 'text-blue-600'
}

/** 가격+수량 복합 포맷. 예: "₩20,000 (50주)" */
export function formatPriceQuantity(price, quantity, market = 'KRX', ratio = null) {
  if (price == null) return '-'
  const formattedPrice = formatCurrency(price, market)

  if (quantity != null) {
    const formattedQty = formatQuantity(quantity)
    const isKorean = market === 'KOSPI' || market === 'KOSDAQ' || market === 'KRX'
    const isCrypto = market === 'CRYPTO' || market === 'USDT'
    const unit = isKorean ? '주' : isCrypto ? '' : ' shares'
    return `${formattedPrice} (${formattedQty}${unit})`
  }
  if (ratio != null) return `${formattedPrice} (${formatPercent(ratio)})`
  return formattedPrice
}

/** UTC 문자열 → Date 파싱 (타임존 정보 없으면 UTC로 간주) */
export function parseUTCDate(date) {
  if (!date) return null
  if (date.endsWith('Z') || date.includes('+') || date.includes('-', 10)) return new Date(date)
  return new Date(date + 'Z')
}

/** Intl.DateTimeFormat 기반 날짜 포맷 (date-fns 불필요) */
export function formatDate(date, options = {}) {
  if (!date) return '-'
  const parsed = parseUTCDate(date)
  if (!parsed || isNaN(parsed.getTime())) return '-'
  const defaults = { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }
  return new Intl.DateTimeFormat('ko-KR', { ...defaults, ...options }).format(parsed)
}

/** KST 기준 오늘 날짜 (YYYY-MM-DD) */
export function getKSTToday() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date())
}

/** KST 기준 현재 시간 (0-23) */
export function getKSTHour() {
  return parseInt(new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Seoul', hour: '2-digit', hour12: false,
  }).format(new Date()))
}
