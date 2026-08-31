import SwiftUI
import SwiftData
import WebKit

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
        UnlockStore.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            RootTabView()
        }
        .modelContainer(sharedModelContainer)
    }
}

struct RootTabView: View {
    @Environment(\.locale) private var locale
    @State private var appNavigation = AppNavigation()

    var body: some View {
        @Bindable var appNavigation = appNavigation
        let language = AppLanguage.from(locale)
        TabView(selection: $appNavigation.selectedTab) {
            CastingHomeView()
                .tabItem {
                    Label("起卦".ui("Cast"), systemImage: "sparkles")
                }
                .tag(AppTab.cast)
            HistoryListView()
                .tabItem {
                    Label("历史".ui("History"), systemImage: "clock")
                }
                .tag(AppTab.history)
            NavigationStack {
                AIAnalysisHistoryView()
            }
            .tabItem {
                Label("问答".ui("Readings"), systemImage: "bubble.left.and.bubble.right")
            }
            .tag(AppTab.qa)
            MyMenuView()
                .tabItem {
                    Label("我的".ui("Me"), systemImage: "person.crop.circle")
                }
                .tag(AppTab.me)
        }
        .id(language)
        .tint(AppTheme.accent)
        .preferredColorScheme(.light)
        .environment(\.locale, language.locale)
        .environment(appNavigation)
        .environment(UnlockStore.shared)
        .animation(nil, value: appNavigation.selectedTab)
        .dismissKeyboardOnBlankTap()
    }
}

struct MyMenuView: View {
    @Environment(\.openURL) private var openURL
    @Environment(UnlockStore.self) private var unlock
    @State private var session: LocalUserSession = LocalAuthStore.load()
    @State private var showLoginSheet = false
    @State private var isCheckingUpdate = false
    @State private var updateResult: UpdateCheckResult?

    private var installedVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if session.isLoggedIn {
                        NavigationLink {
                            ProfileEditView(session: $session)
                        } label: {
                            HStack(spacing: 10) {
                                ProfileAvatarView(
                                    name: session.displayName,
                                    image: session.avatarImagePath == nil ? nil : ProfileAvatarFile.load(),
                                    size: 40
                                )
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(session.displayName.zh)
                                        .foregroundStyle(.primary)
                                    if let email = session.email, !email.isEmpty {
                                        Text(email.zh)
                                            .font(.footnote)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                            }
                        }
                    } else {
                        HStack(spacing: 10) {
                            Image(systemName: "person.crop.circle.badge.exclamationmark")
                                .foregroundStyle(.secondary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("未登录".ui("Not signed in"))
                                Text("支持 Apple / 邮箱登录".ui("Apple or email"))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button("登录".ui("Sign In")) {
                                showLoginSheet = true
                            }
                        }
                    }

                    NavigationLink {
                        UnlockReadingsView()
                    } label: {
                        Label(
                            unlock.isUnlocked ? "已解锁问答".ui("Readings unlocked") : "解锁问答".ui("Unlock Readings"),
                            systemImage: unlock.isUnlocked ? "checkmark.seal" : "lock.open"
                        )
                    }
                }

                Section {
                    NavigationLink {
                        YijingIntroListView()
                    } label: {
                        Label("基础入门".ui("Primer"), systemImage: "text.book.closed")
                    }
                    NavigationLink {
                        ClassicHexagramListView()
                    } label: {
                        Label("六十四卦".ui("64 Hexagrams"), systemImage: "book")
                    }
                    NavigationLink {
                        ClassicWingListView()
                    } label: {
                        Label("四传".ui("The Wings"), systemImage: "scroll")
                    }
                    NavigationLink {
                        CaseListView()
                    } label: {
                        Label("案例".ui("Cases"), systemImage: "books.vertical")
                    }
                }

                Section {
                    NavigationLink {
                        FeedbackView(session: session)
                    } label: {
                        Label("意见反馈".ui("Feedback"), systemImage: "envelope")
                    }
                    Button {
                        Task { await checkForUpdate() }
                    } label: {
                        HStack {
                            Label("检查更新".ui("Check for Update"), systemImage: "arrow.clockwise")
                            Spacer()
                            if isCheckingUpdate {
                                ProgressView()
                            } else if !installedVersion.isEmpty {
                                Text(installedVersion.zh)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .foregroundStyle(.primary)
                    .buttonStyle(.plain)
                    .disabled(isCheckingUpdate)
                }

                Section {
                    NavigationLink {
                        SettingsView(session: $session)
                    } label: {
                        Label("设置".ui("Settings"), systemImage: "gearshape")
                    }
                }
            }
            .navigationTitle("我的".ui("Me"))
            .navigationBarTitleDisplayMode(.inline)
            .parchmentBackground(hidesTabBar: false)
            .onAppear {
                session = LocalAuthStore.load()
                Task { await refreshSessionIfNeeded() }
            }
            .sheet(isPresented: $showLoginSheet) {
                LoginSheetView { newSession in
                    session = newSession
                    LocalAuthStore.save(newSession)
                    showLoginSheet = false
                    Task { await UnlockStore.shared.refreshFromServer() }
                }
            }
            .alert(item: $updateResult) { result in
                switch result {
                case .latest(let current):
                    return Alert(
                        title: Text("已是最新版本".ui("You're up to date")),
                        message: Text("当前版本 \(current)".ui("Version \(current)")),
                        dismissButton: .cancel(Text("好的".ui("OK")))
                    )
                case .available(let latest, let url):
                    return Alert(
                        title: Text("发现新版本".ui("Update available")),
                        message: Text("最新版本 \(latest)，可前往商店更新。".ui("Version \(latest) is available.")),
                        primaryButton: .default(Text("去更新".ui("Update"))) { openURL(url) },
                        secondaryButton: .cancel(Text("以后再说".ui("Later")))
                    )
                case .failed(let message):
                    return Alert(
                        title: Text("检查失败".ui("Couldn't check")),
                        message: Text(message.zh),
                        dismissButton: .cancel(Text("知道了".ui("OK")))
                    )
                }
            }
        }
    }

    @MainActor
    private func checkForUpdate() async {
        guard !isCheckingUpdate else { return }
        isCheckingUpdate = true
        defer { isCheckingUpdate = false }
        do {
            let info = try await AuthAPI.fetchAppVersion()
            let latest = info.ios.trimmingCharacters(in: .whitespacesAndNewlines)
            if isNewerAppVersion(latest, than: installedVersion),
               let url = URL(string: info.iosStoreUrl), !info.iosStoreUrl.isEmpty {
                updateResult = .available(latest: latest, url: url)
            } else {
                updateResult = .latest(current: installedVersion.isEmpty ? latest : installedVersion)
            }
        } catch {
            updateResult = .failed(LoginError.describe(error))
        }
    }

    @MainActor
    private func refreshSessionIfNeeded() async {
        guard session.isLoggedIn, let token = session.accessToken, !token.isEmpty else { return }
        do {
            let me = try await AuthAPI.fetchMe(accessToken: token)
            session = session.applying(account: me.user)
            session.isLoggedIn = true
            session.accessToken = token
            session = await ProfileSync.pullAvatar(session: session, user: me.user, accessToken: token)
            LocalAuthStore.save(session)
            UnlockStore.shared.applyQuota(
                unlocked: me.user.iapUnlocked,
                limit: me.user.aiDailyLimit,
                used: me.user.aiDailyUsed,
                remaining: me.user.aiDailyRemaining
            )
        } catch LoginError.unauthorized {
            session = .guest
            LocalAuthStore.save(session)
            UnlockStore.shared.clearLocal()
        } catch {
            // 网络异常时保留本地会话，下次再校验
        }
    }
}

private enum UpdateCheckResult: Identifiable {
    case latest(current: String)
    case available(latest: String, url: URL)
    case failed(String)

