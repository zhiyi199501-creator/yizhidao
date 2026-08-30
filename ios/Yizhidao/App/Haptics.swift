#if canImport(UIKit)
import UIKit
#endif

/// 起卦流程的触觉反馈。
@MainActor
enum RitualHaptics {
    #if canImport(UIKit)
    private static let light = UIImpactFeedbackGenerator(style: .light)
    private static let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private static let heavy = UIImpactFeedbackGenerator(style: .heavy)
    #endif

    static func prepare() {
        #if canImport(UIKit)
        light.prepare()
        rigid.prepare()
        heavy.prepare()
        #endif
    }

    /// 一爻落定。动爻手感更实，让「这一爻要动」不用看也知道。
    static func yaoSettled(moving: Bool) {
        #if canImport(UIKit)
        if moving {
            rigid.impactOccurred(intensity: 0.9)
        } else {
            light.impactOccurred(intensity: 0.6)
        }
        #endif
    }

    /// 卦名压印。
    static func seal() {
        #if canImport(UIKit)
        heavy.impactOccurred()
        #endif
    }
}
