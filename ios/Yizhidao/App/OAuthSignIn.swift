import AuthenticationServices
import SwiftUI

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
