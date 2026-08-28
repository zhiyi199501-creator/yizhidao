import { useEffect, useState } from "react";
import { ApiError, ImaEntry, ImaIndexItem, api } from "../api";

export function ImaPage() {
  const [hexagrams, setHexagrams] = useState<ImaIndexItem[]>([]);
  const [number, setNumber] = useState(1);
  const [entries, setEntries] = useState<ImaEntry[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [saved, setSaved] = useState("");
  const [busyId, setBusyId] = useState("");

  useEffect(() => {
    api.imaIndex().then((body) => setHexagrams(body.hexagrams)).catch((err) => {
      setError(err instanceof ApiError ? err.message : "加载失败");
    });
  }, []);

  useEffect(() => {
    setSaved("");
    api
      .imaHexagram(number)
      .then((body) => {
        setEntries(body.entries);
        setDrafts(Object.fromEntries(body.entries.map((item) => [item.id, item.answer])));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "加载失败"));
  }, [number]);

  async function save(entry: ImaEntry) {
    setBusyId(entry.id);
    setError("");
    setSaved("");
    try {
      const result = await api.saveImaAnswer(entry.id, drafts[entry.id] ?? "");
      setEntries((prev) => prev.map((item) => (item.id === entry.id ? result.entry : item)));
      setSaved(result.note);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "保存失败");
    } finally {
      setBusyId("");
    }
  }

  return (
    <div className="stack">
      <div className="page-title">
        <h2>黄庭讲解</h2>
        <div className="meta">只改 answer · title / 经文只读</div>
      </div>
      <p className="banner">
        保存后<strong>服务端 AI 立刻</strong>用新稿。App 点经文弹层读的是包内 JSON，要<strong>下次发 App</strong>才变。不要再跑
        export_ima_explanations.py（会覆盖手改）。
      </p>
      {error ? <p className="error">{error}</p> : null}
      {saved ? <p className="ok">{saved}</p> : null}
      <div className="hex-grid">
        {hexagrams.map((item) => (
          <button
            key={item.number}
            type="button"
            className={item.number === number ? "active" : ""}
            onClick={() => setNumber(item.number)}
          >
            {item.number} {item.name}
          </button>
        ))}
      </div>
      {entries.map((entry) => (
        <section className="card stack" key={entry.id}>
          <div className="page-title" style={{ marginBottom: 0 }}>
            <h3 style={{ margin: 0, fontSize: 18 }}>{entry.title || entry.id}</h3>
            <div className="meta mono">{entry.id}</div>
          </div>
          <p className="scripture">{entry.scripture || "（无经文摘录）"}</p>
          <label className="field">
            讲解
            <textarea
              rows={12}
              value={drafts[entry.id] ?? ""}
              onChange={(event) => setDrafts((prev) => ({ ...prev, [entry.id]: event.target.value }))}
            />
          </label>
          <div>
            <button className="btn" type="button" disabled={busyId === entry.id} onClick={() => save(entry)}>
              {busyId === entry.id ? "保存中…" : "保存"}
            </button>
          </div>
        </section>
      ))}
      {!entries.length ? <p className="muted">这一卦没有讲解条目</p> : null}
    </div>
  );
}
