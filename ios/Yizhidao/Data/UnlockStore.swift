import Foundation
import StoreKit

@Observable
@MainActor
final class UnlockStore {
    static let shared = UnlockStore()
    static let productID = "com.yizhidao.app.ai.unlock"

    private static let cacheKey = "iap.unlocked.v1"

    var isUnlocked: Bool
    var isUnlimited = false
    var dailyLimit = 3
    var dailyUsed = 0
    var dailyRemaining = 3
    var product: Product?
    var isBusy = false
    var errorMessage: String?
    var statusMessage: String?

    var displayPrice: String {
        product?.displayPrice ?? "$9.99"
    }

    private var updatesTask: Task<Void, Never>?

    private init() {
        isUnlocked = UserDefaults.standard.bool(forKey: Self.cacheKey)
    }

    func start() {
        guard updatesTask == nil else { return }
        updatesTask = Task { [weak self] in
            for await result in Transaction.updates {
                await self?.submit(result)
            }
        }
        Task {
            for await result in Transaction.unfinished {
                await submit(result)
            }
        }
        Task { await loadProduct() }
        Task { await refreshFromServer() }
    }

    func applyQuota(unlocked: Bool?, unlimited: Bool? = nil, limit: Int?, used: Int?, remaining: Int?) {
        if let unlocked {
            setUnlocked(unlocked)
        }
        if let unlimited {
            isUnlimited = unlimited
        }
        if isUnlimited {
            if let limit, limit > 0 {
                dailyLimit = limit
            } else if dailyLimit < 1 {
                dailyLimit = 30
            }
            if let used { dailyUsed = max(used, 0) }
            dailyRemaining = remaining.map { max($0, 0) } ?? 1_000_000_000
            return
        }
        if let limit {
            // 0 曾被当成不限次哨兵；勿再 max(..., 1) 显示成每天 1 次
            dailyLimit = limit > 0 ? limit : 3
        }
        if let used {
            dailyUsed = max(used, 0)
        }
        if let remaining {
            dailyRemaining = max(remaining, 0)
        } else {
            dailyRemaining = max(dailyLimit - dailyUsed, 0)
        }
    }

    func clearLocal() {
        setUnlocked(false)
        isUnlimited = false
        dailyLimit = 3
        dailyUsed = 0
        dailyRemaining = 3
        errorMessage = nil
        statusMessage = nil
    }

    func loadProduct() async {
        do {
            let products = try await Product.products(for: [Self.productID])
            product = products.first
        } catch {
            if product == nil {
                errorMessage = LoginError.describe(error)
            }
        }
    }

    func refreshFromServer() async {
        guard let token = LocalAuthStore.load().accessToken, !token.isEmpty else { return }
        do {
            let me = try await AuthAPI.fetchMe(accessToken: token)
            applyQuota(
                unlocked: me.user.iapUnlocked,
                unlimited: me.user.aiUnlimited,
                limit: me.user.aiDailyLimit,
                used: me.user.aiDailyUsed,
                remaining: me.user.aiDailyRemaining
            )
        } catch {
            // 离线时保留本地缓存
        }
    }

    func purchase() async -> Bool {
        errorMessage = nil
        statusMessage = nil
        guard LocalAuthStore.load().isLoggedIn, let token = LocalAuthStore.load().accessToken, !token.isEmpty else {
            errorMessage = "请先登录".ui("Please sign in first")
            return false
        }
        if product == nil {
            await loadProduct()
        }
        guard let product else {
            errorMessage = "暂时无法读取价格".ui("Couldn’t load the price")
            return false
        }
        isBusy = true
        defer { isBusy = false }
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                return await submit(verification, accessToken: token)
            case .userCancelled:
                return false
            case .pending:
                statusMessage = "购买处理中，请稍后在解锁页点恢复购买。".ui("Purchase pending. Restore it later on this page.")
                return false
            @unknown default:
                return false
            }
        } catch {
            errorMessage = LoginError.describe(error)
            return false
        }
    }

    func restore() async -> Bool {
        errorMessage = nil
        statusMessage = nil
        guard LocalAuthStore.load().isLoggedIn, let token = LocalAuthStore.load().accessToken, !token.isEmpty else {
            errorMessage = "请先登录".ui("Please sign in first")
            return false
        }
        isBusy = true
        defer { isBusy = false }
        do {
            if await submitCurrentEntitlements(accessToken: token) {
                statusMessage = "已恢复购买".ui("Purchase restored")
                return true
            }
            try await AppStore.sync()
            if await submitCurrentEntitlements(accessToken: token) {
                statusMessage = "已恢复购买".ui("Purchase restored")
                return true
            }
            errorMessage = "没有可恢复的购买".ui("No purchase to restore")
            return false
        } catch {
            errorMessage = LoginError.describe(error)
            return false
        }
    }

    private func submitCurrentEntitlements(accessToken: String) async -> Bool {
        var found = false
        for await result in Transaction.currentEntitlements {
            if await submit(result, accessToken: accessToken) {
                found = true
            }
        }
        return found
    }

    @discardableResult
    private func submit(_ result: VerificationResult<Transaction>, accessToken: String? = nil) async -> Bool {
        let token = accessToken ?? LocalAuthStore.load().accessToken
        guard let token, !token.isEmpty else { return false }
        let transaction: Transaction
        switch result {
        case .verified(let value):
            transaction = value
        case .unverified(let value, _):
            transaction = value
        }
        guard transaction.productID == Self.productID else { return false }
        do {
            let response = try await AuthAPI.verifyIAP(
                signedTransaction: result.jwsRepresentation,
                accessToken: token
            )
            if response.unlocked {
                setUnlocked(true)
                await transaction.finish()
                await refreshFromServer()
                return true
            }
            return false
        } catch {
            errorMessage = LoginError.describe(error)
            return false
        }
    }

    private func setUnlocked(_ unlocked: Bool) {
        isUnlocked = unlocked
        UserDefaults.standard.set(unlocked, forKey: Self.cacheKey)
    }
}
