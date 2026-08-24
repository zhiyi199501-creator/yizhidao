import SwiftUI
import SwiftData
import WebKit
import GoogleSignIn

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
        UserDefaults.standard.set(false, forKey: "NSURLSessionHTTP3Enabled")
        _ = HexagramStore.shared
        _ = ImaExplanationStore.shared
        TapSoundPlayer.shared.prepare()
        OAuthSignIn.configureGoogleIfNeeded()
    }

    var body: some Scene {
        WindowGroup {
            RootTabView()
                .onOpenURL { url in
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
        }
        .modelContainer(sharedModelContainer)
    }
}

struct RootTabView: View {
    @Environment(\.locale) private var systemLocale
    @State private var appNavigation = AppNavigation()

    var body: some View {
        @Bindable var appNavigation = appNavigation
        let language = AppLanguage.from(systemLocale)
        TabView(selection: $appNavigation.selectedTab) {
            CastingHomeView()
                .id(language)
                .tabItem {
                    Label("起卦".zh, systemImage: "sparkles")
                }
                .tag(AppTab.cast)
            HistoryListView()
                .id(language)
                .tabItem {
                    Label("历史".zh, systemImage: "clock")
                }
                .tag(AppTab.history)
            CaseListView()
                .id(language)
                .tabItem {
                    Label("案例".zh, systemImage: "books.vertical")
                }
                .tag(AppTab.cases)
            MyMenuView()
                .id(language)
                .tabItem {
                    Label("我的".zh, systemImage: "person.crop.circle")
                }
                .tag(AppTab.me)
        }
        .tint(AppTheme.accent)
        .preferredColorScheme(.light)
        .environment(\.locale, language.locale)
        .environment(appNavigation)
        .animation(nil, value: appNavigation.selectedTab)
        .dismissKeyboardOnBlankTap()
    }
}

