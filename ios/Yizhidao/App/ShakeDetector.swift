#if canImport(CoreMotion)
import CoreMotion
#endif
import Foundation

/// 摇一摇。走 CoreMotion 加速度计，不去抢 first responder，
/// 免得和起卦页的输入框抢焦点（iPhone 11 上那条路很容易出问题）。
/// 加速度计不属于需要授权的 API，也不用写 Info.plist 用途说明。
final class ShakeDetector {
    /// 摇动阈值（g）。静置时合加速度约等于 1。
    private static let threshold: Double = 2.3
    /// 两次触发之间的冷却，避免一次挥手连出好几爻。
    private static let cooldown: TimeInterval = 1.2

    var onShake: (() -> Void)?

    #if canImport(CoreMotion)
    private let motion = CMMotionManager()
    #endif
    private var lastFiredAt: Date = .distantPast

    func start() {
        #if canImport(CoreMotion)
        guard motion.isAccelerometerAvailable, !motion.isAccelerometerActive else { return }
        motion.accelerometerUpdateInterval = 1.0 / 30.0
        motion.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
            guard let self, let acceleration = data?.acceleration else { return }
            let magnitude = sqrt(
                acceleration.x * acceleration.x
                    + acceleration.y * acceleration.y
                    + acceleration.z * acceleration.z
            )
            guard magnitude > Self.threshold else { return }
            let now = Date()
            guard now.timeIntervalSince(self.lastFiredAt) > Self.cooldown else { return }
            self.lastFiredAt = now
            self.onShake?()
        }
        #endif
    }

    func stop() {
        #if canImport(CoreMotion)
        motion.stopAccelerometerUpdates()
        #endif
        onShake = nil
    }
}
