import { useEffect, useState } from "react";
import { ApiError, HexagramInfo, HexagramReading, api } from "../api";

const YAO = ["初", "二", "三", "四", "五", "上"];

export function JingwenPage() {
  const [hexagrams, setHexagrams] = useState<HexagramInfo[]>([]);
  const [number, setNumber] = useState(1);
  const [data, setData] = useState<HexagramReading | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.hexagrams().then((body) => setHexagrams(body.hexagrams)).catch((err) => {
      setError(err instanceof ApiError ? err.message : "加载失败");
    });
  }, []);

  useEffect(() => {
    api
      .hexagram(number)
      .then((body) => setData(body.hexagram))
      .catch((err) => setError(err instanceof ApiError ? err.message : "加载失败"));
  }, [number]);

  return (
    <div className="stack">
      <div className="page-title">
        <h2>经文</h2>
        <div className="meta">只读 · 改字仍走 Excel → import_jingwen.py</div>
      </div>
      {error ? <p className="error">{error}</p> : null}
      <div className="hex-grid">
        {hexagrams.map((item) => (
          <button
            key={item.number}
            type="button"
            className={item.number === number ? "active" : ""}
            onClick={() => setNumber(item.number)}
          >
            {item.symbol} {item.name}
          </button>
        ))}
      </div>
      {data ? (
        <div className="stack">
          <div className="card">
            <p className="label">
              {data.number}. {data.name} {data.symbol}
            </p>
            <p>{data.title}</p>
          </div>
          <Section title="卦辞" text={data.guaci} />
          <Section title="彖辞" text={data.tuanci} prefix="彖曰：" />
          <Section title="大象" text={data.daxiang} prefix="象曰：" />
          {(data.yaoci || []).map((text, index) => (
            <Section
              key={`yao-${index}`}
              title={`${YAO[index] || index}爻`}
              text={text}
              extra={data.xiaoxiang?.[index] ? `象曰：${data.xiaoxiang[index]}` : ""}
            />
          ))}
          {data.yong?.ci ? (
            <Section title="用九 / 用六" text={data.yong.ci} extra={data.yong.xiang ? `象曰：${data.yong.xiang}` : ""} />
          ) : null}
          {(data.wenyan || []).length ? (
            <div className="card">
              <p className="label">文言（只读）</p>
              {(data.wenyan || []).map((line, index) => (
                <p key={index}>{line}</p>
              ))}
            </div>
          ) : null}
        </div>
      ) : (
        <p className="muted">加载中…</p>
      )}
    </div>
  );
}

function Section({
  title,
  text,
  prefix,
  extra,
}: {
  title: string;
  text: string;
  prefix?: string;
  extra?: string;
}) {
  if (!text) return null;
  return (
    <div className="card">
      <p className="label">{title}</p>
      <p className="scripture">
        {prefix || ""}
        {text}
      </p>
      {extra ? <p className="muted">{extra}</p> : null}
    </div>
  );
}
