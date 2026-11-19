import { formatPercent, formatCurrency, getProfitLossColor } from '../../lib/format';

/**
 * 타겟 기반 프로그레스 계산 유틸리티
 *
 * 완료되지 않은 타겟 중 다음 타겟을 자동 탐색하고,
 * 다단계 타겟(1차/2차/...) 진행률을 누적 계산합니다.
 *
 * @param {number} currentPrice - 현재가
 * @param {number} averagePrice - 평균 매입가
 * @param {Array} takeProfitTargets - 익절 타겟 [{price, quantity, completed}]
 * @param {Array} stopLossTargets - 손절 타겟 [{price, quantity, completed}]
 * @returns {{ progress: number, direction: 'profit'|'loss'|'neutral', targetInfo: object|null, profitRate: number }}
 */
export function calculateTargetProgress(currentPrice, averagePrice, takeProfitTargets = [], stopLossTargets = []) {
  const validTp = (takeProfitTargets || []).filter((t) => t.price && !t.completed);
  const validSl = (stopLossTargets || []).filter((t) => t.price && !t.completed);

  if (!currentPrice || !averagePrice) {
    return { progress: 0, direction: 'neutral', targetInfo: null, profitRate: 0 };
  }
  if (validTp.length === 0 && validSl.length === 0) {
    return { progress: 0, direction: 'neutral', targetInfo: null, profitRate: ((currentPrice - averagePrice) / averagePrice) * 100 };
  }

  const sortedTp = [...validTp].sort((a, b) => a.price - b.price);
  const sortedSl = [...validSl].sort((a, b) => b.price - a.price);

  const isProfit = currentPrice > averagePrice;
  const profitRate = ((currentPrice - averagePrice) / averagePrice) * 100;

  let progress = 0;
  let targetInfo = null;
  let direction = 'neutral';

  if (isProfit && sortedTp.length > 0) {
    direction = 'profit';
    let accumulated = 0;
    const perTarget = 100 / sortedTp.length;

    for (let i = 0; i < sortedTp.length; i++) {
      const target = sortedTp[i];
      const prevPrice = i === 0 ? averagePrice : sortedTp[i - 1].price;

      if (currentPrice >= target.price) {
        accumulated += perTarget;
      } else {
        const rangeTotal = target.price - prevPrice;
        const rangeCurrent = currentPrice - prevPrice;
        const rangeProgress = rangeTotal > 0 ? (rangeCurrent / rangeTotal) * perTarget : 0;
        accumulated += Math.max(0, rangeProgress);
        targetInfo = {
          index: i + 1,
          price: target.price,
          total: sortedTp.length,
          targetQty: target.quantity,
          type: 'takeProfit',
        };
        break;
      }
    }

    progress = Math.min(100, accumulated);
    if (!targetInfo && sortedTp.length > 0) {
      targetInfo = {
        index: sortedTp.length,
        price: sortedTp[sortedTp.length - 1].price,
        total: sortedTp.length,
        targetQty: sortedTp[sortedTp.length - 1].quantity,
        type: 'takeProfit',
        completed: true,
      };
    }
  } else if (!isProfit && sortedSl.length > 0) {
    direction = 'loss';
    let accumulated = 0;
    const perTarget = 100 / sortedSl.length;

    for (let i = 0; i < sortedSl.length; i++) {
      const target = sortedSl[i];
      const prevPrice = i === 0 ? averagePrice : sortedSl[i - 1].price;

      if (currentPrice <= target.price) {
        accumulated += perTarget;
      } else {
        const rangeTotal = prevPrice - target.price;
        const rangeCurrent = prevPrice - currentPrice;
        const rangeProgress = rangeTotal > 0 ? (rangeCurrent / rangeTotal) * perTarget : 0;
        accumulated += Math.max(0, rangeProgress);
        targetInfo = {
          index: i + 1,
          price: target.price,
          total: sortedSl.length,
          targetQty: target.quantity,
          type: 'stopLoss',
        };
        break;
      }
    }

    progress = Math.min(100, accumulated);
    if (!targetInfo && sortedSl.length > 0) {
      targetInfo = {
        index: sortedSl.length,
        price: sortedSl[sortedSl.length - 1].price,
        total: sortedSl.length,
        targetQty: sortedSl[sortedSl.length - 1].quantity,
        type: 'stopLoss',
        completed: true,
      };
    }
  }

  return { progress, direction, targetInfo, profitRate };
}

