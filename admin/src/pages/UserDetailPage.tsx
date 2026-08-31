import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError, UserDetail, api } from "../api";
import { LOGIN_LABELS, formatDateTime, formatNumber } from "../format";
import { Stat, TrendChart } from "../ui";

export function UserDetailPage() {
  const { userId = "" } = useParams();
  const [data, setData] = useState<UserDetail | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.user(userId).then(setData).catch((err) => {
      setError(err instanceof ApiError ? err.message : "加载失败");
    });
  }, [userId]);

  if (error) return <p className="error">{error}</p>;
  if (!data) return <p className="muted">加载中…</p>;
  const user = data.user;

  return (
    <div className="stack">
      <div className="page-title">
        <h2>{user.nickname}</h2>
        <Link className="meta" to="/users">
          返回列表
        </Link>
      </div>
      <div className="grid stats">
        <Stat label="今日 AI" value={formatNumber(user.aiToday)} />
        <Stat label="累计 AI" value={formatNumber(user.aiTotal)} />
        <Stat label="注册" value={formatDateTime(user.createdAt)} />
        <Stat label="最近登录" value={formatDateTime(user.lastLoginAt)} />
      </div>
      <div className="card">
        <p className="label">账号</p>
        <dl className="kv" style={{ marginTop: 12 }}>
          <dt>id</dt>
          <dd className="mono">{user.id}</dd>
          <dt>邮箱</dt>
          <dd>{user.email || "—"}</dd>
          <dt>手机</dt>
          <dd>{user.phone || "—"}</dd>
          <dt>登录方式</dt>
          <dd>{user.loginMethods.map((method) => LOGIN_LABELS[method] || method).join(" / ")}</dd>
        </dl>
      </div>
      <div className="card">
        <TrendChart
          title="近 14 日调用"
          total={formatNumber(data.daily.reduce((sum, point) => sum + point.calls, 0))}
          points={data.daily}
          valueOf={(point) => point.calls}
          color="var(--chart-calls)"
        />
      </div>
    </div>
  );
}
