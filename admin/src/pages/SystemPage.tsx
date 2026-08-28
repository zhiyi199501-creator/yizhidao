import { useEffect, useState } from "react";
import { ApiError, SystemStatus, api } from "../api";

function asText(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "boolean") return value ? "是" : "否";
  return String(value);
}

export function SystemPage() {
  const [data, setData] = useState<SystemStatus | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.system().then(setData).catch((err) => {
      setError(err instanceof ApiError ? err.message : "加载失败");
    });
  }, []);

  if (error) return <p className="error">{error}</p>;
  if (!data) return <p className="muted">加载中…</p>;

  return (
    <div className="stack">
      <div className="page-title">
        <h2>系统</h2>
        <div className={`meta ${data.health ? "ok" : "warn"}`}>{data.health ? "API 可达" : "异常"}</div>
      </div>
      <div className="card">
        <p className="label">配置（不含密钥）</p>
        <dl className="kv" style={{ marginTop: 12 }}>
          {Object.entries(data.config).map(([key, value]) => (
            <div key={key} style={{ display: "contents" }}>
              <dt>{key}</dt>
              <dd>{asText(value)}</dd>
            </div>
          ))}
        </dl>
      </div>
      {Object.entries(data.data).map(([name, info]) => (
        <div className="card" key={name}>
          <p className="label">{name}</p>
          <dl className="kv" style={{ marginTop: 12 }}>
            {Object.entries(info).map(([key, value]) => (
              <div key={key} style={{ display: "contents" }}>
                <dt>{key}</dt>
                <dd className={key === "path" ? "mono" : undefined}>{asText(value)}</dd>
              </div>
            ))}
          </dl>
        </div>
      ))}
      <div className="card">
        <p className="label">限流说明</p>
        <p>{data.rateLimitNote}</p>
      </div>
    </div>
  );
}
