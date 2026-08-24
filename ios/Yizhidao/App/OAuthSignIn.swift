import AuthenticationServices
import SwiftUI
import UIKit

enum AppleSignIn {
    @MainActor
    static func signIn() async throws -> (identityToken: String, fullName: String?) {
        try await withCheckedThrowingContinuation { continuation in
            let coordinator = AppleSignInCoordinator(continuation: continuation)
            coordinator.start()
        }
    }
}

private final class AppleSignInCoordinator: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<(identityToken: String, fullName: String?), Error>?
    private var retainSelf: AppleSignInCoordinator?

    init(continuation: CheckedContinuation<(identityToken: String, fullName: String?), Error>) {
        self.continuation = continuation
        super.init()
        self.retainSelf = self
    }

    func start() {
        let provider = ASAuthorizationAppleIDProvider()
        let request = provider.createRequest()
        request.requestedScopes = [.fullName, .email]
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
            ?? ASPresentationAnchor()
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        defer { retainSelf = nil }
        guard let continuation else { return }
        self.continuation = nil

        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let token = String(data: tokenData, encoding: .utf8)
        else {
            continuation.resume(throwing: LoginError.network("Apple 登录失败"))
            return
        }
        let fullName: String? = {
            guard let name = credential.fullName else { return nil }
            let parts = [name.familyName, name.givenName]
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            return parts.isEmpty ? nil : parts.joined()
        }()
        continuation.resume(returning: (token, fullName))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        defer { retainSelf = nil }
        guard let continuation else { return }
        self.continuation = nil
        let nsError = error as NSError
        if nsError.domain == ASAuthorizationError.errorDomain,
           nsError.code == ASAuthorizationError.canceled.rawValue {
            continuation.resume(throwing: CancellationError())
            return
        }
        continuation.resume(throwing: error)
    }
}
