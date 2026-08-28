export function formatNumber(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return "—";
  return value.toLocaleString("zh-CN");
}

export function formatPercent(value: number | null | undefined): string {
  if (value == null) return "—";
  return `${(value * 100).toFixed(1)}%`;
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  return new Date(value).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" });
}

export function formatDay(value: string): string {
  return value.slice(5);
}

export function formatCost(configured: boolean, usd: number | null | undefined): string {
  if (!configured) return "未配置单价";
  if (usd == null) return "—";
  return `≈ $${usd.toFixed(4)}`;
}

export const LOGIN_LABELS: Record<string, string> = {
  apple: "Apple",
  google: "Google",
  email: "邮箱",
  phone: "手机",
  unknown: "未知",
};

export const KIND_LABELS: Record<string, string> = {
  analyze: "解读",
  followup: "追问",
};
