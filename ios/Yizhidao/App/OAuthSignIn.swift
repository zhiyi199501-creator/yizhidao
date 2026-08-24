import AuthenticationServices
import GoogleSignIn
import SwiftUI
import UIKit

enum OAuthSignIn {
    static func configureGoogleIfNeeded() {
        guard AuthConfig.isGoogleConfigured else { return }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: AuthConfig.googleClientID)
    }

    @MainActor
    static func signInWithGoogle() async throws -> String {
        guard AuthConfig.isGoogleConfigured else {
            throw LoginError.network("Google 登录未配置")
        }
        configureGoogleIfNeeded()
        guard let presenter = UIApplication.shared.topViewController else {
            throw LoginError.network("无法打开 Google 登录")
        }
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        guard let idToken = result.user.idToken?.tokenString else {
            throw LoginError.network("Google 登录失败")
        }
        return idToken
    }
}

struct AppleSignInButton: View {
    let onSuccess: (String, String?) -> Void
    let onError: (Error) -> Void

    var body: some View {
        SignInWithAppleButton(.signIn) { request in
            request.requestedScopes = [.fullName, .email]
        } onCompletion: { result in
            switch result {
            case .success(let authorization):
                guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                      let tokenData = credential.identityToken,
                      let token = String(data: tokenData, encoding: .utf8)
                else {
                    onError(LoginError.network("Apple 登录失败"))
                    return
                }
                let fullName: String? = {
                    guard let name = credential.fullName else { return nil }
                    let parts = [name.familyName, name.givenName]
                        .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                        .filter { !$0.isEmpty }
                    return parts.isEmpty ? nil : parts.joined()
                }()
                onSuccess(token, fullName)
            case .failure(let error):
                let nsError = error as NSError
                if nsError.domain == ASAuthorizationError.errorDomain,
                   nsError.code == ASAuthorizationError.canceled.rawValue {
                    return
                }
                onError(error)
            }
        }
        .signInWithAppleButtonStyle(.black)
        .frame(height: 44)
    }
}

private extension UIApplication {
    var topViewController: UIViewController? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .rootViewController?
            .topMostViewController
    }
}

private extension UIViewController {
    var topMostViewController: UIViewController {
        if let presented = presentedViewController {
            return presented.topMostViewController
        }
        if let nav = self as? UINavigationController, let visible = nav.visibleViewController {
            return visible.topMostViewController
        }
        if let tab = self as? UITabBarController, let selected = tab.selectedViewController {
            return selected.topMostViewController
        }
        return self
    }
}
