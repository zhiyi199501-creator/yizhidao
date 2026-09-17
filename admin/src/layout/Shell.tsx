import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";

const GROUPS = [
  {
    label: "运营",
    items: [
      { to: "/", label: "总览", end: true },
      { to: "/users", label: "用户" },
      { to: "/ai", label: "AI 用量" },
      { to: "/feedback", label: "反馈" },
    ],
  },
  {
    label: "内容",
    items: [
      { to: "/cases", label: "案例" },
      { to: "/ima", label: "黄庭" },
      { to: "/jingwen", label: "经文" },
      { to: "/eval", label: "抽检" },
    ],
  },
  {
    label: "设置",
    items: [{ to: "/system", label: "系统" }],
  },
];

function groupIsActive(pathname: string, items: { to: string; end?: boolean }[]) {
  return items.some((item) => {
    if (item.end) return pathname === item.to;
    if (item.to === "/") return pathname === "/";
    return pathname === item.to || pathname.startsWith(`${item.to}/`);
  });
}

export function Shell() {
  const navigate = useNavigate();
  const { pathname } = useLocation();

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
          {GROUPS.map((group) => (
            <div
              key={group.label}
              className={`nav-group${groupIsActive(pathname, group.items) ? " is-current" : ""}`}
            >
              <p className="nav-group-label">{group.label}</p>
              <div className="nav-group-items">
                {group.items.map((link) => (
                  <NavLink
                    key={link.to}
                    to={link.to}
                    end={link.end}
                    className={({ isActive }) => (isActive ? "active" : "")}
                  >
                    {link.label}
                  </NavLink>
                ))}
              </div>
            </div>
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
