import { FormEvent, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError, ContentCase, HexagramInfo, api } from "../api";

const EMPTY: Omit<ContentCase, "id" | "number" | "updatedAt"> = {
  file: "",
  hexagram: "",
  position: "初爻",
  background: "",
  question: "",
  casting: "",
  explanation: "",
  verification: "",
};

const POSITIONS = ["卦辞", "初爻", "二爻", "三爻", "四爻", "五爻", "上爻"];

export function CaseEditPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isNew = !id || id === "new";
  const [hexagrams, setHexagrams] = useState<HexagramInfo[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.hexagrams().then((body) => setHexagrams(body.hexagrams)).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (isNew) return;
    api
      .case(Number(id))
      .then((body) => {
        const item = body.case;
        setForm({
          file: item.file,
          hexagram: item.hexagram,
          position: item.position,
          background: item.background,
          question: item.question,
          casting: item.casting,
          explanation: item.explanation,
          verification: item.verification,
        });
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "加载失败"));
  }, [id, isNew]);

  const number = useMemo(() => {
    const raw = form.hexagram.replace(/卦$/, "");
    return hexagrams.find((item) => item.name === raw || `${item.name}卦` === form.hexagram)?.number;
  }, [form.hexagram, hexagrams]);

  function setField<K extends keyof typeof EMPTY>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (isNew) await api.createCase(form);
      else await api.updateCase(Number(id), form);
      navigate("/cases");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "保存失败");
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (isNew) return;
    if (!window.confirm(`删除「${form.file}」？未发布前不影响 App。`)) return;
    setBusy(true);
    try {
      await api.deleteCase(Number(id));
      navigate("/cases");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "删除失败");
      setBusy(false);
    }
  }

  return (
    <form className="stack" onSubmit={onSubmit}>
      <div className="page-title">
        <h2>{isNew ? "新增案例" : "编辑案例"}</h2>
        <div className="meta">{number ? `文王序 ${number}` : "卦名将自动换算编号"}</div>
      </div>
      {error ? <p className="error">{error}</p> : null}
      <div className="card form-grid">
        <label>
          编号
          <input value={form.file} onChange={(event) => setField("file", event.target.value)} required />
        </label>
        <label>
          卦名
          <select
            value={form.hexagram}
            onChange={(event) => setField("hexagram", event.target.value)}
            required
          >
            <option value="">选择卦</option>
            {hexagrams.map((item) => {
              const label = `${item.name}卦`;
              return (
                <option key={item.number} value={label}>
                  {item.number}. {label}
                </option>
              );
            })}
            {form.hexagram && !hexagrams.some((item) => `${item.name}卦` === form.hexagram) ? (
              <option value={form.hexagram}>{form.hexagram}</option>
            ) : null}
          </select>
        </label>
        <label>
          爻位
          <input
            list="positions"
            value={form.position}
            onChange={(event) => setField("position", event.target.value)}
            required
          />
          <datalist id="positions">
            {POSITIONS.map((item) => (
              <option key={item} value={item} />
            ))}
          </datalist>
        </label>
      </div>
      {(
        [
          ["background", "背景"],
          ["question", "所问"],
          ["casting", "占得"],
          ["explanation", "讲师解读"],
          ["verification", "验证"],
        ] as const
      ).map(([key, label]) => (
        <label className="card field" key={key}>
          {label}
          <textarea rows={key === "explanation" ? 8 : 4} value={form[key]} onChange={(event) => setField(key, event.target.value)} />
        </label>
      ))}
      <div className="toolbar">
        <button className="btn" type="submit" disabled={busy}>
          {busy ? "保存中…" : "保存到工作副本"}
        </button>
        <button className="btn secondary" type="button" onClick={() => navigate("/cases")}>
          返回
        </button>
        {!isNew ? (
          <button className="btn danger" type="button" onClick={onDelete} disabled={busy}>
            删除
          </button>
        ) : null}
      </div>
    </form>
  );
}
