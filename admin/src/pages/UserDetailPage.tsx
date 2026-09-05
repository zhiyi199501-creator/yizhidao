import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError, UserDetail, api } from "../api";
import { LOGIN_LABELS, formatDateTime, formatNumber } from "../format";
import { Stat, TrendChart } from "../ui";

function unlockLabel(source: string | undefined, unlocked: boolean): string {
  if (!unlocked) return "未解锁";
  if (source === "purchase") return "付费解锁";
  if (source === "android") return "安卓赠送";
  if (source === "admin") return "手动解锁";
  return "已解锁";
}

export function UserDetailPage() {
  const { userId = "" } = useParams();
  const [data, setData] = useState<UserDetail | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");

  useEffect(() => {
    api.user(userId).then(setData).catch((err) => {
      setError(err instanceof ApiError ? err.message : "加载失败");
    });
  }, [userId]);

  async function setUnlock(unlocked: boolean) {
    if (!data) return;
    setBusy(true);
    setActionError("");
    try {
      const resp = await api.setUserIapUnlock(data.user.id, unlocked);
      setData({ ...data, user: { ...data.user, ...resp.user } });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "操作失败");
    } finally {
      setBusy(false);
    }
  }

  async function setUnlimited(unlimited: boolean) {
    if (!data) return;
    setBusy(true);
    setActionError("");
    try {
      const resp = await api.setUserAiUnlimited(data.user.id, unlimited);
      setData({ ...data, user: { ...data.user, ...resp.user } });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "操作失败");
    } finally {
      setBusy(false);
    }
  }

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
          <dt>问答解锁</dt>
          <dd>
            <div className="row-actions">
              <span>{unlockLabel(user.iapSource, user.iapUnlocked)}</span>
              {!user.iapUnlocked ? (
                <button type="button" className="btn" disabled={busy} onClick={() => setUnlock(true)}>
                  手动解锁
                </button>
              ) : user.iapCanRevoke ? (
                <button type="button" className="btn secondary" disabled={busy} onClick={() => setUnlock(false)}>
                  取消解锁
                </button>
              ) : (
                <span className="muted">付费用户不可取消</span>
              )}
            </div>
          </dd>
          <dt>日额度</dt>
          <dd>
            <div className="row-actions">
              <span>{user.aiUnlimited ? "不限次" : "按档位（未购 3 / 解锁 30）"}</span>
              {user.aiUnlimited ? (
                <button type="button" className="btn secondary" disabled={busy} onClick={() => setUnlimited(false)}>
                  取消不限次
                </button>
              ) : (
                <button type="button" className="btn" disabled={busy} onClick={() => setUnlimited(true)}>
                  设为不限次
                </button>
              )}
            </div>
            <p className="muted" style={{ marginTop: 8 }}>
              自用／抽检。开启时顺带解锁问答；仍受间隔与并发限制。
            </p>
            {actionError ? <p className="error" style={{ marginTop: 8 }}>{actionError}</p> : null}
          </dd>
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
