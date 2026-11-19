import { useCallback, useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';

const RANGE_EPSILON = 0.001;

/**
 * 캔들스틱 + 거래량 히스토그램 차트
 *
 * - lightweight-charts 4.x
 * - 한국 주식 컨벤션: 상승=빨강, 하락=초록
 * - 왼쪽 스크롤 시 과거 데이터 자동 로드 (onLoadMore)
 * - 오른쪽 끝 제한 (미래 영역 스크롤 방지)
 * - TradingView 로고 CSS 숨김
 *
 * Props:
 * @param {Array} candles - { time, open, high, low, close, volume }[]
 * @param {boolean} loading - 초기 로딩 상태
 * @param {number} height - 차트 높이 (기본 400)
 * @param {boolean} hasMore - 추가 과거 데이터 존재 여부
 * @param {Function} onLoadMore - (beforeTimestamp, neededBars) => void
 * @param {boolean} loadingMore - 과거 데이터 로딩 중
 */
export function StockChart({
  candles,
  loading,
  height = 400,
  hasMore = false,
  onLoadMore = null,
  loadingMore = false,
}) {
  const chartContainerRef = useRef(null);
  const chartRef = useRef(null);
  const candlestickSeriesRef = useRef(null);
  const volumeSeriesRef = useRef(null);
  const lastCandlesLengthRef = useRef(0);
  const isAdjustingRangeRef = useRef(false);
  const isLoadRequestPendingRef = useRef(false);

  // Refs for stable callback access (prevents chart recreation)
  const candlesRef = useRef(candles);
  const hasMoreRef = useRef(hasMore);
  const onLoadMoreRef = useRef(onLoadMore);
  const loadingMoreRef = useRef(loadingMore);

  // Keep refs in sync
  candlesRef.current = candles;
  hasMoreRef.current = hasMore;
  onLoadMoreRef.current = onLoadMore;
  loadingMoreRef.current = loadingMore;

  // 뷰포트에 빈 공간이 있으면 과거 데이터 로드
  const maybeLoadMore = useCallback(() => {
    if (!chartRef.current) return;
    if (!candlesRef.current || candlesRef.current.length === 0) return;
    if (!hasMoreRef.current || !onLoadMoreRef.current) return;
    if (loadingMoreRef.current || isLoadRequestPendingRef.current) return;

    const logicalRange = chartRef.current.timeScale().getVisibleLogicalRange();
    if (!logicalRange) return;

    // logicalRange.from < 0 이면 첫번째 데이터보다 왼쪽이 보이는 것
    if (logicalRange.from < 0) {
      const oldestDataTime = candlesRef.current[0]?.time;
      if (!oldestDataTime) return;

      // 빈 공간 크기 계산 (여유분 20% 추가)
      const emptyBars = Math.abs(logicalRange.from);
      const neededBars = Math.ceil(emptyBars * 1.2);

      isLoadRequestPendingRef.current = true;
      onLoadMoreRef.current(oldestDataTime, neededBars);
    }
  }, []);

  // 오른쪽 끝만 제한 (미래 스크롤 방지), 왼쪽은 자유 스크롤
  const clampRightEdge = useCallback(() => {
    if (!chartRef.current) return;
    if (!candlesRef.current || candlesRef.current.length < 2) return;

    const timeScale = chartRef.current.timeScale();
    const logicalRange = timeScale.getVisibleLogicalRange();
    if (!logicalRange) return;

    const lastIndex = candlesRef.current.length - 1;

    let nextFrom = logicalRange.from;
    let nextTo = logicalRange.to;

    if (!Number.isFinite(nextFrom) || !Number.isFinite(nextTo) || nextTo <= nextFrom) return;

    const visibleSpan = nextTo - nextFrom;
    let shouldClamp = false;

    if (nextTo > lastIndex + RANGE_EPSILON) {
      nextTo = lastIndex;
      nextFrom = nextTo - visibleSpan;
      shouldClamp = true;
    }

    if (!shouldClamp) return;

    isAdjustingRangeRef.current = true;
    timeScale.setVisibleLogicalRange({ from: nextFrom, to: nextTo });

    requestAnimationFrame(() => {
      isAdjustingRangeRef.current = false;
    });
  }, []);

  const handleVisibleLogicalRangeChange = useCallback(() => {
    if (isAdjustingRangeRef.current) return;

    clampRightEdge();
    maybeLoadMore();
  }, [clampRightEdge, maybeLoadMore]);

  // ── Chart creation ──
  useEffect(() => {
    if (!chartContainerRef.current) return;

    const chart = createChart(chartContainerRef.current, {
      autoSize: true,
      height,
      layout: {
        background: { color: '#ffffff' },
        textColor: '#333333',
      },
      grid: {
        vertLines: { color: '#f0f0f0' },
        horzLines: { color: '#f0f0f0' },
      },
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
        borderColor: '#e0e0e0',
        rightOffset: 0,
        barSpacing: 8,
        fixLeftEdge: false,
        fixRightEdge: true,
        rightBarStaysOnScroll: true,
        lockVisibleTimeRangeOnResize: true,
      },
      rightPriceScale: {
        borderColor: '#e0e0e0',
        autoScale: true,
        scaleMargins: { top: 0.1, bottom: 0.2 },
      },
      leftPriceScale: { visible: false },
      crosshair: { mode: 1 },
      watermark: { visible: false },
      attributionLogo: false,
    });

    // ── Candlestick series ──
    const candlestickSeries = chart.addCandlestickSeries({
      upColor: '#ef5350',
      downColor: '#26a69a',
      borderVisible: false,
      wickUpColor: '#ef5350',
      wickDownColor: '#26a69a',
      autoscaleInfoProvider: (original) => {
        const res = original();
        if (res !== null && res.priceRange) {
          const { minValue, maxValue } = res.priceRange;
          const range = maxValue - minValue;
          const marginExtension = range * 0.5;
          if (minValue - marginExtension < 0) {
            res.priceRange.minValue = marginExtension * 1.1;
          }
        }
        return res;
      },
    });

    candlestickSeries.priceScale().applyOptions({
      scaleMargins: { top: 0.1, bottom: 0.3 },
    });

    // ── Volume histogram series ──
    const volumeSeries = chart.addHistogramSeries({
      color: '#26a69a',
      priceFormat: { type: 'volume' },
      priceScaleId: 'volume',
    });

    volumeSeries.priceScale().applyOptions({
      scaleMargins: { top: 0.8, bottom: 0.02 },
    });

    chartRef.current = chart;
    candlestickSeriesRef.current = candlestickSeries;
    volumeSeriesRef.current = volumeSeries;

    chart.timeScale().subscribeVisibleLogicalRangeChange(handleVisibleLogicalRangeChange);

    return () => {
      chart.timeScale().unsubscribeVisibleLogicalRangeChange(handleVisibleLogicalRangeChange);
      chart.remove();
      chartRef.current = null;
      candlestickSeriesRef.current = null;
      volumeSeriesRef.current = null;
    };
  }, [height, handleVisibleLogicalRangeChange]);

  // ── Data update ──
  useEffect(() => {
    if (!candlestickSeriesRef.current || !volumeSeriesRef.current) return;
    if (!candles || candles.length === 0) return;

    const candlestickData = candles.map((c) => ({
      time: c.time,
      open: c.open,
      high: c.high,
      low: c.low,
      close: c.close,
    }));

    const volumeData = candles.map((c) => ({
      time: c.time,
      value: c.volume,
      color: c.close >= c.open ? '#ef535080' : '#26a69a80',
    }));

    try {
      const isInitialLoad = lastCandlesLengthRef.current === 0;
      const prevLength = lastCandlesLengthRef.current;
      const addedCount = candles.length - prevLength;

      if (!isInitialLoad && addedCount > 0 && chartRef.current) {
        // 과거 데이터 추가 — 뷰 위치 유지
        const timeScale = chartRef.current.timeScale();
        const logicalRange = timeScale.getVisibleLogicalRange();

        candlestickSeriesRef.current.setData(candlestickData);
        volumeSeriesRef.current.setData(volumeData);

        if (logicalRange) {
          const newFrom = logicalRange.from + addedCount;
          const newTo = logicalRange.to + addedCount;

          isAdjustingRangeRef.current = true;
          timeScale.setVisibleLogicalRange({ from: newFrom, to: newTo });

          requestAnimationFrame(() => {
            isAdjustingRangeRef.current = false;
            const updatedRange = timeScale.getVisibleLogicalRange();
            if (updatedRange && updatedRange.from < 0) {
              maybeLoadMore();
            }
          });
        }

        lastCandlesLengthRef.current = candles.length;
      } else {
        candlestickSeriesRef.current.setData(candlestickData);
        volumeSeriesRef.current.setData(volumeData);
        lastCandlesLengthRef.current = candles.length;

        if (isInitialLoad && chartRef.current) {
          chartRef.current.timeScale().fitContent();
          requestAnimationFrame(() => {
            clampRightEdge();
            maybeLoadMore();
          });
        }
      }
    } catch (err) {
      console.error('Chart update failed:', err);
    }
  }, [candles, clampRightEdge, maybeLoadMore]);

  // ── Reset on full loading ──
  useEffect(() => {
    if (loading) {
      lastCandlesLengthRef.current = 0;
      isLoadRequestPendingRef.current = false;
    }
  }, [loading]);

  // ── Loading-more lifecycle ──
  const prevLoadingMoreRef = useRef(loadingMore);
  useEffect(() => {
    const wasLoading = prevLoadingMoreRef.current;
    prevLoadingMoreRef.current = loadingMore;

    if (!loadingMore) {
      isLoadRequestPendingRef.current = false;
    }

    if (wasLoading && !loadingMore) {
      requestAnimationFrame(() => {
        clampRightEdge();
      });
    }
  }, [loadingMore, clampRightEdge]);

  // ── Render ──
  return (
    <div className="relative">
      <style>{`
        .tv-lightweight-charts a[href*="tradingview"],
        .tv-lightweight-charts a[target="_blank"],
        [class*="tv-lightweight-charts"] a,
        div[style*="position: absolute"] > a[href*="tradingview"] {
          display: none !important;
          visibility: hidden !important;
          opacity: 0 !important;
          pointer-events: none !important;
        }
        .stock-chart-container table tr td:first-child {
          width: 0 !important;
          min-width: 0 !important;
          padding: 0 !important;
        }
      `}</style>

      <div
        ref={chartContainerRef}
        className="stock-chart-container"
        style={{ height: `${height}px`, width: '100%' }}
      />

      {/* Full loading overlay */}
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center bg-white/75">
          <div className="flex items-center gap-2 text-slate-500">
            <svg className="h-5 w-5 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
            <span>로딩 중...</span>
          </div>
        </div>
      )}

      {/* Past-data loading indicator */}
      {loadingMore && !loading && (
        <div className="absolute left-2 top-2 flex items-center gap-2 rounded-lg border border-slate-200 bg-white/90 px-3 py-1.5 shadow-sm">
          <svg className="h-4 w-4 animate-spin text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
          <span className="text-xs text-slate-600">과거 데이터 로딩...</span>
        </div>
      )}

      {/* Empty state */}
      {!loading && (!candles || candles.length === 0) && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-50">
          <p className="text-slate-500">종목을 검색하세요</p>
        </div>
      )}
    </div>
  );
}
