import SwiftUI

// iOS 端入口。
// 之所以用 SwiftUI 而不是直接搬 Android 的 Compose：本仓库的 Android 端 2.2 万行里
// 有 CameraX / DataStore / 前台浮窗服务 / Intent 下载这些平台专属件，Compose
// Multiplatform 迁移要先做一轮工程改造；先把"能在模拟器里跑起来的 iOS 壳"立住，
// 再决定 UI 是继续用 WebView 承载还是做 CMP 移植。
@main
struct DshMobileApp: App {
    /// `-autoweb` 由 CI 传入：simctl launch … -autoweb 时直接进网页外壳，
    /// 用于抓 WKWebView 的真实渲染截图。
    private var autoWeb: Bool { CommandLine.arguments.contains("-autoweb") }

    var body: some Scene {
        WindowGroup {
            RootView(autoWeb: autoWeb)
                .preferredColorScheme(.dark)
        }
    }
}

struct RootView: View {
    let autoWeb: Bool

    var body: some View {
        if autoWeb {
            WebShellView(bootURL: AppConfig.mobileURL)
        } else {
            SelfCheckView()
        }
    }
}

enum AppConfig {
    /// 生产页面（桌面端桥接插件内置，公网可达）。局域网直连可换 http://<IP>:17731/mobile。
    static let mobileURL = URL(string: "https://m.zhuquan.xyz/mobile")!

    static var version: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
    }

    static var build: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
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
