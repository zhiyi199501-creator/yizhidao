import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AiEventsPage, AiUsage, ApiError, RangeKey, api } from "../api";
import { KIND_LABELS, formatCost, formatDateTime, formatNumber, formatPercent } from "../format";
import { Bars, Stat } from "../ui";

const RANGES: { id: RangeKey; label: string }[] = [
  { id: "today", label: "今天" },
  { id: "7d", label: "近 7 日" },
  { id: "30d", label: "近 30 日" },
];

export function AiPage() {
  const [range, setRange] = useState<RangeKey>("today");
  const [page, setPage] = useState(1);
  const [data, setData] = useState<AiUsage | null>(null);
  const [events, setEvents] = useState<AiEventsPage | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    setPage(1);
  }, [range]);

  useEffect(() => {
    Promise.all([api.ai(range), api.aiEvents(range, page)])
      .then(([usage, list]) => {
        setData(usage);
        setEvents(list);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "加载失败"));
  }, [range, page]);

  return (
    <div className="stack">
      <div className="page-title">
        <h2>AI 用量</h2>
        <div className="tabs">
          {RANGES.map((item) => (
            <button key={item.id} className={item.id === range ? "active" : ""} onClick={() => setRange(item.id)}>
              {item.label}
            </button>
          ))}
        </div>
      </div>
      {error ? <p className="error">{error}</p> : null}
      {data ? (
        <>
          <div className="grid stats">
            <Stat
              label="调用"
              value={formatNumber(data.summary.calls)}
              hint={`解读 ${data.summary.analyze} · 追问 ${data.summary.followup}`}
            />
            <Stat label="成功率" value={formatPercent(data.summary.successRate)} hint={`限流 ${data.summary.rateLimited}`} />
            <Stat
              label="耗时 p50 / p95"
              value={`${data.summary.latencyMsP50 ?? "—"} / ${data.summary.latencyMsP95 ?? "—"} ms`}
            />
            <Stat
              label="token"
              value={formatNumber(data.summary.tokens)}
              hint={formatCost(data.summary.cost.configured, data.summary.cost.usd)}
            />
          </div>
          <div className="card">
            <p className="label">调用趋势</p>
            <Bars points={data.series} />
          </div>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr 1fr" }}>
            <div className="card">
              <p className="label">失败码</p>
              {!data.errors.length ? <p className="muted">无</p> : null}
              <dl className="kv">
                {data.errors.map((item) => (
                  <div key={item.code} style={{ display: "contents" }}>
                    <dt>{item.code}</dt>
                    <dd>{formatNumber(item.count)}</dd>
                  </div>
                ))}
              </dl>
            </div>
            <div className="card">
              <p className="label">起卦方式</p>
              <dl className="kv">
                {data.methods.map((item) => (
                  <div key={item.method} style={{ display: "contents" }}>
                    <dt>{item.label}</dt>
                    <dd>{formatNumber(item.count)}</dd>
                  </div>
                ))}
              </dl>
            </div>
            <div className="card">
              <p className="label">调用最多</p>
              {!data.topUsers.length ? <p className="muted">无</p> : null}
              <ul style={{ paddingLeft: 18, margin: "10px 0 0" }}>
                {data.topUsers.map((item) => (
                  <li key={item.id}>
                    <Link to={`/users/${item.id}`}>{item.id}</Link>
                    <span className="muted"> · {formatNumber(item.calls)}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </>
      ) : (
        <p className="muted">加载中…</p>
      )}
      <div className="card table-wrap">
        <p className="label">调用明细（无问题、无正文）</p>
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>用户</th>
              <th>类型</th>
              <th>结果</th>
              <th>耗时</th>
              <th>token</th>
              <th>模型</th>
            </tr>
          </thead>
          <tbody>
            {(events?.events || []).map((event) => (
              <tr key={event.id}>
                <td>{formatDateTime(event.createdAt)}</td>
                <td>
                  <Link to={`/users/${event.userId}`}>{event.nickname || event.userId}</Link>
                </td>
                <td>{KIND_LABELS[event.kind] || event.kind}</td>
                <td className={event.ok ? "ok" : "warn"}>
                  {event.ok ? "成功" : event.errorCode ?? "失败"}
                </td>
                <td>{formatNumber(event.latencyMs)} ms</td>
                <td>{formatNumber(event.promptTokens + event.completionTokens)}</td>
                <td>{event.model}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="pager">
        <button className="btn" disabled={page <= 1} onClick={() => setPage((n) => n - 1)}>
          上一页
        </button>
        <span>共 {formatNumber(events?.total || 0)} 条</span>
        <button
          className="btn"
          disabled={!events || page * events.pageSize >= events.total}
          onClick={() => setPage((n) => n + 1)}
        >
          下一页
        </button>
      </div>
    </div>
  );
}
