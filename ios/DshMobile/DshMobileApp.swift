import SwiftUI
import UIKit

// iOS 端入口。
// 形态说明（2026-10）：仓库里的 Android 端 2.2 万行 Compose 里混着 CameraX、
// DataStore、前台浮窗服务、Intent 下载这些平台专属件，直接上 Compose
// Multiplatform 要先做一轮工程改造。所以 iOS 第一版走「原生外壳 + 网页端」：
// 网页端（桌面桥接插件内置的 /mobile）才是功能本体，外壳负责原生扫码、安全区、
// 键盘、错误重试与配对状态持久化。后续可把会话页/聊天页逐步换成原生。
@main
struct DshMobileApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
                .tint(Ink.gold)
        }
    }
}

struct RootView: View {
    @StateObject private var app = AppState()
    private let startAtSelfCheck = CommandLine.arguments.contains("-selfcheck")

    var body: some View {
        ZStack {
            Ink.bg.ignoresSafeArea()
            if startAtSelfCheck {
                SelfCheckView()
            } else {
                switch app.route {
                case .pairing:
                    PairingView(app: app)
                        .transition(.opacity)
                case .shell:
                    ShellView(app: app)
                        .transition(.opacity)
                }
            }
        }
        .animation(.easeInOut(duration: 0.22), value: app.route)
    }
}

// MARK: - 路由与持久化

enum Route: Equatable {
    case pairing
    case shell
}

final class AppState: ObservableObject {
    @Published var route: Route
    @Published var baseURL: URL
    /// 扫到二维码后先带着配对码进外壳——配对本身由页面完成（token 必须落在
    /// 该 origin 的 IndexedDB 里，原生层不碰凭据）。
    @Published var pendingPairCode: String?

    private let defaults = UserDefaults.standard

    init() {
        // CI 驱动的入口：-url <地址> [-paircode <码>]
        let args = CommandLine.arguments
        if let idx = args.firstIndex(of: "-url"), idx + 1 < args.count,
           let url = URL(string: args[idx + 1]) {
            baseURL = url
            route = .shell
            if let cidx = args.firstIndex(of: "-paircode"), cidx + 1 < args.count {
                pendingPairCode = args[cidx + 1]
            }
            return
        }
        if let saved = defaults.string(forKey: "base.url"), let url = URL(string: saved) {
            baseURL = url
            route = .shell
        } else {
            baseURL = AppConfig.defaultBase
            route = .pairing
        }
    }

    /// 扫码/手填之后：地址 + 码 → 进外壳，由页面完成配对。
    func beginPairing(with url: URL, code: String) {
        baseURL = url
        pendingPairCode = code
        route = .shell
    }

    /// 页面里已经没有配对表单了 → 说明配对成功，把这个 origin 记下来。
    func markPaired() {
        defaults.set(baseURL.absoluteString, forKey: "base.url")
        pendingPairCode = nil
    }

    func unpairLocally() {
        defaults.removeObject(forKey: "base.url")
        pendingPairCode = nil
        route = .pairing
    }
}

// MARK: - 常量

enum AppConfig {
    /// 生产页面（桌面端桥接插件内置，公网可达）。局域网直连可换 http://<IP>:17731/mobile/。
    static let defaultBase = URL(string: "https://m.zhuquan.xyz/mobile/")!

    static var version: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
    }

    static var build: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
    }

    /// 从二维码里抠配对信息。二维码形如
    ///   https://m.zhuquan.xyz/mobile/?pair=8H3K-2Q9F
    ///   https://cn.zhuquan.xyz:8443/m/<key>/mobile/?pair=8H3K-2Q9F
    /// 中继链接带路径前缀，而网页端的接口是绝对路径（/mobile/pair），所以带前缀
    /// 的链接只取配对码，页面仍走公网域名。
    static func parsePairPayload(_ raw: String) -> (url: URL, code: String)? {
        guard let pairRange = raw.range(of: #"pair=([A-Za-z0-9_-]{4,})"#, options: .regularExpression),
              let original = URL(string: raw)
        else { return nil }
        let code = String(raw[pairRange]).replacingOccurrences(of: "pair=", with: "")
        guard !code.isEmpty else { return nil }

        var comps = URLComponents(url: original, resolvingAgainstBaseURL: false)
        comps?.query = nil
        comps?.fragment = nil
        guard var page = comps?.url else { return (defaultBase, code) }

        // 中继链接（带 /m/<key>/ 前缀或显式端口）→ 回到公网域名
        if page.path.contains("/m/") || original.port != nil {
            return (defaultBase, code)
        }
        if page.path.isEmpty || page.path == "/" { page = defaultBase }
        if !page.absoluteString.hasSuffix("/") { page = page.appendingPathComponent("") }
        return (page, code)
    }
}

// MARK: - 夜墨调色（与 Android DarkPalette 同源）

enum Ink {
    static let bg = Color(hex: 0x11130F)
    static let surface = Color(hex: 0x191C16)
    static let surfaceHi = Color(hex: 0x252920)
    static let textPrimary = Color(hex: 0xEEE9DC)
    static let textSecondary = Color(hex: 0xB9B9AA)
    static let textTertiary = Color(hex: 0x96998B)
    static let accent = Color(hex: 0xE28B79)
    static let gold = Color(hex: 0xD9B879)
    static let online = Color(hex: 0x9CBB99)
    static let danger = Color(hex: 0xF08D83)
    static let divider = Color(hex: 0x34392F)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}
