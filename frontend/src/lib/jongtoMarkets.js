export const MARKET_COLOR = {
  KOSPI: 'bg-blue-100 text-blue-700',
  KOSDAQ: 'bg-green-100 text-green-700',
  NASDAQ: 'bg-purple-100 text-purple-700',
  NYSE: 'bg-red-100 text-red-700',
  AMEX: 'bg-orange-100 text-orange-700',
}

export const MARKET_GROUPS = [
  { value: 'DOMESTIC', label: '국내' },
  { value: 'OVERSEAS', label: '해외' },
]

export const MARKET_OPTIONS = {
  DOMESTIC: [
    { value: 'ALL', label: '전체' },
    { value: 'KOSPI', label: 'KOSPI' },
    { value: 'KOSDAQ', label: 'KOSDAQ' },
  ],
  OVERSEAS: [
    { value: 'ALL', label: '전체' },
    { value: 'NASDAQ', label: 'NASDAQ' },
    { value: 'NYSE', label: 'NYSE' },
    { value: 'AMEX', label: 'AMEX' },
  ],
}
