export class ApiError extends Error {
  status: number;
  code?: number;

  constructor(message: string, status: number, code?: number) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(init.headers || {}),
    },
    ...init,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(data.message || "请求失败", response.status, data.code);
  }
  return data as T;
}

export type RangeKey = "today" | "7d" | "30d";

export type Cost = { configured: boolean; usd: number | null };

export type UsagePoint = {
  date: string;
  calls: number;
  tokens?: number;
  promptTokens?: number;
  completionTokens?: number;
};

export type Overview = {
  ok: boolean;
  users: {
    total: number;
    today: number;
    last7d: number;
    loginMix: Record<string, number>;
  };
  aiToday: {
    calls: number;
    ok: number;
    successRate: number | null;
    tokens: number;
    promptTokens: number;
    completionTokens: number;
    rateLimited: number;
    failedOther: number;
    analyze: number;
    followup: number;
    cost: Cost;
  };
  aiLast7d: UsagePoint[];
  health: {
    aiMode: string;
    model: string;
    casesVersion: string;
    casesCount: number;
    casesLoaded: boolean;
    hexagramsLoaded: boolean;
    hexagramsCount: number;
    imaLoaded: boolean;
    imaCount: number;
  };
  feedback?: { total: number; unread: number };
};

export type AdminUser = {
  id: string;
  nickname: string;
  email: string | null;
  phone: string | null;
  loginMethods: string[];
  createdAt: string | null;
  lastLoginAt: string | null;
  aiToday: number;
  aiTotal: number;
  iapUnlocked: boolean;
  aiUnlimited?: boolean;
  iapSource?: "none" | "purchase" | "admin" | "android";
  iapCanRevoke?: boolean;
};

export type UsersPage = {
  ok: boolean;
  total: number;
  page: number;
  pageSize: number;
  users: AdminUser[];
};

export type UserDetail = {
  ok: boolean;
  user: AdminUser;
  daily: UsagePoint[];
};

export type AiUsage = {
  ok: boolean;
  range: RangeKey;
  summary: {
    calls: number;
    ok: number;
    successRate: number | null;
    tokens: number;
    promptTokens: number;
    completionTokens: number;
    rateLimited: number;
    failedOther: number;
    analyze: number;
    followup: number;
    cost: Cost;
    latencyMsP50: number | null;
    latencyMsP95: number | null;
  };
  errors: { code: number; count: number }[];
  methods: { method: string; label: string; count: number }[];
  topUsers: { id: string; calls: number }[];
  series: UsagePoint[];
};

export type AiEventsPage = {
  ok: boolean;
  total: number;
  page: number;
  pageSize: number;
  events: {
    id: number;
    createdAt: string | null;
    userId: string;
    nickname: string | null;
    kind: string;
    ok: boolean;
    errorCode: number | null;
    latencyMs: number;
    promptTokens: number;
    completionTokens: number;
    model: string;
    method: string | null;
  }[];
};

export type SystemStatus = {
  ok: boolean;
  health: boolean;
  config: Record<string, string | number | boolean>;
  data: Record<string, Record<string, unknown>>;
  rateLimitNote: string;
};

export type FeedbackItem = {
  id: number;
  createdAt: string | null;
  userId: string | null;
  nickname: string | null;
  email: string | null;
  phone: string | null;
  body: string;
  contact: string;
  platform: string;
  appVersion: string;
  readAt: string | null;
};

export type FeedbackPage = {
  ok: boolean;
  total: number;
  unread: number;
  page: number;
  pageSize: number;
  items: FeedbackItem[];
};

export type ContentCase = {
  id: number;
  file: string;
  hexagram: string;
  position: string;
  background: string;
  question: string;
  casting: string;
  explanation: string;
  verification: string;
  number: number;
  updatedAt: string | null;
};

export type CasesPage = {
  ok: boolean;
  total: number;
  page: number;
  pageSize: number;
  cases: ContentCase[];
};

export type CasesStatus = {
  ok: boolean;
  draftCount: number;
  publishedCount: number;
  publishedVersion: string;
  publishedPath: string;
};

export type HexagramInfo = {
  number: number;
  name: string;
  symbol: string;
  title: string;
};

export type HexagramReading = HexagramInfo & {
  guaci: string;
  tuanci: string;
  daxiang: string;
  yaoci: string[];
  xiaoxiang: string[];
  yong?: { ci?: string; xiang?: string } | null;
  wenyan: string[];
};

export type ImaIndexItem = HexagramInfo & { entryCount: number };

export type ImaEntry = {
  id: string;
  title: string;
  scripture: string;
  answer: string;
};

export type EvalSample = {
  id: string;
  title: string;
  question: string;
  primary: number;
  moving: number[];
};

export type EvalRun = {
  ok: boolean;
  live: boolean;
  aiMode: string;
  pass: boolean;
  note: string;
  followup: {
    message: string;
    reply?: string;
    advice?: string[];
    askNext?: string[];
    error?: string;
  } | null;
  samples: {
    inspect: {
      id: string;
      title: string;
      pass: boolean;
      ima: string[];
      caseCaption: string;
      caseCount: number;
      promptChars: number;
      checks: { imaPresent: boolean; imaAbsent: boolean; cases: boolean };
    };
    analysis?: Record<string, unknown>;
    error?: string;
  }[];
};