    var id: String {
        switch self {
        case .latest(let current): return "latest-\(current)"
        case .available(let latest, _): return "available-\(latest)"
        case .failed(let message): return "failed-\(message)"
        }
    }
}

private func versionParts(_ raw: String) -> [Int] {
    raw.split(separator: ".").map { segment in
        Int(segment.filter(\.isNumber)) ?? 0
    }
}

private func isNewerAppVersion(_ latest: String, than current: String) -> Bool {
    let newest = versionParts(latest)
    let installed = versionParts(current)
    let count = max(newest.count, installed.count)
    for index in 0..<count {
        let left = index < newest.count ? newest[index] : 0
        let right = index < installed.count ? installed[index] : 0
        if left != right { return left > right }
    }
    return false
}

struct LocalUserSession: Codable {
    var isLoggedIn: Bool
    var displayName: String
    var phone: String?
    var email: String?
    var avatarSymbol: String
    var accessToken: String?
    var avatarImagePath: String?
    var avatarUpdatedAt: String?

    static let guest = LocalUserSession(
        isLoggedIn: false,
        displayName: "游客",
        phone: nil,
        email: nil,
        avatarSymbol: "person.crop.circle.fill",
        accessToken: nil
    )

    func applying(account user: AuthAPI.AccountUser) -> LocalUserSession {
        var next = self
        next.isLoggedIn = true
        next.displayName = user.nickname
        next.phone = user.phone ?? next.phone
        next.email = user.email ?? next.email
        next.avatarUpdatedAt = user.avatarUpdatedAt
        return next
    }
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
    private enum PendingAction {
        case apple
    }

    @Environment(\.dismiss) private var dismiss
    @State private var agreed = false
    @State private var showLegal: LegalDocKind?
    @State private var showConsentAlert = false
    @State private var pendingAction: PendingAction?
    @State private var errorMessage: String?
    @State private var isLoggingIn = false
    let onSuccess: (LocalUserSession) -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(AppTheme.accent.opacity(0.55))
                            .frame(width: 32, height: 32)
                            .background(Circle().fill(Color.white.opacity(0.55)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("关闭".ui("Close"))
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)

                Spacer(minLength: 20)

                LoginBrandMark()

                Spacer(minLength: 36)

                VStack(spacing: 16) {
                    Button {
                        requireConsent(then: .apple)
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "apple.logo")
                                .font(.system(size: 17, weight: .medium))
                            Text("通过 Apple 登录".ui("Sign in with Apple"))
                        }
                    }
                    .buttonStyle(LoginPrimaryButtonStyle(fill: .black))
                    .disabled(isLoggingIn)

                    LoginStatusLine(isBusy: isLoggingIn, message: errorMessage, isError: true)

                    LoginConsentRow(agreed: $agreed) { showLegal = $0 }
                }
                .padding(.horizontal, 28)

                Spacer(minLength: 24)

