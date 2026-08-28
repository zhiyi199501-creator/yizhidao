import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { RequireAdmin } from "./RequireAdmin";
import { Shell } from "./layout/Shell";
import { AiPage } from "./pages/AiPage";
import { CaseEditPage } from "./pages/CaseEditPage";
import { CasesPage } from "./pages/CasesPage";
import { EvalPage } from "./pages/EvalPage";
import { ImaPage } from "./pages/ImaPage";
import { JingwenPage } from "./pages/JingwenPage";
import { LoginPage } from "./pages/LoginPage";
import { OverviewPage } from "./pages/OverviewPage";
import { SystemPage } from "./pages/SystemPage";
import { UserDetailPage } from "./pages/UserDetailPage";
import { UsersPageView } from "./pages/UsersPage";

export function App() {
  return (
    <BrowserRouter basename="/admin">
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAdmin />}>
          <Route element={<Shell />}>
            <Route index element={<OverviewPage />} />
            <Route path="users" element={<UsersPageView />} />
            <Route path="users/:userId" element={<UserDetailPage />} />
            <Route path="ai" element={<AiPage />} />
            <Route path="cases" element={<CasesPage />} />
            <Route path="cases/new" element={<CaseEditPage />} />
            <Route path="cases/:id" element={<CaseEditPage />} />
            <Route path="ima" element={<ImaPage />} />
            <Route path="jingwen" element={<JingwenPage />} />
            <Route path="eval" element={<EvalPage />} />
            <Route path="system" element={<SystemPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
