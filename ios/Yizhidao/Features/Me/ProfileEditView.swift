import PhotosUI
import SwiftUI
import UIKit

struct ProfileAvatarView: View {
    let name: String
    var image: UIImage?
    var size: CGFloat = 88
    var showsCamera = false

    private var letter: String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = trimmed.first else { return "?" }
        return String(first).uppercased()
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                } else {
                    Text(letter.zh)
                        .font(.system(size: size * 0.42, weight: .semibold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(ProfileAvatarPalette.gradient(for: name))
                }
            }
            .frame(width: size, height: size)
            .clipShape(Circle())

            if showsCamera {
                Image(systemName: "camera.fill")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color(red: 0.22, green: 0.48, blue: 0.96))
                    .frame(width: 26, height: 26)
                    .background(Circle().fill(Color.white))
                    .overlay(Circle().stroke(Color.black.opacity(0.06), lineWidth: 0.5))
                    .offset(x: 2, y: 2)
            }
        }
        .frame(width: size, height: size)
    }
}

enum ProfileAvatarPalette {
    static func gradient(for name: String) -> LinearGradient {
        let palettes: [(Color, Color)] = [
            (Color(red: 0.62, green: 0.42, blue: 0.92), Color(red: 0.95, green: 0.48, blue: 0.68)),
            (Color(red: 0.38, green: 0.52, blue: 0.90), Color(red: 0.55, green: 0.78, blue: 0.92)),
            (Color(red: 0.90, green: 0.48, blue: 0.38), Color(red: 0.96, green: 0.72, blue: 0.42)),
            (Color(red: 0.28, green: 0.62, blue: 0.58), Color(red: 0.48, green: 0.80, blue: 0.62)),
        ]
        let index = abs(name.hashValue) % palettes.count
        let pair = palettes[index]
        return LinearGradient(colors: [pair.0, pair.1], startPoint: .top, endPoint: .bottom)
    }
}

enum ProfileAvatarFile {
    static let fileName = "profile-avatar.jpg"

    static var url: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(fileName)
    }

    static func load() -> UIImage? {
        UIImage(contentsOfFile: url.path)
    }

    static func save(_ data: Data) {
        try? data.write(to: url, options: .atomic)
    }

    static func clear() {
        try? FileManager.default.removeItem(at: url)
    }
}

enum ProfileSync {
    @MainActor
    static func pullAvatar(
        session: LocalUserSession,
        user: AuthAPI.AccountUser,
        accessToken: String
    ) async -> LocalUserSession {
        var next = session
        if user.hasAvatar == true {
            if user.avatarUpdatedAt == next.avatarUpdatedAt,
               next.avatarImagePath != nil,
               ProfileAvatarFile.load() != nil {
                return next
            }
            guard let data = try? await AuthAPI.fetchAvatar(accessToken: accessToken) else { return next }
            ProfileAvatarFile.save(data)
            next.avatarImagePath = ProfileAvatarFile.fileName
            next.avatarUpdatedAt = user.avatarUpdatedAt
            return next
        }
        if next.avatarImagePath != nil,
           let image = ProfileAvatarFile.load(),
           let jpeg = image.jpegData(compressionQuality: 0.86),
           let me = try? await AuthAPI.uploadAvatar(jpeg, accessToken: accessToken) {
            next = applyRemoteUser(me.user, to: next)
            if me.user.hasAvatar == true {
                next.avatarImagePath = ProfileAvatarFile.fileName
            }
        }
        return next
    }

    @MainActor
    static func applyRemoteUser(_ user: AuthAPI.AccountUser, to session: LocalUserSession) -> LocalUserSession {
        var next = session
        next.displayName = user.nickname
        next.email = user.email ?? next.email
        next.avatarUpdatedAt = user.avatarUpdatedAt
        return next
    }
}

struct ProfileEditView: View {
    @Binding var session: LocalUserSession
    @Environment(\.dismiss) private var dismiss
    @State private var nicknameDraft: String
    @State private var avatarImage: UIImage?
    @State private var photoItem: PhotosPickerItem?
    @State private var saveTask: Task<Void, Never>?
    @State private var bindEmailDraft = ""
    @State private var bindCodeDraft = ""
    @State private var bindCooldownSec = 0
    @State private var isSendingBindCode = false
    @State private var isBindingEmail = false
    @State private var bindMessage: String?
    @State private var showLogoutConfirm = false

