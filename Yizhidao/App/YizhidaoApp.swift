import SwiftUI
import SwiftData

@main
struct YizhidaoApp: App {
    var sharedModelContainer: ModelContainer = {
        let schema = Schema([ReadingRecord.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        do {
            return try ModelContainer(for: schema, configurations: [config])
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
    }()

    init() {
        _ = HexagramStore.shared
    }

    var body: some Scene {
        WindowGroup {
            RootTabView()
        }
        .modelContainer(sharedModelContainer)
    }
}

struct RootTabView: View {
    @State private var appNavigation = AppNavigation()

    var body: some View {
        @Bindable var appNavigation = appNavigation
        TabView(selection: $appNavigation.selectedTab) {
            CastingHomeView()
                .tabItem {
                    Label("起卦", systemImage: "sparkles")
                }
                .tag(AppTab.cast)
            HistoryListView()
                .tabItem {
                    Label("历史", systemImage: "clock")
                }
                .tag(AppTab.history)
            CaseListView()
                .tabItem {
                    Label("案例", systemImage: "books.vertical")
                }
                .tag(AppTab.cases)
            MyMenuView()
                .tabItem {
                    Label("我的", systemImage: "person.crop.circle")
                }
                .tag(AppTab.me)
        }
        .tint(AppTheme.accent)
        .preferredColorScheme(.light)
        .environment(\.locale, Locale(identifier: "zh_CN"))
        .environment(appNavigation)
        .animation(nil, value: appNavigation.selectedTab)
    }
}

struct MyMenuView: View {
    @State private var recycleEntries: [HistoryTrashEntry] = HistoryTrashStore.load()
    @State private var session: LocalUserSession = LocalAuthStore.load()
    @State private var showLoginSheet = false
    @State private var showLogoutConfirm = false
    @State private var openAIAnalysisPage = false
    @State private var pendingOpenAIAnalysis = false

    var body: some View {
        NavigationStack {
            List {
                Section("用户信息") {
                    if session.isLoggedIn {
                        NavigationLink {
                            ProfileEditView(session: $session)
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: session.avatarSymbol)
                                    .font(.title2)
                                    .foregroundStyle(AppTheme.accent)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(session.displayName)
                                        .foregroundStyle(.primary)
                                }
                                Spacer()
                            }
                        }
                        Button("退出登录") {
                            showLogoutConfirm = true
                        }
                        .font(.caption)
                    } else {
                        HStack(spacing: 10) {
                            Image(systemName: "person.crop.circle.badge.exclamationmark")
                                .foregroundStyle(.secondary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("未登录")
                                Text("支持手机号或微信登录")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button("登录") {
                                showLoginSheet = true
                            }
                        }
                    }
                }

                Section("回收站") {
                    NavigationLink {
                        RecycleBinView()
                    } label: {
                        HStack {
                            Label("回收站", systemImage: "trash")
                            Spacer()
                            Text("\(recycleEntries.count)")
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Section("AI") {
                    Button {
                        if session.isLoggedIn {
                            openAIAnalysisPage = true
                        } else {
                            pendingOpenAIAnalysis = true
                            showLoginSheet = true
                        }
                    } label: {
                        HStack {
                            AIBadgeIcon()
                            Spacer()
                            if !session.isLoggedIn {
                                Text("需登录")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("我的")
            .navigationBarTitleDisplayMode(.inline)
            .parchmentBackground()
            .navigationDestination(isPresented: $openAIAnalysisPage) {
                AIAnalysisHistoryView()
            }
            .onAppear {
                recycleEntries = HistoryTrashStore.load()
                session = LocalAuthStore.load()
            }
            .sheet(isPresented: $showLoginSheet) {
                LoginSheetView { newSession in
                    session = newSession
                    LocalAuthStore.save(newSession)
                    showLoginSheet = false
                    if pendingOpenAIAnalysis {
                        pendingOpenAIAnalysis = false
                        openAIAnalysisPage = true
                    }
                }
            }
            .alert("确认退出登录？", isPresented: $showLogoutConfirm) {
                Button("取消", role: .cancel) {}
                Button("退出登录", role: .destructive) {
                    session = .guest
                    LocalAuthStore.save(session)
                }
            }
        }
    }
}

struct LocalUserSession: Codable {
    var isLoggedIn: Bool
    var displayName: String
    var phone: String?
    var avatarSymbol: String
    var accessToken: String?

    static let guest = LocalUserSession(
        isLoggedIn: false,
        displayName: "游客",
        phone: nil,
        avatarSymbol: "person.crop.circle.fill",
        accessToken: nil
    )
}

enum LocalAuthStore {
    private static let key = "auth.local.session.v1"

    static func load() -> LocalUserSession {
        guard let data = UserDefaults.standard.data(forKey: key),
              let session = try? JSONDecoder().decode(LocalUserSession.self, from: data)
        else { return .guest }
        return session
    }

    static func save(_ session: LocalUserSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
}

struct LoginSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var phone = ""
    @State private var code = ""
    @State private var showWechatTip = false
    @State private var agreed = false
    @State private var errorMessage: String?
    @State private var isSendingCode = false
    @State private var isLoggingIn = false
    @State private var cooldownSec = 0
    let onSuccess: (LocalUserSession) -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Button {
                    guard agreed else {
                        errorMessage = "请先勾选并同意用户协议与隐私政策"
                        return
                    }
                    showWechatTip = true
                } label: {
                    Label("微信登录（待接入）", systemImage: "message.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.accent)
                .alert("暂未接入", isPresented: $showWechatTip) {
                    Button("知道了", role: .cancel) {}
                } message: {
                    Text("当前为本地演示版，后续接入真实微信登录。")
                }

                HStack {
                    Rectangle().fill(Color.black.opacity(0.1)).frame(height: 1)
                    Text("或使用手机号")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Rectangle().fill(Color.black.opacity(0.1)).frame(height: 1)
                }

                TextField("手机号", text: $phone)
                    .keyboardType(.numberPad)
                    .appTextFieldStyle()
                HStack(spacing: 8) {
                    TextField("验证码", text: $code)
                        .keyboardType(.numberPad)
                        .appTextFieldStyle()
                    Button(cooldownSec > 0 ? "\(cooldownSec)s" : "发送验证码") {
                        Task { await sendCode() }
                    }
                    .buttonStyle(.bordered)
                    .disabled(isSendingCode || cooldownSec > 0 || phone.trimmingCharacters(in: .whitespacesAndNewlines).count < 6)
                }
                Button("手机号登录") {
                    guard agreed else {
                        errorMessage = "请先勾选并同意用户协议与隐私政策"
                        return
                    }
                    Task { await loginByPhone() }
                }
                .buttonStyle(.borderedProminent)
                .tint(AppTheme.accent)
                .disabled(isLoggingIn || phone.count < 6 || code.isEmpty)

                Toggle(isOn: $agreed) {
                    Text("已阅读并同意《用户协议》《隐私政策》")
                        .font(.caption)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()
            }
            .padding()
            .navigationTitle("登录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                }
            }
            .parchmentBackground()
        }
    }

    private func sendCode() async {
        guard agreed else {
            errorMessage = "请先勾选并同意用户协议与隐私政策"
            return
        }
        let trimmedPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmedPhone.count >= 6 else {
            errorMessage = "请输入正确手机号"
            return
        }
        isSendingCode = true
        defer { isSendingCode = false }
        do {
            let resp = try await AuthAPI.sendSMSCode(phone: trimmedPhone)
            cooldownSec = max(resp.cooldownSec, 0)
            errorMessage = "验证码已发送"
            startCooldown()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func loginByPhone() async {
        let trimmedPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedCode = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPhone.isEmpty, !trimmedCode.isEmpty else {
            errorMessage = "请输入手机号和验证码"
            return
        }
        isLoggingIn = true
        defer { isLoggingIn = false }
        do {
            let resp = try await AuthAPI.loginBySMS(phone: trimmedPhone, code: trimmedCode)
            onSuccess(
                LocalUserSession(
                    isLoggedIn: true,
                    displayName: resp.user.nickname,
                    phone: resp.user.phone ?? trimmedPhone,
                    avatarSymbol: "person.crop.circle.fill",
                    accessToken: resp.accessToken
                )
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func startCooldown() {
        guard cooldownSec > 0 else { return }
        Task {
            while cooldownSec > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                await MainActor.run {
                    cooldownSec -= 1
                }
            }
        }
    }
}

enum AuthAPI {
    #if DEBUG
    #if targetEnvironment(simulator)
    private static let baseURL = URL(string: "http://127.0.0.1:8080")!
    #else
    /// 真机联调：Mac 局域网 IP；变更时在 Mac 终端执行 `ipconfig getifaddr en0`
    private static let baseURL = URL(string: "http://172.20.10.10:8080")!
    #endif
    #else
    private static let baseURL = URL(string: "https://api.yizhidao.app")!
    #endif

    struct SMSCodeResponse: Decodable {
        let ok: Bool
        let cooldownSec: Int
    }

    struct SMSLoginResponse: Decodable {
        struct User: Decodable {
            let id: String
            let nickname: String
            let phone: String?
        }
        let ok: Bool
        let accessToken: String
        let user: User
    }

    private struct ErrorEnvelope: Decodable {
        let message: String?
        let code: Int?
    }

    static func sendSMSCode(phone: String) async throws -> SMSCodeResponse {
        var req = URLRequest(url: baseURL.appendingPathComponent("v1/auth/sms/send"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["phone": phone])
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "发送验证码失败") }
        let decoded = try JSONDecoder().decode(SMSCodeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("发送验证码失败") }
        return decoded
    }

    static func loginBySMS(phone: String, code: String) async throws -> SMSLoginResponse {
        var req = URLRequest(url: baseURL.appendingPathComponent("v1/auth/sms/login"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["phone": phone, "code": code])
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "登录失败") }
        let decoded = try JSONDecoder().decode(SMSLoginResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("登录失败") }
        return decoded
    }

    struct AIAnalyzeResponse: Decodable {
        struct Analysis: Decodable {
            let summary: String
            let focus: String
            let advice: [String]
        }

        let ok: Bool
        let analysis: Analysis
    }

    static func analyzeReading(result: CastResult, accessToken: String) async throws -> AIAnalyzeResponse {
        var payload: [String: Any] = [
            "method": result.method.rawValue,
            "primaryNumber": result.primaryNumber,
            "movingPositions": result.movingPositions,
            "lines": result.lines.map(\.rawValue),
            "hexTextVersion": "yi-zhengshi-2026-08",
        ]
        if let question = result.question {
            payload["question"] = question
        }
        if let resultingNumber = result.resultingNumber {
            payload["resultingNumber"] = resultingNumber
        }

        var req = URLRequest(url: baseURL.appendingPathComponent("v1/ai/analyze"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "解读失败") }
        let decoded = try JSONDecoder().decode(AIAnalyzeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("解读失败") }
        return decoded
    }

    private static func decodeError(_ data: Data, fallback: String) -> LoginError {
        if let envelope = try? JSONDecoder().decode(ErrorEnvelope.self, from: data),
           let message = envelope.message, !message.isEmpty {
            return .network(message)
        }
        return .network(fallback)
    }
}

enum LoginError: LocalizedError {
    case network(String)

    var errorDescription: String? {
        switch self {
        case .network(let message): return message
        }
    }
}

private struct AIAnalysisHistoryView: View {
    @Query(sort: \ReadingRecord.createdAt, order: .reverse) private var records: [ReadingRecord]
    private let store = HexagramStore.shared

    var body: some View {
        Group {
            if records.isEmpty {
                ContentUnavailableView(
                    "暂无占卦记录",
                    systemImage: "sparkles",
                    description: Text("起卦后可在卦象结果页使用 AI 解读")
                )
            } else {
                List {
                    ForEach(records.prefix(30)) { record in
                        NavigationLink {
                            AIAnalysisView(result: record.toCastResult())
                        } label: {
                            aiHistoryRow(record)
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("AI 解读")
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }

    @ViewBuilder
    private func aiHistoryRow(_ record: ReadingRecord) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if let hex = store.hexagram(number: record.primaryNumber) {
                Text("\(hex.symbol) \(hex.name)")
                    .font(.headline)
            } else {
                Text("第\(record.primaryNumber)卦")
                    .font(.headline)
            }
            Text(ReadingRecordRow.timeString(record.createdAt))
                .font(.caption)
                .foregroundStyle(.secondary)
            if let question = record.question, !question.isEmpty {
                Text(question)
                    .font(.subheadline)
                    .lineLimit(1)
            }
        }
    }
}

private struct ProfileEditView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var session: LocalUserSession
    @State private var nicknameDraft: String
    @State private var avatarDraft: String
    @State private var showValidationAlert = false
    @State private var validationMessage = ""

    private static let avatarOptions: [String] = [
        "person.crop.circle.fill",
        "person.fill",
        "moon.stars.fill",
        "sun.max.fill",
        "sparkles",
        "leaf.fill",
        "flame.fill",
        "star.fill"
    ]

    init(session: Binding<LocalUserSession>) {
        _session = session
        _nicknameDraft = State(initialValue: session.wrappedValue.displayName)
        _avatarDraft = State(initialValue: session.wrappedValue.avatarSymbol)
    }

    var body: some View {
        List {
            Section("头像") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(Self.avatarOptions, id: \.self) { symbol in
                            Button {
                                avatarDraft = symbol
                            } label: {
                                Image(systemName: symbol)
                                    .font(.title2)
                                    .foregroundStyle(avatarDraft == symbol ? .white : AppTheme.accent)
                                    .frame(width: 44, height: 44)
                                    .background(
                                        Circle().fill(
                                            avatarDraft == symbol
                                            ? AppTheme.accent
                                            : Color.black.opacity(0.06)
                                        )
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }

            Section("昵称") {
                TextField("输入昵称", text: $nicknameDraft)
                    .appTextFieldStyle()
            }
        }
        .navigationTitle("编辑资料")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("保存") {
                    let trimmed = nicknameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                    let limited = String(trimmed.prefix(20))
                    if !(2...20).contains(limited.count) {
                        validationMessage = "昵称需为 2-20 个字符"
                        showValidationAlert = true
                        return
                    }
                    session.displayName = limited
                    session.avatarSymbol = avatarDraft
                    LocalAuthStore.save(session)
                    dismiss()
                }
            }
        }
        .parchmentBackground()
        .alert("保存失败", isPresented: $showValidationAlert) {
            Button("知道了", role: .cancel) {}
        } message: {
            Text(validationMessage)
        }
    }
}

private struct RecycleBinView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var entries: [HistoryTrashEntry] = HistoryTrashStore.load()
    private let store = HexagramStore.shared

    var body: some View {
        Group {
            if entries.isEmpty {
                ContentUnavailableView(
                    "回收站为空",
                    systemImage: "trash",
                    description: Text("删除的记录会先放在这里，可恢复")
                )
            } else {
                List {
                    ForEach(entries.sorted(by: { $0.deletedAt > $1.deletedAt })) { entry in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                if let hex = store.hexagram(number: entry.primaryNumber) {
                                    Text("\(hex.symbol) \(hex.name)")
                                        .font(.headline)
                                } else {
                                    Text("第\(entry.primaryNumber)卦")
                                        .font(.headline)
                                }
                                if let resulting = entry.resultingNumber {
                                    Text("→")
                                        .foregroundStyle(.secondary)
                                    if let hex = store.hexagram(number: resulting) {
                                        Text("\(hex.symbol) \(hex.name)")
                                            .font(.headline)
                                    } else {
                                        Text("第\(resulting)卦")
                                            .font(.headline)
                                    }
                                }
                            }
                            .lineLimit(1)
                            Text(ReadingRecordRow.timeString(entry.createdAt))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if let question = entry.question, !question.isEmpty {
                                Text(question)
                                    .font(.subheadline)
                                    .lineLimit(1)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button("恢复") {
                                modelContext.insert(entry.toReadingRecord())
                                try? modelContext.save()
                                HistoryTrashStore.remove(entryID: entry.id)
                                entries = HistoryTrashStore.load()
                            }
                            .tint(.green)

                            Button("彻底删除", role: .destructive) {
                                HistoryTrashStore.remove(entryID: entry.id)
                                entries = HistoryTrashStore.load()
                            }
                            .tint(.red)
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("回收站")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !entries.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("清空回收站") {
                        HistoryTrashStore.clearAll()
                        entries = []
                    }
                    .tint(.red)
                }
            }
        }
        .parchmentBackground()
        .onAppear {
            entries = HistoryTrashStore.load()
        }
    }
}