                VStack(spacing: 14) {
                    LoginSectionDivider(title: "其他登录方式".ui("Other ways to sign in"))
                    NavigationLink {
                        EmailLoginView(agreed: $agreed, onSuccess: onSuccess)
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "envelope")
                                .font(.system(size: 15, weight: .medium))
                            Text("邮箱登录".ui("Email"))
                        }
                    }
                    .buttonStyle(LoginSecondaryButtonStyle())

                    #if DEBUG
                    Text("当前接口：\(AuthAPI.debugEndpoint)")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                    #endif
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 28)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(AppTheme.parchmentGradient.ignoresSafeArea(.container))
            .toolbar(.hidden, for: .navigationBar)
        }
        .sheet(item: $showLegal) { kind in
            NavigationStack {
                LegalDocumentView(title: kind.title.zh, file: kind.file, hidesTabBar: false)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("关闭".ui("Close")) { showLegal = nil }
                        }
                    }
            }
        }
        .alert("请先同意协议".ui("Please agree first"), isPresented: $showConsentAlert) {
            Button("取消".ui("Cancel"), role: .cancel) {
                pendingAction = nil
            }
            Button("同意并继续".ui("Agree and Continue")) {
                agreed = true
                let action = pendingAction
                pendingAction = nil
                if let action {
                    Task { await perform(action) }
                }
            }
        } message: {
            Text("登录前需同意《用户协议》和《隐私政策》。点击「同意并继续」即表示你已阅读并同意。".ui("Please read and agree to the Terms of Use and Privacy Policy before signing in."))
        }
    }

    private func requireConsent(then action: PendingAction) {
        if agreed {
            Task { await perform(action) }
            return
        }
        pendingAction = action
        showConsentAlert = true
    }

    @MainActor
    private func perform(_ action: PendingAction) async {
        switch action {
        case .apple:
            await startAppleLogin()
        }
    }

    private func startAppleLogin() async {
        isLoggingIn = true
        defer { isLoggingIn = false }
        do {
            let result = try await AppleSignIn.signIn()
            let resp = try await AuthAPI.loginWithApple(
                identityToken: result.identityToken,
                fullName: result.fullName
            )
            onSuccess(await makeSession(from: resp))
        } catch is CancellationError {
            // 用户取消，不提示
        } catch {
            errorMessage = LoginError.describe(error)
        }
    }
}

private struct EmailLoginView: View {
    private enum PendingAction {
        case sendEmailCode
        case emailLogin
    }

    @Binding var agreed: Bool
    let onSuccess: (LocalUserSession) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var code = ""
    @State private var showLegal: LegalDocKind?
    @State private var showConsentAlert = false
    @State private var pendingAction: PendingAction?
    @State private var errorMessage: String?
    @State private var isSendingCode = false
    @State private var isLoggingIn = false
    @State private var cooldownSec = 0

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
                .frame(height: 28)

            VStack(spacing: 8) {
                Text("邮箱登录".ui("Email"))
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(AppTheme.accent)
                Text("收到验证码后填入即可登录".ui("Enter the code we send you"))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 24)

            VStack(spacing: 12) {
                LoginFieldRow(systemImage: "envelope") {
                    TextField("邮箱".ui("Email"), text: $email)
                        .textFieldStyle(.plain)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                }

                LoginFieldRow(systemImage: "number") {
                    LoginNumberField(text: $code, placeholder: "验证码".ui("Code"))
                    Rectangle()
                        .fill(AppTheme.fieldStroke)
                        .frame(width: 1, height: 22)
                    Button(cooldownSec > 0 ? "\(cooldownSec)s" : "发送验证码".ui("Send code")) {
                        requireConsent(then: .sendEmailCode)
                    }
                    .buttonStyle(.plain)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(
                        canSendCode ? AppTheme.accent : Color.secondary.opacity(0.5)
                    )
                    .disabled(!canSendCode)
                }

                Button("登 录".ui("Sign In")) {
                    requireConsent(then: .emailLogin)
                }
                .buttonStyle(LoginPrimaryButtonStyle(fill: AppTheme.accent))
                .disabled(isLoggingIn || !isValidEmail(email) || code.isEmpty)
                .padding(.top, 4)

                LoginStatusLine(
                    isBusy: isLoggingIn,
                    message: errorMessage,
                    isError: errorMessage != "验证码已发送".ui("Code sent")
                )

                LoginConsentRow(agreed: $agreed) { showLegal = $0 }
            }
            .padding(.horizontal, 28)

