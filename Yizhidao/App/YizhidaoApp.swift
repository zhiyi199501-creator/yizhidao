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
    var body: some View {
        TabView {
            CastingHomeView()
                .tabItem {
                    Label("起卦", systemImage: "sparkles")
                }
            HistoryListView()
                .tabItem {
                    Label("历史", systemImage: "clock")
                }
        }
        .tint(Color(red: 0.45, green: 0.22, blue: 0.18))
    }
}
