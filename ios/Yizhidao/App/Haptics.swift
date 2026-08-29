#if canImport(UIKit)
import UIKit
#endif

/// 起卦流程的触觉反馈。按键音效默认是「无」，静音的用户全靠触觉感知节奏，
/// 所以这里不跟 `TapSoundPlayer` 的设置绑定。
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
