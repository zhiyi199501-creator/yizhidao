import { useEffect, useState } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { ApiError, api } from "./api";

export function RequireAdmin() {
  const [state, setState] = useState<"loading" | "ok" | "no">("loading");

  useEffect(() => {
    api
      .me()
      .then(() => setState("ok"))
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) setState("no");
        else setState("no");
      });
  }, []);

  if (state === "loading") return <p className="muted" style={{ padding: 32 }}>核对登录态…</p>;
  if (state === "no") return <Navigate to="/login" replace />;
  return <Outlet />;
}
