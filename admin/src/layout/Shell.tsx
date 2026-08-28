import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { api } from "../api";

const LINKS = [
  { to: "/", label: "总览", end: true },
  { to: "/users", label: "用户" },
  { to: "/ai", label: "AI 用量" },
  { to: "/cases", label: "案例" },
  { to: "/ima", label: "黄庭" },
  { to: "/jingwen", label: "经文" },
  { to: "/eval", label: "抽检" },
  { to: "/system", label: "系统" },
];

export function Shell() {
  const navigate = useNavigate();

  async function logout() {
    try {
      await api.logout();
    } finally {
      navigate("/login", { replace: true });
    }
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <p className="eyebrow">ADMIN</p>
          <h1>易玩家</h1>
        </div>
        <nav className="nav">
          {LINKS.map((link) => (
            <NavLink key={link.to} to={link.to} end={link.end} className={({ isActive }) => (isActive ? "active" : "")}>
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="spacer" />
        <button type="button" onClick={logout}>
          退出
        </button>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
