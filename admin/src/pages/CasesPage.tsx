import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError, CasesPage as CasesList, CasesStatus, HexagramInfo, api } from "../api";

export function CasesPage() {
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);
  const [q, setQ] = useState("");
  const [number, setNumber] = useState<number | "">("");
  const [page, setPage] = useState(1);
  const [data, setData] = useState<CasesList | null>(null);
  const [status, setStatus] = useState<CasesStatus | null>(null);
  const [hexagrams, setHexagrams] = useState<HexagramInfo[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState("");

  function reload() {
    Promise.all([api.cases(q, number, page), api.casesStatus()])
      .then(([list, next]) => {
        setData(list);
        setStatus(next);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "加载失败"));
  }

  useEffect(() => {
    api.hexagrams().then((body) => setHexagrams(body.hexagrams)).catch(() => undefined);
  }, []);

  useEffect(() => {
    const handle = window.setTimeout(reload, 200);
    return () => window.clearTimeout(handle);
  }, [q, number, page]);

  async function publish() {
    if (!status) return;
    const ok = window.confirm(
      `工作副本 ${status.draftCount} 条，现役 ${status.publishedCount} 条。\n发布后 App 下次打开「案例」即更新。确定发布？`,
    );
    if (!ok) return;
    setBusy("publish");
    setError("");
    try {
      const result = await api.publishCases();
      setBusy("");
      reload();
      window.alert(`已发布 ${result.count} 条，version ${result.version}`);
    } catch (err) {
      setBusy("");
      setError(err instanceof ApiError ? err.message : "发布失败");
    }
  }

  async function onImport(file: File) {
    setBusy("import");
    setError("");
    try {
      const result = await api.importCases(file);
      const extra = result.problems?.length ? `\n注意：${result.problems.slice(0, 8).join("；")}` : "";
      window.alert(`已导入 ${result.total} 条到工作副本，尚未发布。${extra}`);
      setPage(1);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "导入失败");
    } finally {
      setBusy("");
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  return (
    <div className="stack">
      <div className="page-title">
        <h2>案例</h2>
        <div className="meta">
          草稿 {status?.draftCount ?? "—"} · 现役 {status?.publishedCount ?? "—"}
          {status?.publishedVersion ? ` · ${status.publishedVersion}` : ""}
        </div>
      </div>
      <p className="banner">
        网页里改的是工作副本。点「发布」才写入服务端 cases.json，现役 App 下次打开案例即热更新。导出 JSON 给 git 提交，供下次发 App 打进包内。
      </p>
      <div className="toolbar">
        <button className="btn" onClick={() => navigate("/cases/new")}>
          新增
        </button>
        <button className="btn secondary" onClick={() => fileRef.current?.click()} disabled={Boolean(busy)}>
          导入 Excel / JSON
        </button>
        <button className="btn secondary" onClick={() => api.exportCases().catch((err) => setError(err.message))} disabled={Boolean(busy)}>
          导出 JSON
        </button>
        <button className="btn" onClick={publish} disabled={Boolean(busy)}>
          {busy === "publish" ? "发布中…" : "发布到 App"}
        </button>
        <input
          ref={fileRef}
          type="file"
          accept=".json,.xlsx"
          hidden
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) onImport(file);
          }}
        />
      </div>
      <div className="search">
        <input
          placeholder="搜编号 / 卦名 / 所问"
          value={q}
          onChange={(event) => {
            setPage(1);
            setQ(event.target.value);
          }}
        />
        <select
          value={number}
          onChange={(event) => {
            setPage(1);
            setNumber(event.target.value ? Number(event.target.value) : "");
          }}
        >
          <option value="">全部卦</option>
          {hexagrams.map((item) => (
            <option key={item.number} value={item.number}>
              {item.number}. {item.name}
            </option>
          ))}
        </select>
      </div>
      {error ? <p className="error">{error}</p> : null}
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>编号</th>
              <th>卦</th>
              <th>爻位</th>
              <th>所问</th>
            </tr>
          </thead>
          <tbody>
            {(data?.cases || []).map((item) => (
              <tr key={item.id} className="clickable" onClick={() => navigate(`/cases/${item.id}`)}>
                <td className="mono">{item.file}</td>
                <td>
                  {item.number} {item.hexagram}
                </td>
                <td>{item.position}</td>
                <td className="clip">{item.question || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!data?.cases.length ? <p className="muted">没有匹配的案例</p> : null}
      </div>
      <div className="pager">
        <button className="btn secondary" disabled={page <= 1} onClick={() => setPage((n) => n - 1)}>
          上一页
        </button>
        <span>
          {page} / {Math.max(1, Math.ceil((data?.total || 0) / (data?.pageSize || 30)))}
        </span>
        <button
          className="btn secondary"
          disabled={!data || page * data.pageSize >= data.total}
          onClick={() => setPage((n) => n + 1)}
        >
          下一页
        </button>
      </div>
    </div>
  );
}
