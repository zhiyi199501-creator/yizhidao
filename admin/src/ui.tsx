import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import type { UsagePoint } from "./api";
import { formatCompact, formatNumber } from "./format";

export function Bars({ points }: { points: UsagePoint[] }) {
  return (
    <TrendChart
      title="调用"
      total={formatNumber(points.reduce((sum, point) => sum + point.calls, 0))}
      points={points}
      valueOf={(point) => point.calls}
      color="var(--chart-calls)"
    />
  );
}

function niceMax(value: number): number {
  if (value <= 0) return 1;
  const exp = 10 ** Math.floor(Math.log10(value));
  const mantissa = value / exp;
  const nice = mantissa <= 1 ? 1 : mantissa <= 2 ? 2 : mantissa <= 5 ? 5 : 10;
  return nice * exp;
}

function xLabel(date: string): string {
  if (date.includes("T")) {
    const hour = date.slice(11, 13);
    return `${hour}:00`;
  }
  return date.slice(5).replace("-", "/");
}

function pickLabels(points: UsagePoint[]): Set<number> {
  const last = points.length - 1;
  if (last <= 0) return new Set([0]);
  if (points.length <= 8) return new Set(points.map((_, index) => index));
  const step = Math.max(1, Math.round(last / 3));
  return new Set([0, step, Math.min(last, step * 2), last]);
}

export function TrendChart({
  title,
  total,
  points,
  valueOf,
  color,
  formatTick = formatCompact,
}: {
  title: string;
  total: string;
  points: UsagePoint[];
  valueOf: (point: UsagePoint) => number;
  color: string;
  formatTick?: (value: number) => string;
}) {
  const [hover, setHover] = useState<number | null>(null);
  const width = 640;
  const height = 220;
  const pad = { top: 16, right: 12, bottom: 28, left: 44 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;
  const values = points.map(valueOf);
  const max = niceMax(Math.max(0, ...values));
  const ticks = [0, max / 2, max];
  const labels = useMemo(() => pickLabels(points), [points]);

  const coords = points.map((point, index) => {
    const x = points.length === 1 ? innerW / 2 : (index / Math.max(1, points.length - 1)) * innerW;
    const y = innerH - (valueOf(point) / max) * innerH;
    return { x, y, point, value: valueOf(point) };
  });

  const line =
    coords.length === 0
      ? ""
      : coords
          .map((point, index) => `${index === 0 ? "M" : "L"} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`)
          .join(" ");
  const area =
    coords.length === 0
      ? ""
      : `${line} L ${coords[coords.length - 1].x.toFixed(1)} ${innerH} L ${coords[0].x.toFixed(1)} ${innerH} Z`;
  const active = hover == null ? null : coords[hover];

  return (
    <div className="trend">
      <div className="trend-head">
        <p className="label">{title}</p>
        <p className="trend-total">{total}</p>
      </div>
      <div className="trend-plot">
        <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={title}>
          <g transform={`translate(${pad.left},${pad.top})`}>
            {ticks.map((tick) => {
              const y = innerH - (tick / max) * innerH;
              return (
                <g key={tick}>
                  <line className="trend-grid" x1={0} x2={innerW} y1={y} y2={y} />
                  <text className="trend-y" x={-8} y={y + 4} textAnchor="end">
                    {formatTick(tick)}
                  </text>
                </g>
              );
            })}
            <path className="trend-area" d={area} style={{ fill: color }} />
            <path className="trend-line" d={line} style={{ stroke: color }} />
            {active ? (
              <>
                <line className="trend-cross" x1={active.x} x2={active.x} y1={0} y2={innerH} />
                <circle cx={active.x} cy={active.y} r={5} fill="#fff" style={{ stroke: color }} strokeWidth={2} />
              </>
            ) : coords.length ? (
              <circle
                cx={coords[coords.length - 1].x}
                cy={coords[coords.length - 1].y}
                r={3.5}
                style={{ fill: color }}
              />
            ) : null}
            {coords.map((item, index) => (
              <rect
                key={item.point.date}
                x={index === 0 ? 0 : item.x - innerW / points.length / 2}
                y={0}
                width={innerW / Math.max(1, points.length)}
                height={innerH}
                fill="transparent"
                onMouseEnter={() => setHover(index)}
                onMouseLeave={() => setHover(null)}
              />
            ))}
            {coords.map((item, index) =>
              labels.has(index) ? (
                <text key={`x-${item.point.date}`} className="trend-x" x={item.x} y={innerH + 18} textAnchor="middle">
                  {xLabel(item.point.date)}
                </text>
              ) : null,
            )}
          </g>
        </svg>
        {active ? (
          <div className="trend-tip">
            {xLabel(active.point.date)} · {formatNumber(active.value)}
          </div>
        ) : null}
      </div>
    </div>
  );
}

export function Stat({
  label,
  value,
  hint,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
}) {
  return (
    <div className="card">
      <p className="label">{label}</p>
      <p className="value">{value}</p>
      {hint ? <p className="hint">{hint}</p> : null}
    </div>
  );
}
