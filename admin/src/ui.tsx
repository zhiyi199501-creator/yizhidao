import type { ReactNode } from "react";

export function Bars({ points }: { points: { date: string; calls: number }[] }) {
  const max = Math.max(1, ...points.map((point) => point.calls));
  return (
    <>
      <div className="bars">
        {points.map((point) => (
          <div className="bar" key={point.date} title={`${point.date} ${point.calls}`}>
            <span style={{ height: `${Math.max(4, (point.calls / max) * 100)}%` }} />
          </div>
        ))}
      </div>
      <div className="bar-labels">
        {points.map((point) => (
          <span key={point.date}>{point.date.slice(5)}</span>
        ))}
      </div>
    </>
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