// ── 바 fill 클래스 (light-only) ──

function getBarFillClass(dir, nearTarget) {
  if (dir === 'profit') {
    return nearTarget
      ? 'bg-gradient-to-r from-red-400 via-red-500 to-orange-400 animate-pulse'
      : 'bg-red-500';
  }
  if (dir === 'loss') {
    return nearTarget
      ? 'bg-gradient-to-r from-blue-400 via-blue-500 to-indigo-400 animate-pulse'
      : 'bg-blue-500';
  }
  return 'bg-slate-400';
}

function getRingClass(direction, progress) {
  if (progress < 70) return '';
  if (progress >= 90) {
    return `ring-2 ring-offset-1 ring-offset-white ${
      direction === 'profit' ? 'ring-red-300' : 'ring-blue-300'
    }`;
  }
  return `ring-1 ring-offset-1 ring-offset-white ${
    direction === 'profit' ? 'ring-red-400' : 'ring-blue-400'
  }`;
}

// ── Size maps ──

const BAR_SIZE = { xs: 'h-1.5', sm: 'h-1.5', md: 'h-2', lg: 'h-2.5' };
const TEXT_SIZE = { xs: 'text-xs', sm: 'text-xs', md: 'text-sm', lg: 'text-base font-medium' };

/**
 * 풀사이즈 타겟 프로그레스 바
 *
 * 수익률 % | 타겟 상태 텍스트 | 그라데이션 진행 바 | 가격범위 (평균매입가 -> 타겟가)
 * 70%+ 진행 시 pulse 애니메이션, 90%+ 진행 시 ring highlight
 *
 * @param {number} currentPrice - 현재가
 * @param {number} averagePrice - 평균 매입가
 * @param {Array} takeProfitTargets - 익절 타겟 [{price, quantity, completed}]
 * @param {Array} stopLossTargets - 손절 타겟 [{price, quantity, completed}]
 * @param {string} market - 시장 (KRX, NASDAQ 등)
 * @param {string} size - 바 크기 ('xs' | 'sm' | 'md' | 'lg')
 */
export function TargetProgressBar({
  currentPrice,
  averagePrice,
  takeProfitTargets = [],
  stopLossTargets = [],
  market,
  size = 'md',
}) {
  const validTp = (takeProfitTargets || []).filter((t) => t.price && !t.completed);
  const validSl = (stopLossTargets || []).filter((t) => t.price && !t.completed);

  if (validTp.length === 0 && validSl.length === 0) return null;
  if (!currentPrice || !averagePrice) return null;

  const { progress, direction, targetInfo, profitRate } = calculateTargetProgress(
    currentPrice,
    averagePrice,
    takeProfitTargets,
    stopLossTargets,
  );

  const isNearTarget = progress >= 70;

  return (
    <div className="space-y-1">
      {/* 수익률 + 타겟 라벨 */}
      <div className="flex items-center justify-between">
        <span className={`${TEXT_SIZE[size]} ${getProfitLossColor(profitRate / 100)} font-medium`}>
          {profitRate >= 0 ? '+' : ''}
          {profitRate.toFixed(2)}%
        </span>
        {targetInfo && (
          <span className="text-xs text-slate-500">
            {targetInfo.completed
              ? direction === 'profit'
                ? '익절 도달'
                : '손절 도달'
              : `${targetInfo.index}차 ${direction === 'profit' ? '익절' : '손절'}`}
          </span>
        )}
      </div>

      {/* 프로그레스 바 */}
      <div
        className={`w-full ${BAR_SIZE[size]} rounded-full overflow-hidden bg-slate-200 ${getRingClass(direction, progress)}`}
      >
        <div
          className={`h-full rounded-full transition-all duration-500 ${getBarFillClass(direction, isNearTarget)}`}
          style={{ width: `${progress}%` }}
        />
      </div>

      {/* 평단가 -> 타겟가 */}
      {targetInfo && !targetInfo.completed && (
        <div className="flex items-center justify-between text-xs text-slate-500">
          <span>{formatCurrency(averagePrice, market)}</span>
          <span className={direction === 'profit' ? 'text-red-500' : 'text-blue-500'}>
            {formatCurrency(targetInfo.price, market)}
          </span>
        </div>
      )}
    </div>
  );
}

