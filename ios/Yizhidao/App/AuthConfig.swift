import Foundation

enum AuthConfig {
    /// Google Cloud Console → iOS OAuth Client ID（形如 `123456-abc.apps.googleusercontent.com`）。
    /// 配置后还需在 Info.plist 的 `CFBundleURLSchemes` 填入对应的 reversed client id。
    static let googleClientID = ""

    static var isGoogleConfigured: Bool {
        let trimmed = googleClientID.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && !trimmed.hasPrefix("YOUR_")
    }
}
