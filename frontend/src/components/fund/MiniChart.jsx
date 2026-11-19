import { useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';

/**
 * 미니 캔들스틱 차트 — 볼륨 없음, 스크롤/인터랙션 없음
 *
 * - 종목명 + 티커 + 변동률 헤더
 * - 날짜 범위 푸터
 * - ResizeObserver 반응형
 * - 한국 주식 컨벤션: 상승=빨강, 하락=초록
 */
export function MiniChart({ chartData, height = 150, loading = false }) {
  const chartContainerRef = useRef(null);
  const chartRef = useRef(null);

  useEffect(() => {
    if (!chartContainerRef.current || !chartData?.candles?.length) return;

    // 기존 차트 정리
    if (chartRef.current) {
      chartRef.current.remove();
    }

    const chart = createChart(chartContainerRef.current, {
      width: chartContainerRef.current.clientWidth,
      height,
      layout: {
        background: { color: '#ffffff' },
        textColor: '#6b7280',
      },
      grid: {
        vertLines: { visible: false },
        horzLines: { color: '#e5e7eb' },
      },
      timeScale: {
        timeVisible: false,
        borderVisible: false,
      },
      rightPriceScale: {
        borderVisible: false,
      },
      leftPriceScale: { visible: false },
      crosshair: {
        vertLine: { visible: false },
        horzLine: { visible: false },
      },
      handleScale: false,
      handleScroll: false,
      watermark: { visible: false },
      attributionLogo: false,
    });

    const candlestickSeries = chart.addCandlestickSeries({
      upColor: '#ef5350',
      downColor: '#26a69a',
      borderVisible: false,
      wickUpColor: '#ef5350',
      wickDownColor: '#26a69a',
    });

    candlestickSeries.priceScale().applyOptions({
      scaleMargins: { top: 0.1, bottom: 0.1 },
    });

    const candlestickData = chartData.candles.map((c) => ({
      time: c.time,
      open: c.open,
      high: c.high,
      low: c.low,
      close: c.close,
    }));

    candlestickSeries.setData(candlestickData);
    chart.timeScale().fitContent();

    chartRef.current = chart;

    // 반응형 리사이즈
    const resizeObserver = new ResizeObserver(() => {
      if (chartRef.current && chartContainerRef.current) {
        chartRef.current.applyOptions({
          width: chartContainerRef.current.clientWidth,
        });
      }
    });

    resizeObserver.observe(chartContainerRef.current);

    return () => {
      resizeObserver.disconnect();
      if (chartRef.current) {
        chartRef.current.remove();
        chartRef.current = null;
      }
    };
  }, [chartData, height]);

  // ── 변동률 계산 ──
  const firstCandle = chartData?.candles?.[0];
  const lastCandle = chartData?.candles?.[chartData?.candles?.length - 1];
  const changeRate =
    firstCandle && lastCandle
      ? ((lastCandle.close - firstCandle.open) / firstCandle.open * 100).toFixed(2)
      : '0.00';
  const isPositive = parseFloat(changeRate) >= 0;

  // ── 날짜 범위 ──
  const fromDate = chartData?.from
    ? new Date(chartData.from * 1000).toLocaleDateString('ko-KR')
    : '';
  const toDate = chartData?.to
    ? new Date(chartData.to * 1000).toLocaleDateString('ko-KR')
    : '';

  return (
    <div className="relative overflow-hidden rounded-lg border border-slate-200 bg-white">
      {/* TradingView 로고 숨김 + 왼쪽 가격축 셀 제거 */}
      <style>{`
        .tv-lightweight-charts a[href*="tradingview"],
        .tv-lightweight-charts a[target="_blank"],
        [class*="tv-lightweight-charts"] a {
          display: none !important;
        }
        .mini-chart-container table tr td:first-child {
          width: 0 !important;
          min-width: 0 !important;
          padding: 0 !important;
        }
      `}</style>

      {/* 헤더 */}
      <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-3 py-2">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-slate-900">
            {chartData?.name || '-'}
          </span>
          <span className="text-xs text-slate-500">{chartData?.ticker || ''}</span>
        </div>
        <span
          className={`text-sm font-medium ${isPositive ? 'text-red-500' : 'text-blue-500'}`}
        >
          {isPositive ? '+' : ''}
          {changeRate}%
        </span>
      </div>

      {/* 차트 */}
      <div ref={chartContainerRef} className="mini-chart-container" style={{ height }} />

      {/* 푸터 */}
      <div className="border-t border-slate-200 bg-slate-50 px-3 py-1.5">
        <p className="text-xs text-slate-500">
          {fromDate && toDate
            ? `${fromDate} ~ ${toDate} (${chartData?.candles?.length || 0}일)`
            : '-'}
        </p>
      </div>

      {/* 로딩 오버레이 */}
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center bg-white/75">
          <div className="flex items-center gap-2 text-slate-500">
            <svg className="h-5 w-5 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
            <span className="text-sm">로딩 중...</span>
          </div>
        </div>
      )}

      {/* 데이터 없음 */}
      {!loading && !chartData?.candles?.length && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-50">
          <p className="text-sm text-slate-500">차트 데이터 없음</p>
        </div>
      )}
    </div>
  );
}