            Spacer(minLength: 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppTheme.parchmentGradient.ignoresSafeArea(.container))
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $showLegal) { kind in
            NavigationStack {
                LegalDocumentView(title: kind.title.zh, file: kind.file, hidesTabBar: false)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("关闭".ui("Close")) { showLegal = nil }
                        }
                    }
            }
        }
        .alert("请先同意协议".ui("Please agree first"), isPresented: $showConsentAlert) {
            Button("取消".ui("Cancel"), role: .cancel) {
                pendingAction = nil
            }
            Button("同意并继续".ui("Agree and Continue")) {
                agreed = true
                let action = pendingAction
                pendingAction = nil
                if let action {
                    Task { await perform(action) }
                }
            }
        } message: {
            Text("登录前需同意《用户协议》和《隐私政策》。点击「同意并继续」即表示你已阅读并同意。".ui("Please read and agree to the Terms of Use and Privacy Policy before signing in."))
        }
    }

    private func requireConsent(then action: PendingAction) {
        if agreed {
            Task { await perform(action) }
            return
        }
        pendingAction = action
        showConsentAlert = true
    }

    @MainActor
    private func perform(_ action: PendingAction) async {
        switch action {
        case .sendEmailCode:
            await sendEmailCode()
        case .emailLogin:
            await loginByEmail()
        }
    }

    private var canSendCode: Bool {
        !isSendingCode && cooldownSec == 0 && isValidEmail(email)
    }

    private func isValidEmail(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.contains("@"), trimmed.contains(".") else { return false }
        return trimmed.count >= 5
    }

    private func sendEmailCode() async {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard isValidEmail(trimmedEmail) else {
            errorMessage = "请输入正确邮箱".ui("Enter a valid email")
            return
        }
        isSendingCode = true
        defer { isSendingCode = false }
        do {
            let resp = try await AuthAPI.sendEmailCode(email: trimmedEmail)
            cooldownSec = max(resp.cooldownSec, 0)
            errorMessage = "验证码已发送".ui("Code sent")
            startCooldown()
        } catch {
            errorMessage = LoginError.describe(error)
        }
    }

    private func loginByEmail() async {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let trimmedCode = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty, !trimmedCode.isEmpty else {
            errorMessage = "请输入邮箱和验证码".ui("Enter email and code")
            return
        }
        isLoggingIn = true
        defer { isLoggingIn = false }
        do {
            let resp = try await AuthAPI.loginByEmail(email: trimmedEmail, code: trimmedCode)
            onSuccess(await makeSession(from: resp, email: trimmedEmail))
        } catch {
            errorMessage = LoginError.describe(error)
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

private struct LoginBrandMark: View {
    /// 纯装饰用的六爻图形，自上而下阳阴阳阳阴阳。
    private let strokes = [true, false, true, true, false, true]

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.62))
                Circle()
                    .stroke(AppTheme.accent.opacity(0.16), lineWidth: 1)
                VStack(spacing: 4.5) {
                    ForEach(Array(strokes.enumerated()), id: \.offset) { _, isYang in
                        if isYang {
                            bar(width: 36)
                        } else {
                            HStack(spacing: 9) {
                                bar(width: 13.5)
                                bar(width: 13.5)
                            }
                        }
                    }
                }
            }
            .frame(width: 92, height: 92)

            VStack(spacing: 7) {
                Text("易玩家".zh)
                    .font(.system(size: 27, weight: .semibold))
                    .tracking(8)
                    .padding(.leading, 8)
                    .foregroundStyle(AppTheme.accent)
                Text("起卦观辞 · 玩占明理".ui("Cast, then contemplate the words"))
                    .font(.footnote)
                    .tracking(1.5)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func bar(width: CGFloat) -> some View {
        Capsule(style: .continuous)
            .fill(AppTheme.accent.opacity(0.82))
            .frame(width: width, height: 3.5)
    }
}

private struct LoginPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    let fill: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .fill(fill.opacity(isEnabled ? 1 : 0.3))
            )
            .opacity(configuration.isPressed ? 0.82 : 1)
    }
}

private struct LoginSecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 15, weight: .medium))
            .foregroundStyle(AppTheme.accent)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .fill(Color.white.opacity(0.55))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .stroke(AppTheme.accent.opacity(0.22), lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

private struct LoginFieldRow<Content: View>: View {
    let systemImage: String
    @ViewBuilder let content: Content

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.system(size: 15))
                .foregroundStyle(AppTheme.accent.opacity(0.55))
                .frame(width: 18)
            content
        }
        .padding(.horizontal, 14)
        .frame(height: 50)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(AppTheme.fieldFill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(AppTheme.fieldStroke, lineWidth: 1)
        )
    }
}

private struct LoginSectionDivider: View {
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            line
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            line
        }
    }

    private var line: some View {
        Rectangle()
            .fill(AppTheme.accent.opacity(0.14))
            .frame(height: 1)
    }
}

/// 高度固定，避免出错或转圈时按钮上下跳动。
private struct LoginStatusLine: View {
    let isBusy: Bool
    let message: String?
    let isError: Bool