/**
 * 타겟 기반 수익률 프로그레스 바 (기존 호환)
 *
 * TargetProgressBar와 동일한 기능이지만, 기존 import 호환을 위해 유지.
 * 익절/손절 타겟이 있을 때만 렌더링.
 */
export function ProfitProgressBar({
  currentPrice,
  averagePrice,
  takeProfitTargets = [],
  stopLossTargets = [],
  market,
  size = 'md',
}) {
  return (
    <TargetProgressBar
      currentPrice={currentPrice}
      averagePrice={averagePrice}
      takeProfitTargets={takeProfitTargets}
      stopLossTargets={stopLossTargets}
      market={market}
      size={size}
    />
  );
}

/**
 * 미니 타겟 프로그레스 바 (인라인 / 목록용)
 *
 * calculateTargetProgress를 내부에서 호출하므로 외부 계산 불필요.
 * 70% 이상 진행 시 그라데이션 + pulse 자동 적용.
 */
export function MiniTargetProgressBar({
  currentPrice,
  averagePrice,
  takeProfitTargets = [],
  stopLossTargets = [],
  size = 'xs',
}) {
  const validTp = (takeProfitTargets || []).filter((t) => t.price && !t.completed);
  const validSl = (stopLossTargets || []).filter((t) => t.price && !t.completed);

  if (validTp.length === 0 && validSl.length === 0) return null;
  if (!currentPrice || !averagePrice) return null;

  const { progress, direction } = calculateTargetProgress(
    currentPrice,
    averagePrice,
    takeProfitTargets,
    stopLossTargets,
  );

  const isNearTarget = progress >= 70;

  const miniSize = { xs: 'w-16 h-1.5', sm: 'w-20 h-2' };

  return (
    <div
      className={`${miniSize[size]} rounded-full overflow-hidden bg-slate-200 ${getRingClass(direction, progress)}`}
    >
      <div
        className={`h-full rounded-full transition-all ${getBarFillClass(direction, isNearTarget)}`}
        style={{ width: `${progress}%` }}
      />
    </div>
  );
}

/**
 * 수익률 배지 (바 없이 색상 배지만 표시)
 *
 * +12.3% (빨강) 또는 -5.2% (파랑) 형태의 간결한 배지.
 *
 * @param {number} value - 수익률 (소수점, 예: 0.123 = 12.3%)
 * @param {string} size - 배지 크기 ('sm' | 'md' | 'lg')
 */
export function ProfitBadge({ value, size = 'md' }) {
  if (value == null) {
    return <span className="text-slate-400">-</span>;
  }

  const isProfit = value > 0;
  const textSizeClasses = {
    sm: 'text-xs px-1.5 py-0.5',
    md: 'text-sm px-2 py-0.5',
    lg: 'text-base px-2.5 py-1',
  };

  const bgClass = isProfit
    ? 'bg-red-100 text-red-700'
    : value < 0
      ? 'bg-blue-100 text-blue-700'
      : 'bg-slate-100 text-slate-600';

  return (
    <span className={`${textSizeClasses[size]} ${bgClass} rounded font-medium`}>
      {isProfit ? '+' : ''}{formatPercent(value)}
    </span>
  );
}