struct MyMenuView: View {
    @State private var session: LocalUserSession = LocalAuthStore.load()
    @State private var showLoginSheet = false
    @State private var openAIAnalysisPage = false
    @State private var pendingOpenAIAnalysis = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if session.isLoggedIn {
                        NavigationLink {
                            ProfileEditView(session: $session)
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: session.avatarSymbol)
                                    .font(.title2)
                                    .foregroundStyle(AppTheme.accent)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(session.displayName.zh)
                                        .foregroundStyle(.primary)
                                }
                                Spacer()
                            }
                        }
                    } else {
                        HStack(spacing: 10) {
                            Image(systemName: "person.crop.circle.badge.exclamationmark")
                                .foregroundStyle(.secondary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("未登录".zh)
                                Text("支持 Apple / Google / 邮箱登录".zh)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button("登录".zh) {
                                showLoginSheet = true
                            }
                        }
                    }
                }

                Section {
                    Button {
                        if session.isLoggedIn {
                            openAIAnalysisPage = true
                        } else {
                            pendingOpenAIAnalysis = true
                            showLoginSheet = true
                        }
                    } label: {
                        HStack {
                            Label("保存的AI解读".zh, systemImage: "text.book.closed")
                            Spacer()
                            if !session.isLoggedIn {
                                Text("需登录".zh)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                Section {
                    NavigationLink {
                        YijingIntroListView()
                    } label: {
                        Label("易经基础入门".zh, systemImage: "text.book.closed")
                    }
                    NavigationLink {
                        ClassicHexagramListView()
                    } label: {
                        Label("易经六十四卦".zh, systemImage: "book")
                    }
                    NavigationLink {
                        ClassicWingListView()
                    } label: {
                        Label("易经四传".zh, systemImage: "scroll")
                    }
                }

                Section {
                    NavigationLink {
                        SettingsView(session: $session)
                    } label: {
                        Label("设置".zh, systemImage: "gearshape")
                    }
                }
            }
            .navigationTitle("我的".zh)
            .navigationBarTitleDisplayMode(.inline)
            .parchmentBackground()
            .navigationDestination(isPresented: $openAIAnalysisPage) {
                AIAnalysisHistoryView()
            }
            .onAppear {
                session = LocalAuthStore.load()
                Task { await refreshSessionIfNeeded() }
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
        }
    }

    @MainActor
    private func refreshSessionIfNeeded() async {
        guard session.isLoggedIn, let token = session.accessToken, !token.isEmpty else { return }
        do {
            let me = try await AuthAPI.fetchMe(accessToken: token)
            session.displayName = me.user.nickname
            session.phone = me.user.phone
            session.email = me.user.email
            session.isLoggedIn = true
            LocalAuthStore.save(session)
        } catch LoginError.unauthorized {
            session = .guest
            LocalAuthStore.save(session)
        } catch {
            // 网络异常时保留本地会话，下次再校验
        }
    }
}

struct LocalUserSession: Codable {
    var isLoggedIn: Bool
    var displayName: String
    var phone: String?
    var email: String?
    var avatarSymbol: String
    var accessToken: String?

    static let guest = LocalUserSession(
        isLoggedIn: false,
        displayName: "游客",
        phone: nil,
        email: nil,
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
    @State private var email = ""
    @State private var code = ""
    #if DEBUG
    @State private var phone = ""
    #endif
    @State private var agreed = false
    @State private var showLegal: LegalDocKind?
    @State private var errorMessage: String?
    @State private var isSendingCode = false
    @State private var isLoggingIn = false
    @State private var cooldownSec = 0
    let onSuccess: (LocalUserSession) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Button("取消".zh) { dismiss() }
                Spacer()
                Text("登录".zh)
                    .font(.headline)
                Spacer()
                Color.clear.frame(width: 44, height: 1)
            }

            AppleSignInButton(
                onSuccess: { token, fullName in
                    guard agreed else {
                        errorMessage = "请先勾选并同意用户协议与隐私政策"
                        return
                    }
                    Task { await loginWithApple(identityToken: token, fullName: fullName) }
                },
                onError: { error in
                    errorMessage = LoginError.describe(error)
                }
            )

            Button {
                guard agreed else {
                    errorMessage = "请先勾选并同意用户协议与隐私政策"
                    return
                }
                Task { await loginWithGoogle() }
            } label: {
                Label("Google 登录".zh, systemImage: "globe")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(AppTheme.accent)
            .disabled(isLoggingIn || !AuthConfig.isGoogleConfigured)

            if !AuthConfig.isGoogleConfigured {
                Text("Google 登录需在 AuthConfig.swift 配置 Client ID".zh)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }

            HStack {
                Rectangle().fill(Color.black.opacity(0.1)).frame(height: 1)
                Text("或使用邮箱".zh)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Rectangle().fill(Color.black.opacity(0.1)).frame(height: 1)
            }

            TextField("邮箱".zh, text: $email)
                .textInputAutocapitalization(.never)
                .keyboardType(.emailAddress)
                .autocorrectionDisabled()
                .loginFieldChrome()
            HStack(alignment: .center, spacing: 8) {
                LoginNumberField(text: $code, placeholder: "验证码")
                    .loginFieldChrome()
                Button(cooldownSec > 0 ? "\(cooldownSec)s" : "发送验证码".zh) {
                    Task { await sendEmailCode() }
                }
                .buttonStyle(.bordered)
                .disabled(isSendingCode || cooldownSec > 0 || !isValidEmail(email))
            }
            Button("邮箱登录".zh) {
                guard agreed else {
                    errorMessage = "请先勾选并同意用户协议与隐私政策"
                    return
                }
                Task { await loginByEmail() }
            }
            .buttonStyle(.borderedProminent)
            .tint(AppTheme.accent)
            .disabled(isLoggingIn || !isValidEmail(email) || code.isEmpty)

            #if DEBUG
            HStack {
                Rectangle().fill(Color.black.opacity(0.1)).frame(height: 1)
                Text("Debug：手机号".zh)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Rectangle().fill(Color.black.opacity(0.1)).frame(height: 1)
            }

            LoginNumberField(text: $phone, placeholder: "手机号")
                .loginFieldChrome()
            Button("手机号登录（Debug）".zh) {
                guard agreed else {
                    errorMessage = "请先勾选并同意用户协议与隐私政策"
                    return
                }
                Task { await loginByPhone() }
            }
            .buttonStyle(.bordered)
            .disabled(isLoggingIn || phone.count < 6 || code.isEmpty)
            #endif

            HStack(alignment: .center, spacing: 0) {
                Toggle("", isOn: $agreed)
                    .labelsHidden()
                    .scaleEffect(0.8)
                Text("已阅读并同意".zh)
                    .font(.caption)
                Button {
                    showLegal = .terms
                } label: {
                    Text("《用户协议》".zh)
                        .font(.caption)
                        .foregroundStyle(AppTheme.accent)
                }
                .buttonStyle(.plain)
                Button {
                    showLegal = .privacy
                } label: {
                    Text("《隐私政策》".zh)
                        .font(.caption)
                        .foregroundStyle(AppTheme.accent)
                }
                .buttonStyle(.plain)
                Spacer(minLength: 0)
            }

            if let errorMessage {
                Text(errorMessage.zh)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            #if DEBUG
            Text("当前接口：\(AuthAPI.debugEndpoint)")
                .font(.caption2)
                .foregroundStyle(.tertiary)
            #endif

            Spacer()
        }
        .padding()
        .background(AppTheme.parchmentGradient.ignoresSafeArea(.container))
        .sheet(item: $showLegal) { kind in
            NavigationStack {
                LegalDocumentView(title: kind.title.zh, file: kind.file)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("关闭".zh) { showLegal = nil }
                        }
                    }
            }
        }
    }

    private func isValidEmail(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.contains("@"), trimmed.contains(".") else { return false }
        return trimmed.count >= 5
    }

    private func sendEmailCode() async {
        guard agreed else {
            errorMessage = "请先勾选并同意用户协议与隐私政策"
            return
        }
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard isValidEmail(trimmedEmail) else {
            errorMessage = "请输入正确邮箱"
            return
        }
        isSendingCode = true
        defer { isSendingCode = false }
        do {
            let resp = try await AuthAPI.sendEmailCode(email: trimmedEmail)
            cooldownSec = max(resp.cooldownSec, 0)
            errorMessage = "验证码已发送"
            startCooldown()
        } catch {
            errorMessage = LoginError.describe(error)
        }
    }

    private func loginByEmail() async {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let trimmedCode = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty, !trimmedCode.isEmpty else {
            errorMessage = "请输入邮箱和验证码"
            return
        }
        isLoggingIn = true
        defer { isLoggingIn = false }
        do {
            let resp = try await AuthAPI.loginByEmail(email: trimmedEmail, code: trimmedCode)
            onSuccess(session(from: resp, email: trimmedEmail))
        } catch {
            errorMessage = LoginError.describe(error)
        }
    }

    private func loginWithApple(identityToken: String, fullName: String?) async {
        isLoggingIn = true
        defer { isLoggingIn = false }
        do {
            let resp = try await AuthAPI.loginWithApple(identityToken: identityToken, fullName: fullName)
            onSuccess(session(from: resp))
        } catch {
            errorMessage = LoginError.describe(error)
        }
    }

    private func loginWithGoogle() async {
        isLoggingIn = true
        defer { isLoggingIn = false }
        do {
            let idToken = try await OAuthSignIn.signInWithGoogle()
            let resp = try await AuthAPI.loginWithGoogle(idToken: idToken)
            onSuccess(session(from: resp))
        } catch {
            errorMessage = LoginError.describe(error)
        }
    }

    #if DEBUG
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
                    email: resp.user.email,
                    avatarSymbol: "person.crop.circle.fill",
                    accessToken: resp.accessToken
                )
            )
        } catch {
            errorMessage = LoginError.describe(error)
        }
    }
    #endif

    private func session(from resp: AuthAPI.LoginResponse, email: String? = nil) -> LocalUserSession {
        LocalUserSession(
            isLoggedIn: true,
            displayName: resp.user.nickname,
            phone: resp.user.phone,
            email: resp.user.email ?? email,
            avatarSymbol: "person.crop.circle.fill",
            accessToken: resp.accessToken
        )
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
    /// 真机 Debug：填 Mac 的局域网 IP（`ipconfig getifaddr en0`），不要填手机 IP。
    private static let baseURL = URL(string: "http://172.20.10.10:8080")!
    #endif
    #else
    /// 海外 Release；Connect 排除中国大陆。
    private static let baseURL = URL(string: "https://api.yiwanjia.work")!
    #endif

    static var debugEndpoint: String { baseURL.absoluteString }

    /// 不用 URLSession.shared：系统会缓存 HTTP/3，iPhone 11 在关 h3 后会一直 TLS 失败（-1200）。
    private static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 180
        config.waitsForConnectivity = false
        config.urlCache = nil
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: config)
    }()
    private static let aiTimeout: TimeInterval = 180

    struct SMSCodeResponse: Decodable {
        let ok: Bool
        let cooldownSec: Int
    }

    struct LoginResponse: Decodable {
        struct User: Decodable {
            let id: String
            let nickname: String
            let phone: String?
            let email: String?
        }
        let ok: Bool
        let accessToken: String
        let user: User
    }

    typealias SMSLoginResponse = LoginResponse

    private struct ErrorEnvelope: Decodable {
        let message: String?
        let code: Int?
    }

    private static func jsonRequest(path: String, method: String, timeout: TimeInterval = 15) -> URLRequest {
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = method
        req.timeoutInterval = timeout
        if #available(iOS 14.0, *) {
            req.assumesHTTP3Capable = false
        }
        return req
    }

    static func sendSMSCode(phone: String) async throws -> SMSCodeResponse {
        var req = jsonRequest(path: "v1/auth/sms/send", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["phone": phone])
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "发送验证码失败") }
        let decoded = try JSONDecoder().decode(SMSCodeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("发送验证码失败") }
        return decoded
    }

    static func loginBySMS(phone: String, code: String) async throws -> LoginResponse {
        var req = jsonRequest(path: "v1/auth/sms/login", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["phone": phone, "code": code])
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "登录失败") }
        let decoded = try JSONDecoder().decode(LoginResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("登录失败") }
        return decoded
    }

    static func sendEmailCode(email: String) async throws -> SMSCodeResponse {
        var req = jsonRequest(path: "v1/auth/email/send", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["email": email])
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "发送验证码失败") }
        let decoded = try JSONDecoder().decode(SMSCodeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("发送验证码失败") }
        return decoded
    }

    static func loginByEmail(email: String, code: String) async throws -> LoginResponse {
        var req = jsonRequest(path: "v1/auth/email/login", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["email": email, "code": code])
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "登录失败") }
        let decoded = try JSONDecoder().decode(LoginResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("登录失败") }
        return decoded
    }

    static func loginWithApple(identityToken: String, fullName: String?) async throws -> LoginResponse {
        var body: [String: Any] = ["identityToken": identityToken]
        if let fullName, !fullName.isEmpty {
            body["fullName"] = fullName
        }
        var req = jsonRequest(path: "v1/auth/apple", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "Apple 登录失败") }
        let decoded = try JSONDecoder().decode(LoginResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("Apple 登录失败") }
        return decoded
    }

    static func loginWithGoogle(idToken: String) async throws -> LoginResponse {
        var req = jsonRequest(path: "v1/auth/google", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["idToken": idToken])
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "Google 登录失败") }
        let decoded = try JSONDecoder().decode(LoginResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("Google 登录失败") }
        return decoded
    }

    struct MeResponse: Decodable {
        struct User: Decodable {
            let id: String
            let nickname: String
            let phone: String?
            let email: String?
        }
        let ok: Bool
        let user: User
    }

    static func fetchMe(accessToken: String) async throws -> MeResponse {
        var req = jsonRequest(path: "v1/me", method: "GET")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        if http.statusCode == 401 {
            throw LoginError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "获取用户信息失败") }
        let decoded = try JSONDecoder().decode(MeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("获取用户信息失败") }
        return decoded
    }

    private struct OkResponse: Decodable {
        let ok: Bool
    }

    static func deleteAccount(accessToken: String) async throws {
        var req = jsonRequest(path: "v1/me", method: "DELETE")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        if http.statusCode == 401 {
            throw LoginError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "注销账号失败") }
        let decoded = try JSONDecoder().decode(OkResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("注销账号失败") }
    }

    struct AIAnalyzeResponse: Decodable {
        struct Analysis: Codable, Hashable {
            let summary: String
            let focus: String
            let advice: [String]
        }

        let ok: Bool
        let analysis: Analysis
    }

    struct AIFollowupResponse: Decodable {
        let ok: Bool
        let reply: String
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

        var req = jsonRequest(path: "v1/ai/analyze", method: "POST", timeout: aiTimeout)
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.timeoutInterval = aiTimeout
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let data = try await perform(req, fallback: "解读失败")
        let decoded = try JSONDecoder().decode(AIAnalyzeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("解读失败") }
        return decoded
    }

    static func followupReading(
        result: CastResult,
        analysis: AIAnalyzeResponse.Analysis,
        conversation: [SavedAIFollowUp],
        message: String,
        accessToken: String
    ) async throws -> AIFollowupResponse {
        var payload: [String: Any] = [
            "method": result.method.rawValue,
            "primaryNumber": result.primaryNumber,
            "movingPositions": result.movingPositions,
            "lines": result.lines.map(\.rawValue),
            "hexTextVersion": "yi-zhengshi-2026-08",
            "message": message,
            "previousAnalysis": [
                "summary": analysis.summary,
                "focus": analysis.focus,
                "advice": analysis.advice,
            ],
            "conversation": conversation.map {
                ["user": $0.user, "assistant": $0.assistant]
            },
        ]
        if let question = result.question {
            payload["question"] = question
        }
        if let resultingNumber = result.resultingNumber {
            payload["resultingNumber"] = resultingNumber
        }

        var req = jsonRequest(path: "v1/ai/followup", method: "POST", timeout: aiTimeout)
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.timeoutInterval = aiTimeout
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let data = try await perform(req, fallback: "追问失败")
        let decoded = try JSONDecoder().decode(AIFollowupResponse.self, from: data)
        guard decoded.ok, !decoded.reply.isEmpty else { throw LoginError.network("追问失败") }
        return decoded
    }

    enum CasesFetchResult {
        case notModified
        case updated(version: String, cases: [CaseStudy])
    }

    static func fetchCases(ifNoneMatch: String?) async throws -> CasesFetchResult {
        var req = jsonRequest(path: "v1/cases", method: "GET", timeout: 20)
        req.cachePolicy = .reloadIgnoringLocalCacheData
        if let ifNoneMatch, !ifNoneMatch.isEmpty {
            req.setValue("\"\(ifNoneMatch)\"", forHTTPHeaderField: "If-None-Match")
        }
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        if http.statusCode == 304 {
            return .notModified
        }
        guard (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        struct Envelope: Decodable {
            let ok: Bool
            let version: String
            let cases: [CaseStudy]
        }
        let decoded = try JSONDecoder().decode(Envelope.self, from: data)
        guard decoded.ok else { throw URLError(.badServerResponse) }
        return .updated(version: decoded.version, cases: decoded.cases)
    }

    private static func perform(_ request: URLRequest, fallback: String) async throws -> Data {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError where error.code == .timedOut || error.code == .networkConnectionLost {
            throw LoginError.network("请求超时，请稍后重试")
        }
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常") }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: fallback) }
        return data
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
    case unauthorized

    var errorDescription: String? {
        switch self {
        case .network(let message): return message
        case .unauthorized: return "登录已过期，请重新登录"
        }
    }

    static func describe(_ error: Error) -> String {
        if let login = error as? LoginError, let text = login.errorDescription {
            return text
        }
        if let url = error as? URLError {
            switch url.code {
            case .timedOut:
                return "连接超时：\(AuthAPI.debugEndpoint)"
            case .cannotConnectToHost, .cannotFindHost, .networkConnectionLost, .notConnectedToInternet:
                return "连不上 \(AuthAPI.debugEndpoint)"
            default:
                return "网络异常（\(url.code.rawValue)）：\(AuthAPI.debugEndpoint)"
            }
        }
        return error.localizedDescription
    }
}

private struct AIAnalysisHistoryView: View {
    @State private var items: [SavedAIAnalysis] = SavedAIAnalysisStore.load()
    private let store = HexagramStore.shared

    var body: some View {
        Group {
            if items.isEmpty {
                ContentUnavailableView(
                    "还没有保存的解读",
                    systemImage: "sparkles",
                    description: Text("觉得合适的 AI 解读，可在结果页点「保存」".zh)
                )
            } else {
                List {
                    ForEach(items) { item in
                        NavigationLink {
                            AIAnalysisView(saved: item)
                        } label: {
                            savedRow(item)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                SavedAIAnalysisStore.remove(id: item.id)
                                items = SavedAIAnalysisStore.load()
                            } label: {
                                Image(systemName: "trash.fill")
                            }
                            .tint(.red)
                            .accessibilityLabel("删除".zh)
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("保存的AI解读".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .onAppear {
            items = SavedAIAnalysisStore.load()
        }
    }

    @ViewBuilder
    private func savedRow(_ item: SavedAIAnalysis) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if let hex = store.hexagram(number: item.primaryNumber) {
                Text("\(hex.symbol) \(hex.name)".zh)
                    .font(.headline)
            } else {
                Text("第\(item.primaryNumber)卦".zh)
                    .font(.headline)
            }
            Text(ReadingRecordRow.timeString(item.updatedAt).zh)
                .font(.caption)
                .foregroundStyle(.secondary)
            if let question = item.question, !question.isEmpty {
                Text(question.zh)
                    .font(.subheadline)
                    .lineLimit(1)
            } else {
                Text(item.analysis.summary.zh)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
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
            Section("头像".zh) {
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

            Section("昵称".zh) {
                TextField("输入昵称", text: $nicknameDraft)
                    .appTextFieldStyle()
            }
        }
        .navigationTitle("编辑资料".zh)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("保存".zh) {
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
        .alert("保存失败".zh, isPresented: $showValidationAlert) {
            Button("知道了".zh, role: .cancel) {}
        } message: {
            Text(validationMessage.zh)
        }
    }
}

private struct SettingsView: View {
    @Binding var session: LocalUserSession
    @Environment(\.dismiss) private var dismiss
    @AppStorage(TapSoundPlayer.defaultsKey) private var tapSound: TapSoundKind = .none
    @State private var showLogoutConfirm = false
    @State private var showDeleteConfirm = false
    @State private var isDeletingAccount = false
    @State private var deleteErrorMessage: String?
    @State private var recycleCount = HistoryTrashStore.load().count

    var body: some View {
        List {
            Section {
                NavigationLink {
                    TapSoundSettingsView()
                } label: {
                    HStack {
                        Label("按键音效".zh, systemImage: "speaker.wave.2")
                        Spacer()
                        Text(tapSound.title.zh)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                NavigationLink {
                    RecycleBinView()
                } label: {
                    HStack {
                        Label("回收站".zh, systemImage: "trash")
                        Spacer()
                        Text("\(recycleCount)".zh)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                NavigationLink {
                    LegalDocumentView(title: "隐私政策".zh, file: "privacy_policy")
                } label: {
                    Label("隐私政策".zh, systemImage: "lock.shield")
                }
                NavigationLink {
                    LegalDocumentView(title: "用户协议".zh, file: "terms_of_service")
                } label: {
                    Label("用户协议".zh, systemImage: "doc.text")
                }
            }

            if session.isLoggedIn {
                Section {
                    Button("退出登录".zh) {
                        showLogoutConfirm = true
                    }
                    Button("注销账号".zh, role: .destructive) {
                        showDeleteConfirm = true
                    }
                    .disabled(isDeletingAccount)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("设置".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .onAppear {
            recycleCount = HistoryTrashStore.load().count
        }
        .alert("确认退出登录？".zh, isPresented: $showLogoutConfirm) {
            Button("取消".zh, role: .cancel) {}
            Button("退出登录".zh) {
                session = .guest
                LocalAuthStore.save(session)
                dismiss()
            }
        }
        .alert("确认注销账号？".zh, isPresented: $showDeleteConfirm) {
            Button("取消".zh, role: .cancel) {}
            Button("注销账号".zh, role: .destructive) {
                Task { await deleteAccount() }
            }
        } message: {
            Text("注销后，服务器上的账号信息将被永久删除且不可恢复。设备本地的起卦记录与保存的 AI 解读不会自动清除。".zh)
        }
        .alert("注销失败".zh, isPresented: Binding(
            get: { deleteErrorMessage != nil },
            set: { if !$0 { deleteErrorMessage = nil } }
        )) {
            Button("知道了".zh, role: .cancel) {}
        } message: {
            Text((deleteErrorMessage ?? "").zh)
        }
    }

    private func deleteAccount() async {
        guard let token = session.accessToken, !token.isEmpty else {
            deleteErrorMessage = "登录态已失效，请重新登录"
            return
        }
        isDeletingAccount = true
        defer { isDeletingAccount = false }
        do {
            try await AuthAPI.deleteAccount(accessToken: token)
            session = .guest
            LocalAuthStore.save(session)
            dismiss()
        } catch {
            deleteErrorMessage = LoginError.describe(error)
        }
    }
}

private struct TapSoundSettingsView: View {
    @AppStorage(TapSoundPlayer.defaultsKey) private var tapSound: TapSoundKind = .none

    var body: some View {
        List {
            Section {
                ForEach(TapSoundKind.allCases) { kind in
                    Button {
                        tapSound = kind
                        TapSoundPlayer.shared.play(kind: kind)
                    } label: {
                        HStack {
                            Text(kind.title.zh)
                            Spacer()
                            if tapSound == kind {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(AppTheme.accent)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            } footer: {
                Text("点按「随机」「一键随机」「摇」「一键摇满」时播放。系统静音时不会出声。".zh)
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("按键音效".zh)
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
    }
}

private struct RecycleBinView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var entries: [HistoryTrashEntry] = HistoryTrashStore.load()
    @State private var showClearConfirm = false
    private let store = HexagramStore.shared

    var body: some View {
        Group {
            if entries.isEmpty {
                ContentUnavailableView(
                    "回收站为空",
                    systemImage: "trash",
                    description: Text("删除的记录会先放在这里，可恢复".zh)
                )
            } else {
                List {
                    ForEach(entries.sorted(by: { $0.deletedAt > $1.deletedAt })) { entry in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                if let hex = store.hexagram(number: entry.primaryNumber) {
                                    Text("\(hex.symbol) \(hex.name)".zh)
                                        .font(.headline)
                                } else {
                                    Text("第\(entry.primaryNumber)卦".zh)
                                        .font(.headline)
                                }
                                if let resulting = entry.resultingNumber {
                                    Text("→".zh)
                                        .foregroundStyle(.secondary)
                                    if let hex = store.hexagram(number: resulting) {
                                        Text("\(hex.symbol) \(hex.name)".zh)
                                            .font(.headline)
                                    } else {
                                        Text("第\(resulting)卦".zh)
                                            .font(.headline)
                                    }
                                }
                            }
                            .lineLimit(1)
                            Text(ReadingRecordRow.timeString(entry.createdAt).zh)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if let question = entry.question, !question.isEmpty {
                                Text(question.zh)
                                    .font(.subheadline)
                                    .lineLimit(1)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button("恢复".zh) {
                                modelContext.insert(entry.toReadingRecord())
                                try? modelContext.save()
                                HistoryTrashStore.remove(entryID: entry.id)
                                entries = HistoryTrashStore.load()
                            }
                            .tint(.green)

                            Button("彻底删除".zh, role: .destructive) {
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
        .navigationTitle("回收站".zh)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !entries.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("清空".zh) {
                        showClearConfirm = true
                    }
                    .tint(.red)
                }
            }
        }
        .alert("确认清空？".zh, isPresented: $showClearConfirm) {
            Button("取消".zh, role: .cancel) {}
            Button("确定".zh, role: .destructive) {
                HistoryTrashStore.clearAll()
                entries = []
            }
        } message: {
            Text("回收站中的记录将被彻底删除，无法恢复。".zh)
        }
        .parchmentBackground()
        .onAppear {
            entries = HistoryTrashStore.load()
        }
    }
}

// MARK: - 应用内协议页面（WebView 渲染本地 HTML，不跳浏览器）

enum LegalDocKind: String, Identifiable {
    case terms, privacy
    var id: String { rawValue }
    var title: String {
        switch self {
        case .terms: return "用户协议"
        case .privacy: return "隐私政策"
        }
    }
    var file: String {
        switch self {
        case .terms: return "terms_of_service"
        case .privacy: return "privacy_policy"
        }
    }
}

struct LegalWebView: UIViewRepresentable {
    let fileName: String

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 0.969, green: 0.953, blue: 0.914, alpha: 1)
        webView.scrollView.bounces = true
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        if let url = Bundle.main.url(forResource: fileName, withExtension: "html") {
            webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
    }
}

struct LegalDocumentView: View {
    let title: String
    let file: String

    var body: some View {
        LegalWebView(fileName: file)
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
    }
}
