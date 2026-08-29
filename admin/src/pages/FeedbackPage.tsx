import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError, FeedbackItem, FeedbackPage as FeedbackList, api } from "../api";
import { formatDateTime, formatNumber } from "../format";

const PLATFORM_LABELS: Record<string, string> = {
  ios: "iOS",
  android: "Android",
};

export function FeedbackPage() {
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(1);
  const [data, setData] = useState<FeedbackList | null>(null);
  const [selected, setSelected] = useState<FeedbackItem | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      api
        .feedback(q, unreadOnly, page)
        .then((next) => {
          setData(next);
          setError("");
        })
        .catch((err) => {
          setError(err instanceof ApiError ? err.message : "加载失败");
        });
    }, 200);
    return () => window.clearTimeout(handle);
  }, [q, unreadOnly, page]);

  async function toggleRead(item: FeedbackItem) {
    setBusy(true);
    try {
      const next = await api.setFeedbackRead(item.id, !item.readAt);
      setSelected(next.item);
      const list = await api.feedback(q, unreadOnly, page);
      setData(list);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "更新失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <div className="page-title">
        <h2>意见反馈</h2>
        <div className="meta">
          未读 {formatNumber(data?.unread || 0)} · 共 {formatNumber(data?.total || 0)} 条
        </div>
      </div>
      <div className="search">
        <input
          placeholder="搜索正文 / 联系方式 / 用户"
          value={q}
          onChange={(event) => {
            setPage(1);
            setQ(event.target.value);
          }}
        />
        <div className="tabs">
          <button
            type="button"
            className={!unreadOnly ? "active" : ""}
            onClick={() => {
              setPage(1);
              setUnreadOnly(false);
            }}
          >
            全部
          </button>
          <button
            type="button"
            className={unreadOnly ? "active" : ""}
            onClick={() => {
              setPage(1);
              setUnreadOnly(true);
            }}
          >
            未读
          </button>
        </div>
      </div>
      {error ? <p className="error">{error}</p> : null}
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>来源</th>
              <th>用户</th>
              <th>摘要</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {(data?.items || []).map((item) => (
              <tr
                key={item.id}
                className={`clickable${selected?.id === item.id ? " selected" : ""}`}
                onClick={() => setSelected(item)}
              >
                <td>{formatDateTime(item.createdAt)}</td>
                <td>
                  {PLATFORM_LABELS[item.platform] || "—"}
                  {item.appVersion ? <div className="muted">{item.appVersion}</div> : null}
                </td>
                <td>
                  {item.nickname || "未登录"}
                  {item.userId ? (
                    <div className="muted mono">
                      <button
                        type="button"
                        className="linkish"
                        onClick={(event) => {
                          event.stopPropagation();
                          navigate(`/users/${item.userId}`);
                        }}
                      >
                        {item.userId}
                      </button>
                    </div>
                  ) : null}
                  {item.contact ? <div className="muted">{item.contact}</div> : null}
                  {item.email || item.phone ? (
                    <div className="muted">{item.email || item.phone}</div>
                  ) : null}
                </td>
                <td className="clip">{item.body.length > 48 ? `${item.body.slice(0, 48)}…` : item.body}</td>
                <td>{item.readAt ? "已读" : <span className="warn">未读</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!data?.items.length ? <p className="muted">没有匹配的反馈</p> : null}
      </div>
      <div className="pager">
        <button className="btn" disabled={page <= 1} onClick={() => setPage((n) => n - 1)}>
          上一页
        </button>
        <span>
          第 {data?.page || page} 页 · 共 {formatNumber(data?.total || 0)} 条
        </span>
        <button
          className="btn"
          disabled={!data || page * data.pageSize >= data.total}
          onClick={() => setPage((n) => n + 1)}
        >
          下一页
        </button>
      </div>
      {selected ? (
        <div className="card">
          <div className="page-title" style={{ marginBottom: 12 }}>
            <h3 style={{ margin: 0, fontSize: 18 }}>反馈 #{selected.id}</h3>
            <button className="btn secondary" disabled={busy} onClick={() => toggleRead(selected)}>
              {selected.readAt ? "标为未读" : "标为已读"}
            </button>
          </div>
          <dl className="kv">
            <dt>时间</dt>
            <dd>{formatDateTime(selected.createdAt)}</dd>
            <dt>来源</dt>
            <dd>
              {PLATFORM_LABELS[selected.platform] || "未知"}
              {selected.appVersion ? ` · ${selected.appVersion}` : ""}
            </dd>
            <dt>用户</dt>
            <dd>
              {selected.nickname || "未登录"}
              {selected.userId ? ` · ${selected.userId}` : ""}
            </dd>
            <dt>联系方式</dt>
            <dd>{selected.contact || selected.email || selected.phone || "—"}</dd>
          </dl>
          <p className="prewrap" style={{ marginTop: 16 }}>
            {selected.body}
          </p>
        </div>
      ) : null}
    </div>
  );
}
