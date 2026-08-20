import Foundation
import Observation
import SwiftUI

enum AppTab: Hashable {
    case cast
    case history
    case cases
    case me
}

/// 跳转到历史「同卦明细」并预填方法与筛选。
struct SimilarHexagramDestination: Hashable {
    /// 每次跳转唯一，避免相同筛选第二次 NavigationPath 不刷新。
    let jumpID: UUID
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
            jumpID: UUID(),
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
    /// 递增后历史页执行同类跳转（比 optional 更稳，可重复点同一卦）。
    var similarJumpTick: Int = 0
    /// 递增后起卦页应收起结果导航。
    var dismissCastResultTick: Int = 0

    func openSimilarHexagram(for result: CastResult) {
        pendingSimilar = .from(result: result)
        // Tab 切换静默，详情用历史导航栈的正常 push（与「时间」进结果一致）。
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            selectedTab = .history
        }
        similarJumpTick += 1
        DispatchQueue.main.async {
            var dismissTransaction = Transaction()
            dismissTransaction.disablesAnimations = true
            withTransaction(dismissTransaction) {
                self.dismissCastResultTick += 1
            }
        }
    }
}