async function parseError(response: Response): Promise<ApiError> {
  const data = await response.json().catch(() => ({}));
  return new ApiError(data.message || "请求失败", response.status, data.code);
}

export const api = {
  login: (password: string) => request<{ ok: boolean }>("/v1/admin/login", { method: "POST", body: JSON.stringify({ password }) }),
  logout: () => request<{ ok: boolean }>("/v1/admin/logout", { method: "POST" }),
  me: () => request<{ ok: boolean; role: string }>("/v1/admin/me"),
  overview: () => request<Overview>("/v1/admin/overview"),
  users: (q: string, page = 1) =>
    request<UsersPage>(`/v1/admin/users?q=${encodeURIComponent(q)}&page=${page}&pageSize=20`),
  user: (id: string) => request<UserDetail>(`/v1/admin/users/${encodeURIComponent(id)}`),
  setUserIapUnlock: (id: string, unlocked: boolean) =>
    request<{ ok: boolean; user: AdminUser }>(`/v1/admin/users/${encodeURIComponent(id)}/iap-unlock`, {
      method: "POST",
      body: JSON.stringify({ unlocked }),
    }),
  setUserAiUnlimited: (id: string, unlimited: boolean) =>
    request<{ ok: boolean; user: AdminUser }>(`/v1/admin/users/${encodeURIComponent(id)}/ai-unlimited`, {
      method: "POST",
      body: JSON.stringify({ unlimited }),
    }),
  ai: (range: RangeKey) => request<AiUsage>(`/v1/admin/ai?range=${range}`),
  aiEvents: (range: RangeKey, page = 1) =>
    request<AiEventsPage>(`/v1/admin/ai/events?range=${range}&page=${page}&pageSize=50`),
  system: () => request<SystemStatus>("/v1/admin/system"),
  cases: (q: string, number: number | "", page = 1) => {
    const params = new URLSearchParams({ q, page: String(page), pageSize: "30" });
    if (number) params.set("number", String(number));
    return request<CasesPage>(`/v1/admin/cases?${params}`);
  },
  casesStatus: () => request<CasesStatus>("/v1/admin/cases/status"),
  case: (id: number) => request<{ ok: boolean; case: ContentCase }>(`/v1/admin/cases/${id}`),
  createCase: (body: Omit<ContentCase, "id" | "number" | "updatedAt">) =>
    request<{ ok: boolean; case: ContentCase }>("/v1/admin/cases", { method: "POST", body: JSON.stringify(body) }),
  updateCase: (id: number, body: Omit<ContentCase, "id" | "number" | "updatedAt">) =>
    request<{ ok: boolean; case: ContentCase }>(`/v1/admin/cases/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  deleteCase: (id: number) => request<{ ok: boolean }>(`/v1/admin/cases/${id}`, { method: "DELETE" }),
  publishCases: () => request<{ ok: boolean; version: string; count: number; path: string }>("/v1/admin/cases/publish", { method: "POST" }),
  importCases: async (file: File) => {
    const data = new FormData();
    data.append("file", file);
    const response = await fetch("/v1/admin/cases/import", { method: "POST", credentials: "include", body: data });
    if (!response.ok) throw await parseError(response);
    return response.json() as Promise<{ ok: boolean; total: number; problems: string[] }>;
  },
  exportCases: async () => {
    const response = await fetch("/v1/admin/cases/export", { credentials: "include" });
    if (!response.ok) throw await parseError(response);
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "cases.json";
    link.click();
    URL.revokeObjectURL(url);
  },
  hexagrams: () => request<{ ok: boolean; hexagrams: HexagramInfo[] }>("/v1/admin/hexagrams"),
  hexagram: (number: number) => request<{ ok: boolean; hexagram: HexagramReading }>(`/v1/admin/hexagrams/${number}`),
  imaIndex: () => request<{ ok: boolean; hexagrams: ImaIndexItem[] }>("/v1/admin/ima"),
  imaHexagram: (number: number) => request<{ ok: boolean; number: number; entries: ImaEntry[] }>(`/v1/admin/ima/${number}`),
  saveImaAnswer: (id: string, answer: string) =>
    request<{ ok: boolean; entry: ImaEntry; note: string }>(`/v1/admin/ima/entries/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify({ answer }),
    }),
  evalSamples: () => request<{ ok: boolean; samples: EvalSample[] }>("/v1/admin/eval/samples"),
  evalRun: (ids: string[] | null, live: boolean) =>
    request<EvalRun>("/v1/admin/eval/run", { method: "POST", body: JSON.stringify({ ids, live }) }),
  feedback: (q: string, unreadOnly: boolean, page = 1) => {
    const params = new URLSearchParams({
      q,
      unreadOnly: unreadOnly ? "true" : "false",
      page: String(page),
      pageSize: "20",
    });
    return request<FeedbackPage>(`/v1/admin/feedback?${params}`);
  },
  setFeedbackRead: (id: number, read: boolean) =>
    request<{ ok: boolean; item: FeedbackItem }>(`/v1/admin/feedback/${id}`, {
      method: "PATCH",
      body: JSON.stringify({ read }),
    }),
};
