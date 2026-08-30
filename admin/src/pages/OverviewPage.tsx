import { useEffect, useState } from "react";
import { ApiError, Overview, api } from "../api";
import { LOGIN_LABELS, formatCost, formatNumber, formatPercent } from "../format";
import { Bars, Stat } from "../ui";

export function OverviewPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.overview().then(setData).catch((err) => {
      setError(err instanceof ApiError ? err.message : "加载失败");
    });
  }, []);

  if (error) return <p className="error">{error}</p>;
  if (!data) return <p className="muted">加载中…</p>;

  const healthBits = [
    data.health.casesLoaded ? `案例 ${data.health.casesCount}` : "案例未加载",
    data.health.hexagramsLoaded ? `经文 ${data.health.hexagramsCount}` : "经文未加载",
    data.health.imaLoaded ? `黄庭 ${data.health.imaCount}` : "黄庭未加载",
  ];

  return (
    <div className="stack">
      <div className="page-title">
        <h2>总览</h2>
        <div className="meta">
          {data.health.aiMode} · {data.health.model} · {healthBits.join(" · ")}
          {data.feedback?.unread ? ` · 未读反馈 ${data.feedback.unread}` : ""}
        </div>
      </div>
      <div className="grid stats">
        <Stat label="累计用户" value={formatNumber(data.users.total)} hint={`今日 +${data.users.today}，近 7 日 +${data.users.last7d}`} />
        <Stat
          label="今日 AI 调用"
          value={formatNumber(data.aiToday.calls)}
          hint={`解读 ${data.aiToday.analyze} · 追问 ${data.aiToday.followup}`}
        />
        <Stat label="今日成功率" value={formatPercent(data.aiToday.successRate)} hint={`限流 ${data.aiToday.rateLimited} · 其他失败 ${data.aiToday.failedOther}`} />
        <Stat
          label="今日 token"
          value={formatNumber(data.aiToday.tokens)}
          hint={formatCost(data.aiToday.cost.configured, data.aiToday.cost.usd)}
        />
      </div>
      <div className="grid" style={{ gridTemplateColumns: "1.4fr 1fr" }}>
        <div className="card">
          <p className="label">近 7 日调用</p>
          <Bars points={data.aiLast7d} />
        </div>
        <div className="card">
          <p className="label">登录构成（主方式）</p>
          <dl className="kv" style={{ marginTop: 12 }}>
            {Object.entries(data.users.loginMix).map(([key, count]) => (
              <div key={key} style={{ display: "contents" }}>
                <dt>{LOGIN_LABELS[key] || key}</dt>
                <dd>{formatNumber(count)}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </div>
  );
}
