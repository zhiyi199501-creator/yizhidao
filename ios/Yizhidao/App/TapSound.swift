import AVFoundation

enum TapSoundKind: String, CaseIterable, Identifiable {
    case none
    case bubble
    case click
    case wood
    case jade

    var id: String { rawValue }

    var title: String {
        let raw: String
        switch self {
        case .none: raw = "无音效"
        case .bubble: raw = "气泡音"
        case .click: raw = "按键音"
        case .wood: raw = "木鱼"
        case .jade: raw = "玉磬"
        }
        return raw.zh
    }

    var resourceName: String? {
        switch self {
        case .none: return nil
        case .bubble: return "tap_bubble"
        case .click: return "tap_click"
        case .wood: return "tap_wood"
        case .jade: return "tap_jade"
        }
    }
}

final class TapSoundPlayer: @unchecked Sendable {
    static let shared = TapSoundPlayer()
    static let defaultsKey = "settings.tapSound.v1"

    private var players: [TapSoundKind: AVAudioPlayer] = [:]
    private var lastPlayAt: TimeInterval = 0
    private var prepared = false

    var kind: TapSoundKind {
        TapSoundKind(rawValue: UserDefaults.standard.string(forKey: Self.defaultsKey) ?? "") ?? .none
    }

    func prepare() {
        guard !prepared else { return }
        prepared = true
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        for kind in TapSoundKind.allCases {
            guard let name = kind.resourceName,
                  let url = Bundle.main.url(forResource: name, withExtension: "wav"),
                  let player = try? AVAudioPlayer(contentsOf: url)
            else { continue }
            player.prepareToPlay()
            player.volume = 0.72
            players[kind] = player
        }
    }

    func play(kind override: TapSoundKind? = nil) {
        let target = override ?? kind
        guard target != .none, let player = players[target] else { return }
        let now = ProcessInfo.processInfo.systemUptime
        if now - lastPlayAt < 0.05 { return }
        lastPlayAt = now
        player.currentTime = 0
        player.play()
    }
}
