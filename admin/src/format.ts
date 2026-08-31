export function formatNumber(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return "—";
  return value.toLocaleString("zh-CN");
}

export function formatCompact(value: number): string {
  if (value >= 1_000_000) {
    const millions = value / 1_000_000;
    return `${millions >= 10 ? millions.toFixed(0) : millions.toFixed(1).replace(/\.0$/, "")}M`;
  }
  if (value >= 1000) {
    const thousands = value / 1000;
    return `${thousands >= 10 ? thousands.toFixed(0) : thousands.toFixed(1).replace(/\.0$/, "")}k`;
  }
  return String(Math.round(value));
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