    init(session: Binding<LocalUserSession>) {
        _session = session
        _nicknameDraft = State(initialValue: session.wrappedValue.displayName)
        _avatarImage = State(initialValue: session.wrappedValue.avatarImagePath == nil ? nil : ProfileAvatarFile.load())
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                profileCard
                accountCard
                logoutButton
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 36)
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("个人资料".ui("Profile"))
        .navigationBarTitleDisplayMode(.inline)
        .parchmentBackground()
        .dismissKeyboardOnBlankTap()
        .onChange(of: nicknameDraft) { _, _ in scheduleSave() }
        .onChange(of: photoItem) { _, item in
            Task { await applyPhoto(item) }
        }
        .onDisappear {
            saveTask?.cancel()
            persistNow(force: true)
        }
        .alert("确认退出登录？".ui("Sign out?"), isPresented: $showLogoutConfirm) {
            Button("取消".ui("Cancel"), role: .cancel) {}
            Button("退出登录".ui("Sign Out")) {
                signOut()
            }
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            PhotosPicker(selection: $photoItem, matching: .images) {
                ProfileAvatarView(
                    name: nicknameDraft,
                    image: avatarImage,
                    size: 88,
                    showsCamera: true
                )
            }
            .buttonStyle(.plain)

            Text(nicknameDraft.zh)
                .font(.title3.weight(.semibold))
                .foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 8)
        .padding(.bottom, 4)
    }

    private var profileCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionLabel("资料".ui("Details"))
            VStack(spacing: 0) {
                profileRow(title: "昵称".ui("Name")) {
                    TextField("昵称".ui("Name"), text: $nicknameDraft)
                        .multilineTextAlignment(.trailing)
                        .textInputAutocapitalization(.never)
                        .textFieldStyle(.plain)
                        .foregroundStyle(.secondary)
                }
            }
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.white.opacity(0.92))
            )
        }
    }

    private var hasBoundEmail: Bool {
        guard let email = session.email else { return false }
        return !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var accountCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionLabel("账户".ui("Account"))
            VStack(spacing: 0) {
                if hasBoundEmail {
                    profileRow(title: "邮箱".ui("Email")) {
                        Text((session.email ?? "").zh)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                } else {
                    emailBindRows
                }
                if let phone = session.phone, !phone.isEmpty {
                    divider
                    profileRow(title: "手机".ui("Phone")) {
                        Text(phone.zh)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color.white.opacity(0.92))
            )
        }
    }

    private var emailBindRows: some View {
        VStack(spacing: 0) {
            profileRow(title: "邮箱".ui("Email")) {
                TextField("绑定后可邮箱登录".ui("Link to sign in with email"), text: $bindEmailDraft)
                    .multilineTextAlignment(.trailing)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)
                    .textFieldStyle(.plain)
                    .foregroundStyle(.secondary)
                    .autocorrectionDisabled()
            }
            divider
            profileRow(title: "验证码".ui("Code")) {
                HStack(spacing: 8) {
                    TextField("验证码".ui("Code"), text: $bindCodeDraft)
                        .multilineTextAlignment(.trailing)
                        .keyboardType(.numberPad)
                        .textFieldStyle(.plain)
                        .foregroundStyle(.secondary)
                    Button(bindCooldownSec > 0 ? "\(bindCooldownSec)s" : "发送".ui("Send")) {
                        Task { await sendBindCode() }
                    }
                    .buttonStyle(.plain)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(canSendBindCode ? AppTheme.accent : Color.secondary.opacity(0.5))
                    .disabled(!canSendBindCode)
                }
            }
            divider
            Button {
                Task { await bindEmailNow() }
            } label: {
                HStack {
                    Spacer()
                    if isBindingEmail {
                        ProgressView()
                    } else {
                        Text("绑定邮箱".ui("Link email"))
                            .foregroundStyle(AppTheme.accent)
                    }
                    Spacer()
                }
                .padding(.vertical, 13)
            }
            .buttonStyle(.plain)
            .disabled(isBindingEmail || !canBindEmail)
            if let bindMessage {
                Text(bindMessage.zh)
                    .font(.footnote)
                    .foregroundStyle(bindMessage == "验证码已发送".ui("Code sent") ? Color.secondary : Color.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 10)
            }
        }
    }

    private var canSendBindCode: Bool {
        !isSendingBindCode && bindCooldownSec == 0 && isValidEmail(bindEmailDraft)
    }

    private var canBindEmail: Bool {
        isValidEmail(bindEmailDraft) && !bindCodeDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func isValidEmail(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.contains("@"), trimmed.contains(".") else { return false }
        return trimmed.count >= 5
    }

    @MainActor
    private func sendBindCode() async {
        let trimmed = bindEmailDraft.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard isValidEmail(trimmed) else {
            bindMessage = "请输入正确邮箱".ui("Enter a valid email")
            return
        }
        guard let token = session.accessToken, !token.isEmpty else { return }
        isSendingBindCode = true
        defer { isSendingBindCode = false }
        do {
            let resp = try await AuthAPI.sendBindEmailCode(email: trimmed, accessToken: token)
            bindCooldownSec = max(resp.cooldownSec, 0)
            bindMessage = "验证码已发送".ui("Code sent")
            startBindCooldown()
        } catch {
            bindMessage = LoginError.describe(error)
        }
    }

    @MainActor
    private func bindEmailNow() async {
        let trimmedEmail = bindEmailDraft.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let trimmedCode = bindCodeDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidEmail(trimmedEmail), !trimmedCode.isEmpty else {
            bindMessage = "请输入邮箱和验证码".ui("Enter email and code")
            return
        }
        guard let token = session.accessToken, !token.isEmpty else { return }
        isBindingEmail = true
        defer { isBindingEmail = false }
        do {
            let me = try await AuthAPI.bindEmail(email: trimmedEmail, code: trimmedCode, accessToken: token)
            session = ProfileSync.applyRemoteUser(me.user, to: session)
            session.email = me.user.email ?? trimmedEmail
            LocalAuthStore.save(session)
            bindMessage = nil
        } catch {
            bindMessage = LoginError.describe(error)
        }
    }

    private func startBindCooldown() {
        guard bindCooldownSec > 0 else { return }
        Task { @MainActor in
            while bindCooldownSec > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                bindCooldownSec -= 1
            }
        }
    }

    private var logoutButton: some View {
        Button {
            showLogoutConfirm = true
        } label: {
            Text("退出登录".ui("Sign Out"))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .foregroundStyle(.primary)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(Color.white.opacity(0.92))
                )
        }
        .buttonStyle(.plain)
        .padding(.top, 8)
    }

    private func signOut() {
        saveTask?.cancel()
        ProfileAvatarFile.clear()
        session = .guest
        LocalAuthStore.save(session)
        UnlockStore.shared.clearLocal()
        dismiss()
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.zh)
            .font(.footnote)
            .foregroundStyle(.secondary)
            .padding(.leading, 4)
            .padding(.bottom, 8)
    }

    private var divider: some View {
        Rectangle()
            .fill(Color.black.opacity(0.06))
            .frame(height: 0.5)
            .padding(.leading, 16)
    }

    private func profileRow<Content: View>(
        title: String,
        @ViewBuilder value: () -> Content
    ) -> some View {
        HStack(spacing: 12) {
            Text(title.zh)
                .foregroundStyle(.primary)
            Spacer(minLength: 8)
            value()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
        .contentShape(Rectangle())
    }

    private func scheduleSave() {
        saveTask?.cancel()
        saveTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 700_000_000)
            guard !Task.isCancelled else { return }
            persistNow(force: false)
        }
    }

    private func persistNow(force: Bool) {
        guard session.isLoggedIn else { return }
        let trimmed = nicknameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let limited = String(trimmed.prefix(20))
        if !(2...20).contains(limited.count) {
            if force {
                nicknameDraft = session.displayName
            }
            return
        }
        nicknameDraft = limited
        var next = session
        next.displayName = limited
        next.avatarImagePath = avatarImage == nil ? nil : ProfileAvatarFile.fileName
        session = next
        LocalAuthStore.save(next)
        guard let token = session.accessToken, !token.isEmpty else { return }
        Task {
            guard let me = try? await AuthAPI.updateMe(nickname: limited, accessToken: token) else { return }
            await MainActor.run {
                session = ProfileSync.applyRemoteUser(me.user, to: session)
                LocalAuthStore.save(session)
            }
        }
    }

    @MainActor
    private func applyPhoto(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        guard let data = try? await item.loadTransferable(type: Data.self) else { return }
        guard let image = UIImage(data: data) else { return }
        let jpeg = image.jpegData(compressionQuality: 0.86) ?? data
        ProfileAvatarFile.save(jpeg)
        avatarImage = UIImage(data: jpeg) ?? image
        session.avatarImagePath = ProfileAvatarFile.fileName
        LocalAuthStore.save(session)
        guard let token = session.accessToken, !token.isEmpty else { return }
        if let me = try? await AuthAPI.uploadAvatar(jpeg, accessToken: token) {
            var next = ProfileSync.applyRemoteUser(me.user, to: session)
            if me.user.hasAvatar == true {
                next.avatarImagePath = ProfileAvatarFile.fileName
            }
            session = next
            LocalAuthStore.save(session)
        }
    }
}
