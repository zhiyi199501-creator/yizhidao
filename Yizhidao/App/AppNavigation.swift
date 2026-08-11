import Foundation
import Observation

enum AppTab: Hashable {
    case cast
    case history
}

/// 跳转到历史「同卦明细」并预填方法与筛选。
struct SimilarHexagramDestination: Hashable {
    let primaryNumber: Int
    let methodTab: HexagramMethodTab
    let positionFilter: MovingPositionFilter
    let countFilter: MovingCountFilter

    static func from(result: CastResult) -> SimilarHexagramDestination {
        let methodTab: HexagramMethodTab = result.method == .coin ? .coin : .digital
        let positionFilter: MovingPositionFilter = {
            guard methodTab == .digital,
                  result.movingPositions.count == 1,
                  let pos = result.movingPositions.first else {
                return .all
            }
            return MovingPositionFilter.from(position: pos) ?? .all
        }()
        let countFilter: MovingCountFilter = {
            guard methodTab == .coin else { return .all }
            return MovingCountFilter.from(movingCount: result.movingPositions.count)
        }()
        return SimilarHexagramDestination(
            primaryNumber: result.primaryNumber,
            methodTab: methodTab,
            positionFilter: positionFilter,
            countFilter: countFilter
        )
    }
}

@Observable
final class AppNavigation {
    var selectedTab: AppTab = .cast
    var pendingSimilar: SimilarHexagramDestination?
    /// 递增后起卦页应收起结果导航。
    var dismissCastResultTick: Int = 0

    func openSimilarHexagram(for result: CastResult) {
        pendingSimilar = .from(result: result)
        dismissCastResultTick += 1
        selectedTab = .history
    }
}