    var body: some View {
        ZStack {
            if isBusy {
                HStack(spacing: 6) {
                    ProgressView()
                        .controlSize(.small)
                    Text("登录中…".ui("Signing in…"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else if let message {
                Text(message.zh)
                    .font(.caption)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(isError ? Color.red.opacity(0.85) : Color.secondary)
            }
        }
        .frame(minHeight: 18)
    }
}

private struct LoginConsentRow: View {
    @Binding var agreed: Bool
    let onShowLegal: (LegalDocKind) -> Void

    var body: some View {
        HStack(spacing: 6) {
            Button {
                agreed.toggle()
            } label: {
                Image(systemName: agreed ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 15))
                    .foregroundStyle(agreed ? AppTheme.accent : Color.secondary.opacity(0.45))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("同意用户协议和隐私政策".ui("Agree to Terms of Use and Privacy Policy"))

            Text("已阅读并同意".ui("I agree to"))
            Button {
                onShowLegal(.terms)
            } label: {
                Text("《用户协议》".ui("Terms of Use"))
                    .foregroundStyle(AppTheme.accent)
            }
            .buttonStyle(.plain)
            Button {
                onShowLegal(.privacy)
            } label: {
                Text("《隐私政策》".ui("Privacy Policy"))
                    .foregroundStyle(AppTheme.accent)
            }
            .buttonStyle(.plain)
        }
        .font(.caption)
        .foregroundStyle(.secondary)
    }
}

@MainActor
private func makeSession(from resp: AuthAPI.LoginResponse, email: String? = nil) async -> LocalUserSession {
    UnlockStore.shared.applyQuota(
        unlocked: resp.user.iapUnlocked,
        limit: resp.user.aiDailyLimit,
        used: resp.user.aiDailyUsed,
        remaining: resp.user.aiDailyRemaining
    )
    var session = LocalUserSession(
        isLoggedIn: true,
        displayName: resp.user.nickname,
        phone: resp.user.phone,
        email: resp.user.email ?? email,
        avatarSymbol: "person.crop.circle.fill",
        accessToken: resp.accessToken
    ).applying(account: resp.user)
    session = await ProfileSync.pullAvatar(
        session: session,
        user: resp.user,
        accessToken: resp.accessToken
    )
    LocalAuthStore.save(session)
    return session
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

    struct AccountUser: Decodable {
        let id: String
        let nickname: String
        let phone: String?
        let email: String?
        let createdAt: String?
        let hasAvatar: Bool?
        let avatarUpdatedAt: String?
        let iapUnlocked: Bool?
        let aiDailyLimit: Int?
        let aiDailyUsed: Int?
        let aiDailyRemaining: Int?
    }

    struct LoginResponse: Decodable {
        let ok: Bool
        let accessToken: String
        let user: AccountUser
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

    static func sendEmailCode(email: String) async throws -> SMSCodeResponse {
        var req = jsonRequest(path: "v1/auth/email/send", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["email": email])
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常".ui("Network error")) }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "发送验证码失败".ui("Couldn’t send code")) }
        let decoded = try JSONDecoder().decode(SMSCodeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("发送验证码失败".ui("Couldn’t send code")) }
        return decoded
    }

    static func loginByEmail(email: String, code: String) async throws -> LoginResponse {
        var req = jsonRequest(path: "v1/auth/email/login", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["email": email, "code": code])
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常".ui("Network error")) }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "登录失败".ui("Sign-in failed")) }
        let decoded = try JSONDecoder().decode(LoginResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("登录失败".ui("Sign-in failed")) }
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
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常".ui("Network error")) }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "Apple 登录失败".ui("Apple sign-in failed")) }
        let decoded = try JSONDecoder().decode(LoginResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("Apple 登录失败".ui("Apple sign-in failed")) }
        return decoded
    }


    struct MeResponse: Decodable {
        let ok: Bool
        let user: AccountUser
    }

    struct IAPVerifyResponse: Decodable {
        let ok: Bool
        let unlocked: Bool
        let productId: String
        let aiDailyLimit: Int
    }

    static func verifyIAP(signedTransaction: String, accessToken: String) async throws -> IAPVerifyResponse {
        var req = jsonRequest(path: "v1/iap/verify", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.httpBody = try JSONSerialization.data(withJSONObject: [
            "platform": "ios",
            "signedTransaction": signedTransaction,
        ])
        let data = try await perform(req, fallback: "无法验证购买".ui("Couldn’t verify the purchase"))
        let decoded = try JSONDecoder().decode(IAPVerifyResponse.self, from: data)
        guard decoded.ok, decoded.unlocked else {
            throw LoginError.network("无法验证购买".ui("Couldn’t verify the purchase"))
        }
        return decoded
    }

    static func fetchMe(accessToken: String) async throws -> MeResponse {
        var req = jsonRequest(path: "v1/me", method: "GET")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常".ui("Network error")) }
        if http.statusCode == 401 {
            throw LoginError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "获取用户信息失败".ui("Couldn’t load profile")) }
        let decoded = try JSONDecoder().decode(MeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("获取用户信息失败".ui("Couldn’t load profile")) }
        return decoded
    }

    static func updateMe(nickname: String, accessToken: String) async throws -> MeResponse {
        var req = jsonRequest(path: "v1/me", method: "PATCH")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["nickname": nickname])
        let data = try await perform(req, fallback: "保存资料失败".ui("Couldn’t save profile"))
        let decoded = try JSONDecoder().decode(MeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("保存资料失败".ui("Couldn’t save profile")) }
        return decoded
    }

    static func sendBindEmailCode(email: String, accessToken: String) async throws -> SMSCodeResponse {
        var req = jsonRequest(path: "v1/me/email/send", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["email": email])
        let data = try await perform(req, fallback: "发送验证码失败".ui("Couldn’t send code"))
        let decoded = try JSONDecoder().decode(SMSCodeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("发送验证码失败".ui("Couldn’t send code")) }
        return decoded
    }

    static func bindEmail(email: String, code: String, accessToken: String) async throws -> MeResponse {
        var req = jsonRequest(path: "v1/me/email/bind", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["email": email, "code": code])
        let data = try await perform(req, fallback: "绑定失败".ui("Couldn’t link email"))
        let decoded = try JSONDecoder().decode(MeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("绑定失败".ui("Couldn’t link email")) }
        return decoded
    }

    static func uploadAvatar(_ jpeg: Data, accessToken: String) async throws -> MeResponse {
        var req = jsonRequest(path: "v1/me/avatar", method: "PUT")
        req.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.httpBody = jpeg
        let data = try await perform(req, fallback: "上传头像失败".ui("Couldn’t upload photo"))
        let decoded = try JSONDecoder().decode(MeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("上传头像失败".ui("Couldn’t upload photo")) }
        return decoded
    }

    static func fetchAvatar(accessToken: String) async throws -> Data {
        var req = jsonRequest(path: "v1/me/avatar", method: "GET")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常".ui("Network error")) }
        if http.statusCode == 401 {
            throw LoginError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else {
            throw decodeError(data, fallback: "获取头像失败".ui("Couldn’t load photo"))
        }
        return data
    }

    struct AppVersionResponse: Decodable {
        let ok: Bool
        let ios: String
        let android: String
        let iosStoreUrl: String
        let androidStoreUrl: String
    }

    static func fetchAppVersion() async throws -> AppVersionResponse {
        var req = jsonRequest(path: "v1/app/version", method: "GET")
        let data = try await perform(req, fallback: "检查更新失败".ui("Couldn’t check for update"))
        let decoded = try JSONDecoder().decode(AppVersionResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("检查更新失败".ui("Couldn’t check for update")) }
        return decoded
    }

    static func submitFeedback(
        body: String,
        contact: String,
        accessToken: String?
    ) async throws {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let payload: [String: Any] = [
            "body": body,
            "contact": contact,
            "platform": "ios",
            "appVersion": version,
        ]
        var req = jsonRequest(path: "v1/feedback", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let accessToken, !accessToken.isEmpty {
            req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let data = try await perform(req, fallback: "提交失败".ui("Couldn’t send"))
        let decoded = try JSONDecoder().decode(OkResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("提交失败".ui("Couldn’t send")) }
    }

    private struct OkResponse: Decodable {
        let ok: Bool
    }

    static func deleteAccount(accessToken: String) async throws {
        var req = jsonRequest(path: "v1/me", method: "DELETE")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常".ui("Network error")) }
        if http.statusCode == 401 {
            throw LoginError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: "注销账号失败".ui("Couldn’t delete account")) }
        let decoded = try JSONDecoder().decode(OkResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("注销账号失败".ui("Couldn’t delete account")) }
    }

    struct AIAnalyzeResponse: Decodable {
        struct Analysis: Codable, Hashable {
            let summary: String
            let focus: String
            let advice: [String]
            let direction: String
            let risks: [String]
            let askNext: [String]

            enum CodingKeys: String, CodingKey {
                case summary, focus, advice, direction, risks, askNext
            }

            init(
                summary: String,
                focus: String,
                advice: [String],
                direction: String = "",
                risks: [String] = [],
                askNext: [String] = []
            ) {
                self.summary = summary
                self.focus = focus
                self.advice = advice
                self.direction = direction
                self.risks = risks
                self.askNext = askNext
            }

            init(from decoder: Decoder) throws {
                let container = try decoder.container(keyedBy: CodingKeys.self)
                summary = try container.decode(String.self, forKey: .summary)
                focus = try container.decode(String.self, forKey: .focus)
                advice = try container.decode([String].self, forKey: .advice)
                direction = try container.decodeIfPresent(String.self, forKey: .direction) ?? ""
                risks = try container.decodeIfPresent([String].self, forKey: .risks) ?? []
                askNext = try container.decodeIfPresent([String].self, forKey: .askNext) ?? []
            }

            init(saved: SavedAIContent) {
                self.init(
                    summary: saved.summary,
                    focus: saved.focus,
                    advice: saved.advice,
                    direction: saved.direction,
                    risks: saved.risks,
                    askNext: saved.askNext
                )
            }

            func savedContent() -> SavedAIContent {
                SavedAIContent(
                    summary: summary,
                    focus: focus,
                    advice: advice,
                    direction: direction,
                    risks: risks,
                    askNext: askNext
                )
            }

            func previousAnalysisPayload() -> [String: Any] {
                [
                    "summary": summary,
                    "focus": focus,
                    "advice": advice,
                    "direction": direction,
                    "risks": risks,
                    "askNext": askNext,
                ]
            }
        }

        let ok: Bool
        let analysis: Analysis
    }

    struct AIFollowupResponse: Decodable {
        let ok: Bool
        let reply: String
        let advice: [String]
        let askNext: [String]

        enum CodingKeys: String, CodingKey {
            case ok, reply, advice, askNext
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            ok = try container.decode(Bool.self, forKey: .ok)
            reply = try container.decode(String.self, forKey: .reply)
            advice = try container.decodeIfPresent([String].self, forKey: .advice) ?? []
            askNext = try container.decodeIfPresent([String].self, forKey: .askNext) ?? []
        }
    }

    static func analyzeReading(result: CastResult, accessToken: String) async throws -> AIAnalyzeResponse {
        var payload: [String: Any] = [
            "method": result.method.rawValue,
            "primaryNumber": result.primaryNumber,
            "movingPositions": result.movingPositions,
            "lines": result.lines.map(\.rawValue),
            "hexTextVersion": "yi-zhengshi-2026-08",
            "uiLanguage": AppLanguage.current.isEnglish ? "en" : "zh",
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
        let data = try await perform(req, fallback: "解读失败".ui("Couldn’t generate a reading"))
        let decoded = try JSONDecoder().decode(AIAnalyzeResponse.self, from: data)
        guard decoded.ok else { throw LoginError.network("解读失败".ui("Couldn’t generate a reading")) }
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
            "uiLanguage": AppLanguage.current.isEnglish ? "en" : "zh",
            "message": message,
            "previousAnalysis": analysis.previousAnalysisPayload(),
            "conversation": conversation.map {
                [
                    "user": $0.user,
                    "assistant": $0.assistant,
                    "advice": $0.advice,
                ]
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
        let data = try await perform(req, fallback: "追问失败".ui("Couldn’t send the follow-up"))
        let decoded = try JSONDecoder().decode(AIFollowupResponse.self, from: data)
        guard decoded.ok, !decoded.reply.isEmpty else { throw LoginError.network("追问失败".ui("Couldn’t send the follow-up")) }
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
            throw LoginError.network("请求超时，请稍后重试".ui("Timed out. Please try again."))
        }
        guard let http = response as? HTTPURLResponse else { throw LoginError.network("网络异常".ui("Network error")) }
        guard (200..<300).contains(http.statusCode) else { throw decodeError(data, fallback: fallback) }
        return data
    }

    private static func decodeError(_ data: Data, fallback: String) -> LoginError {
        if let envelope = try? JSONDecoder().decode(ErrorEnvelope.self, from: data) {
            let message = (envelope.message?.isEmpty == false) ? envelope.message! : fallback
            if envelope.code == 4290 {
                return .rateLimited(message: message, dailyDone: message.contains("明天再来"))
            }
            if envelope.message?.isEmpty == false {
                return .network(message)
            }
        }
        return .network(fallback)
    }
}

enum LoginError: LocalizedError {
    case network(String)
    case unauthorized
    case rateLimited(message: String, dailyDone: Bool)

    var errorDescription: String? {
        switch self {
        case .network(let message): return message
        case .rateLimited(let message, _): return message
        case .unauthorized: return "登录已过期，请重新登录".ui("Session expired. Please sign in again.")
        }
    }

    static func isDailyQuotaExhausted(_ error: Error) -> Bool {
        if case .rateLimited(_, true) = error as? LoginError {
            return true
        }
        return false
    }

    static func describe(_ error: Error) -> String {
        if let login = error as? LoginError, let text = login.errorDescription {
            return text
        }
        if let url = error as? URLError {
            switch url.code {
            case .timedOut:
                return "连接超时：\(AuthAPI.debugEndpoint)".ui("Timed out: \(AuthAPI.debugEndpoint)")
            case .cannotConnectToHost, .cannotFindHost, .networkConnectionLost, .notConnectedToInternet:
                return "连不上 \(AuthAPI.debugEndpoint)".ui("Can’t reach \(AuthAPI.debugEndpoint)")
            default:
                return "网络异常（\(url.code.rawValue)）：\(AuthAPI.debugEndpoint)".ui("Network error (\(url.code.rawValue)): \(AuthAPI.debugEndpoint)")
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
                VStack(spacing: 8) {
                    Spacer()
                    Text("还没有解读".ui("No readings yet"))
                        .font(.headline)
                    Text("起卦后点问，解读会自动出现在这里".ui("After you cast, tap Ask. Readings appear here."))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(items) { item in
                        NavigationLink {
                            AIAnalysisView(saved: item, opensResultOnHeaderTap: true)
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
                            .accessibilityLabel("删除".ui("Delete"))
                        }
                    }
                }
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("问答".ui("Readings"))
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground(hidesTabBar: false)
        .onAppear {
            items = SavedAIAnalysisStore.load()
        }
        .onReceive(NotificationCenter.default.publisher(for: .savedAIAnalysesDidChange)) { _ in
            items = SavedAIAnalysisStore.load()
        }
    }

    @ViewBuilder
    private func savedRow(_ item: SavedAIAnalysis) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                if let hex = store.hexagram(number: item.primaryNumber) {
                    Text(hex.listLabel)
                        .font(.headline)
                        .lineLimit(1)
                } else {
                    Text("第\(item.primaryNumber)卦".ui("Hexagram \(item.primaryNumber)"))
                        .font(.headline)
                        .lineLimit(1)
                }
                if let resulting = item.resultingNumber {
                    HexagramChangeArrow(
                        movingLabel: ReadingRecordRow.digitalMovingLabel(
                            method: item.method,
                            movingPositions: item.movingPositions
                        )
                    )
                    if let hex = store.hexagram(number: resulting) {
                        Text(hex.listLabel)
                            .font(.headline)
                            .lineLimit(1)
                    } else {
                        Text("第\(resulting)卦".ui("Hexagram \(resulting)"))
                            .font(.headline)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
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

private struct FeedbackView: View {
    let session: LocalUserSession
    @Environment(\.dismiss) private var dismiss
    @State private var bodyDraft = ""
    @State private var contactDraft = ""
    @State private var isSubmitting = false
    @State private var showSuccess = false
    @State private var errorMessage: String?

    private var trimmedBody: String {
        bodyDraft.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSubmit: Bool {
        trimmedBody.count >= 5 && !isSubmitting
    }

    var body: some View {
        List {
            Section {
                TextField("想说的话（至少 5 个字）", text: $bodyDraft, axis: .vertical)
                    .lineLimit(6...12)
                    .appTextFieldStyle()
            } header: {
                Text("意见".ui("Feedback"))
            } footer: {
                Text("\(trimmedBody.count)/2000".zh)
                    .foregroundStyle(.secondary)
            }

            Section {
                TextField("邮箱或其它联系方式（选填）", text: $contactDraft)
                    .appTextFieldStyle()
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)
            } header: {
                Text("联系方式".ui("Contact (optional)"))
            } footer: {
                Text(
                    (session.isLoggedIn
                     ? "已登录时会带上账号，方便我们对照。"
                     : "未登录也可以提交。留下联系方式，有进展时方便回你。").zh
                )
            }
        }
        .navigationTitle("意见反馈".ui("Feedback"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("提交".ui("Send")) {
                    Task { await submit() }
                }
                .disabled(!canSubmit)
            }
        }
        .parchmentBackground()
        .onAppear {
            if contactDraft.isEmpty {
                contactDraft = session.email ?? session.phone ?? ""
            }
        }
        .alert("已收到".ui("Received"), isPresented: $showSuccess) {
            Button("好的".ui("OK")) { dismiss() }
        } message: {
            Text("感谢反馈，我们会尽快查看。".ui("Thank you. We’ll look at this soon."))
        }
        .alert("提交失败".ui("Couldn’t send"), isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("知道了".ui("OK"), role: .cancel) {}
        } message: {
            Text((errorMessage ?? "").zh)
        }
    }

    @MainActor
    private func submit() async {
        let bodyText = String(trimmedBody.prefix(2000))
        guard bodyText.count >= 5, !isSubmitting else { return }
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            try await AuthAPI.submitFeedback(
                body: bodyText,
                contact: contactDraft.trimmingCharacters(in: .whitespacesAndNewlines),
                accessToken: session.accessToken
            )
            showSuccess = true
        } catch {
            errorMessage = LoginError.describe(error)
        }
    }
}

private struct SettingsView: View {
    @Binding var session: LocalUserSession
    @Environment(\.dismiss) private var dismiss
    @State private var showLogoutConfirm = false
    @State private var showDeleteConfirm = false
    @State private var isDeletingAccount = false
    @State private var deleteErrorMessage: String?
    @State private var recycleCount = HistoryTrashStore.load().count

    var body: some View {
        List {
            Section {
                NavigationLink {
                    RecycleBinView()
                } label: {
                    HStack {
                        Label("回收站".ui("Trash"), systemImage: "trash")
                        Spacer()
                        Text("\(recycleCount)".zh)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                NavigationLink {
                    LegalDocumentView(title: "隐私政策".ui("Privacy Policy"), file: "privacy_policy")
                } label: {
                    Label("隐私政策".ui("Privacy Policy"), systemImage: "lock.shield")
                }
                NavigationLink {
                    LegalDocumentView(title: "用户协议".ui("Terms of Use"), file: "terms_of_service")
                } label: {
                    Label("用户协议".ui("Terms of Use"), systemImage: "doc.text")
                }
            }

            if session.isLoggedIn {
                Section {
                    Button("退出登录".ui("Sign Out")) {
                        showLogoutConfirm = true
                    }
                    Button("注销账号".ui("Delete Account"), role: .destructive) {
                        showDeleteConfirm = true
                    }
                    .disabled(isDeletingAccount)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("设置".ui("Settings"))
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .onAppear {
            recycleCount = HistoryTrashStore.load().count
        }
        .alert("确认退出登录？".ui("Sign out?"), isPresented: $showLogoutConfirm) {
            Button("取消".ui("Cancel"), role: .cancel) {}
            Button("退出登录".ui("Sign Out")) {
                ProfileAvatarFile.clear()
                session = .guest
                LocalAuthStore.save(session)
                UnlockStore.shared.clearLocal()
                dismiss()
            }
        }
        .alert("确认注销账号？".ui("Delete this account?"), isPresented: $showDeleteConfirm) {
            Button("取消".ui("Cancel"), role: .cancel) {}
            Button("注销账号".ui("Delete Account"), role: .destructive) {
                Task { await deleteAccount() }
            }
        } message: {
            Text("注销后，服务器上的账号信息将被永久删除且不可恢复。设备本地的起卦记录与保存的问答不会自动清除。".ui("This permanently deletes your account on the server. Casts and readings saved on this device stay until you remove them."))
        }
        .alert("注销失败".ui("Couldn’t delete account"), isPresented: Binding(
            get: { deleteErrorMessage != nil },
            set: { if !$0 { deleteErrorMessage = nil } }
        )) {
            Button("知道了".ui("OK"), role: .cancel) {}
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
            ProfileAvatarFile.clear()
            session = .guest
            LocalAuthStore.save(session)
            UnlockStore.shared.clearLocal()
            dismiss()
        } catch {
            deleteErrorMessage = LoginError.describe(error)
        }
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
                    description: Text("删除的记录会先放在这里，可恢复".ui("Deleted casts wait here until you restore or erase them"))
                )
            } else {
                List {
                    ForEach(entries.sorted(by: { $0.deletedAt > $1.deletedAt })) { entry in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                if let hex = store.hexagram(number: entry.primaryNumber) {
                                    Text(hex.listLabel)
                                        .font(.headline)
                                } else {
                                    Text("第\(entry.primaryNumber)卦".ui("Hexagram \(entry.primaryNumber)"))
                                        .font(.headline)
                                }
                                if let resulting = entry.resultingNumber {
                                    Text("→".zh)
                                        .foregroundStyle(.secondary)
                                    if let hex = store.hexagram(number: resulting) {
                                        Text(hex.listLabel)
                                            .font(.headline)
                                    } else {
                                        Text("第\(resulting)卦".ui("Hexagram \(resulting)"))
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
                            Button("恢复".ui("Restore")) {
                                modelContext.insert(entry.toReadingRecord())
                                try? modelContext.save()
                                HistoryTrashStore.remove(entryID: entry.id)
                                entries = HistoryTrashStore.load()
                            }
                            .tint(.green)

                            Button("彻底删除".ui("Delete forever"), role: .destructive) {
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
        .navigationTitle("回收站".ui("Trash"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !entries.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("清空".ui("Empty")) {
                        showClearConfirm = true
                    }
                    .tint(.red)
                }
            }
        }
        .alert("确认清空？".ui("Empty the trash?"), isPresented: $showClearConfirm) {
            Button("取消".ui("Cancel"), role: .cancel) {}
            Button("确定".ui("Empty"), role: .destructive) {
                HistoryTrashStore.clearAll()
                entries = []
            }
        } message: {
            Text("回收站中的记录将被彻底删除，无法恢复。".ui("These records will be permanently deleted."))
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
        case .terms: return "用户协议".ui("Terms of Use")
        case .privacy: return "隐私政策".ui("Privacy Policy")
        }
    }
    var file: String {
        let base: String
        switch self {
        case .terms: base = "terms_of_service"
        case .privacy: base = "privacy_policy"
        }
        return AppLanguage.current.isEnglish ? "\(base).en" : base
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
    var hidesTabBar: Bool = true

    var body: some View {
        LegalWebView(fileName: file)
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(hidesTabBar ? .hidden : .automatic, for: .tabBar)
    }
}
