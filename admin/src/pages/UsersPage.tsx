import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError, UsersPage, api } from "../api";
import { LOGIN_LABELS, formatDateTime, formatNumber } from "../format";

export function UsersPageView() {
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);
  const [data, setData] = useState<UsersPage | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const handle = window.setTimeout(() => {
      api.users(q, page).then(setData).catch((err) => {
        setError(err instanceof ApiError ? err.message : "加载失败");
      });
    }, 200);
    return () => window.clearTimeout(handle);
  }, [q, page]);

  return (
    <div className="stack">
      <div className="page-title">
        <h2>用户</h2>
        <div className="meta">只读 · 邮箱与手机已脱敏 · 不展示所问</div>
      </div>
      <div className="search">
        <input
          placeholder="搜索 id / 邮箱 / 手机 / 昵称"
          value={q}
          onChange={(event) => {
            setPage(1);
            setQ(event.target.value);
          }}
        />
      </div>
      {error ? <p className="error">{error}</p> : null}
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>用户 ID</th>
              <th>昵称</th>
              <th>邮箱</th>
              <th>登录</th>
              <th>最近登录</th>
              <th>今日 AI</th>
              <th>累计 AI</th>
              <th>用户购买</th>
            </tr>
          </thead>
          <tbody>
            {(data?.users || []).map((user) => (
              <tr key={user.id} className="clickable" onClick={() => navigate(`/users/${user.id}`)}>
                <td className="mono">{user.id}</td>
                <td>{user.nickname}</td>
                <td>{user.email || "—"}</td>
                <td>
                  {user.loginMethods.map((method) => (
                    <span className="badge" key={method}>
                      {LOGIN_LABELS[method] || method}
                    </span>
                  ))}
                </td>
                <td>{formatDateTime(user.lastLoginAt)}</td>
                <td>{formatNumber(user.aiToday)}</td>
                <td>{formatNumber(user.aiTotal)}</td>
                <td>{user.iapUnlocked ? "解锁问答" : ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!data?.users.length ? <p className="muted">没有匹配的用户</p> : null}
      </div>
      <div className="pager">
        <button className="btn" disabled={page <= 1} onClick={() => setPage((n) => n - 1)}>
          上一页
        </button>
        <span>
          第 {data?.page || page} 页 · 共 {formatNumber(data?.total || 0)} 人
        </span>
        <button
          className="btn"
          disabled={!data || page * data.pageSize >= data.total}
          onClick={() => setPage((n) => n + 1)}
        >
          下一页
        </button>
      </div>
    </div>
  );
}
