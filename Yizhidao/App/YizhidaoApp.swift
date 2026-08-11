import SwiftUI
import SwiftData

@main
struct YizhidaoApp: App {
    var sharedModelContainer: ModelContainer = {
        let schema = Schema([ReadingRecord.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        do {
            return try ModelContainer(for: schema, configurations: [config])
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
    }()

    init() {
        _ = HexagramStore.shared
    }

    var body: some Scene {
        WindowGroup {
            RootTabView()
        }
        .modelContainer(sharedModelContainer)
    }
}

struct RootTabView: View {
    @State private var appNavigation = AppNavigation()

    var body: some View {
        @Bindable var appNavigation = appNavigation
        TabView(selection: $appNavigation.selectedTab) {
            CastingHomeView()
                .tabItem {
                    Label("起卦", systemImage: "sparkles")
                }
                .tag(AppTab.cast)
            HistoryListView()
                .tabItem {
                    Label("历史", systemImage: "clock")
                }
                .tag(AppTab.history)
        }
        .tint(AppTheme.accent)
        .preferredColorScheme(.light)
        .environment(\.locale, Locale(identifier: "zh_CN"))
        .environment(appNavigation)
        .animation(nil, value: appNavigation.selectedTab)
    }
}
