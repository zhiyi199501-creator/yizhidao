import { useEffect, useState } from "react";
import { ApiError, EvalRun, EvalSample, api } from "../api";

export function EvalPage() {
  const [samples, setSamples] = useState<EvalSample[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [live, setLive] = useState(false);
  const [result, setResult] = useState<EvalRun | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .evalSamples()
      .then((body) => {
        setSamples(body.samples);
        setSelected(body.samples.map((item) => item.id));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "加载失败"));
  }, []);

  async function run() {
    setBusy(true);
    setError("");
    try {
      setResult(await api.evalRun(selected, live));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "抽检失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <div className="page-title">
        <h2>AI 抽检</h2>
        <div className="meta">固定夹具 · 不是用户所问</div>
      </div>
      <p className="banner">
        只跑 <code>eval_fixtures.py</code> 里的假问题：检查焦点、黄庭 id、案例条数。可选按现役 AI_MODE 出卡，结果只给管理员看，不写入用量事件，也不碰用户表。
      </p>
      {error ? <p className="error">{error}</p> : null}
      <div className="card stack">
        {samples.map((sample) => (
          <label key={sample.id} className="check-row">
            <input
              type="checkbox"
              checked={selected.includes(sample.id)}
              onChange={(event) => {
                setSelected((prev) =>
                  event.target.checked ? [...prev, sample.id] : prev.filter((id) => id !== sample.id),
                );
              }}
            />
            <span>
              <strong>{sample.title}</strong>
              <span className="muted"> · {sample.question}</span>
            </span>
          </label>
        ))}
      </div>
      <div className="toolbar">
        <label className="check-row">
          <input type="checkbox" checked={live} onChange={(event) => setLive(event.target.checked)} />
          真实出卡（走现役 AI_MODE；mock 也会出样卡）
        </label>
        <button className="btn" type="button" onClick={run} disabled={busy || !selected.length}>
          {busy ? "抽检中…" : "开始抽检"}
        </button>
      </div>
      {result ? (
        <div className="stack">
          <p className={result.pass ? "ok" : "error"}>
            {result.pass ? "槽位全部通过" : "有样本未通过"} · AI_MODE={result.aiMode}
            {result.live ? " · 已出卡" : " · 仅槽位"}
          </p>
          <p className="muted">{result.note}</p>
          {result.samples.map((item) => (
            <div className="card stack" key={item.inspect.id}>
              <div className="page-title" style={{ marginBottom: 0 }}>
                <h3 style={{ margin: 0, fontSize: 18 }}>{item.inspect.title}</h3>
                <div className={item.inspect.pass ? "ok" : "warn"}>{item.inspect.pass ? "通过" : "未过"}</div>
              </div>
              <p className="muted">{item.inspect.caseCaption}</p>
              <p className="mono">{item.inspect.ima.join(" · ")}</p>
              <p className="muted">
                黄庭命中 {item.inspect.checks.imaPresent ? "是" : "否"} · 不该出现的已排除{" "}
                {item.inspect.checks.imaAbsent ? "是" : "否"} · 案例 {item.inspect.caseCount} 条{" "}
                {item.inspect.checks.cases ? "合格" : "不合格"} · prompt {item.inspect.promptChars} 字
              </p>
              {item.error ? <p className="error">{item.error}</p> : null}
              {item.analysis ? <pre className="pre">{JSON.stringify(item.analysis, null, 2)}</pre> : null}
            </div>
          ))}
          {result.followup ? (
            <div className="card">
              <p className="label">追问</p>
              <p>{result.followup.message}</p>
              {result.followup.error ? <p className="error">{result.followup.error}</p> : null}
              {result.followup.reply ? <p>{result.followup.reply}</p> : null}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
