import SwiftUI
import UIKit

/// 自检页：证明"这台模拟器 + 这个包 + 这条网络"三件事都是真的。
/// 每个值都来自运行时（Bundle / ProcessInfo / WKWebView 的 DOM 计数），
/// 不写死任何期望值——CI 截图能直接当验收证据。
struct SelfCheckView: View {
    @StateObject private var probe = WebProbe()

    private var deviceName: String {
        ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]
            ?? UIDevice.current.name
    }

    private var runtime: String {
        let v = ProcessInfo.processInfo.environment["SIMULATOR_RUNTIME_VERSION"]
        return v.map { "iOS \($0)" } ?? "iOS \(UIDevice.current.systemVersion)"
    }

    private var isSimulator: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    private var loaded: Bool { probe.phase == "已加载" && probe.domNodes > 0 }

    var body: some View {
        ZStack {
            Ink.bg.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    header
                    section("设备") {
                        row("机型", deviceName)
                        row("系统", runtime)
                        row("屏幕", screenText)
                        row("环境", isSimulator ? "iOS 模拟器" : "真机")
                    }
                    section("构建") {
                        row("版本", "\(AppConfig.version) (\(AppConfig.build))")
                        row("Bundle ID", Bundle.main.bundleIdentifier ?? "—")
                        row("架构", archText)
                    }
                    section("网页端 · WKWebView") {
                        row("目标", probe.target.isEmpty ? AppConfig.mobileURL.absoluteString : probe.target)
                        row("状态", probe.phase)
                        row("导航", "\(probe.navigations) 次")
                        row("DOM 节点", probe.domNodes < 0 ? "—" : "\(probe.domNodes)")
                        row("正文长度", probe.textLength < 0 ? "—" : "\(probe.textLength) 字")
                        if !probe.detail.isEmpty && probe.phase != "已加载" {
                            row("错误", probe.detail, tone: .danger)
                        }
                    }
                    previewCard
                    footer
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 28)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 9) {
                Text("DSH Mobile")
                    .font(.system(size: 27, weight: .medium))
                    .tracking(0.6)
                    .foregroundStyle(Ink.textPrimary)
                Text("iOS")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(0.8)
                    .foregroundStyle(Ink.gold)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Ink.gold.opacity(0.45), lineWidth: 0.6)
                    )
            }
            Text("模拟器自检 · 环境、构建与网页端渲染")
                .font(.system(size: 13))
                .foregroundStyle(Ink.textTertiary)
            HStack(spacing: 7) {
                Circle()
                    .fill(loaded ? Ink.online : Ink.gold)
                    .frame(width: 6, height: 6)
                Text(loaded ? "全部就绪" : "网页端加载中…")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(loaded ? Ink.online : Ink.textSecondary)
            }
            .padding(.top, 2)
        }
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 11.5, weight: .semibold))
                .tracking(1.2)
                .foregroundStyle(Ink.textTertiary)
            VStack(spacing: 0) {
                content()
            }
            .background(Ink.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Ink.divider.opacity(0.7), lineWidth: 0.6)
            )
        }
    }

    private enum Tone { case normal, danger }

    private func row(_ label: String, _ value: String, tone: Tone = .normal) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: 14) {
                Text(label)
                    .font(.system(size: 13.5))
                    .foregroundStyle(Ink.textSecondary)
                Spacer(minLength: 8)
                Text(value)
                    .font(.system(size: 13, weight: .medium, design: .monospaced))
                    .foregroundStyle(tone == .danger ? Ink.danger : Ink.textPrimary)
                    .multilineTextAlignment(.trailing)
                    .lineLimit(2)
                    .truncationMode(.middle)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            Rectangle()
                .fill(Ink.divider.opacity(0.55))
                .frame(height: 0.5)
                .padding(.leading, 14)
        }
    }

    private var previewCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("网页端实时预览")
                    .font(.system(size: 11.5, weight: .semibold))
                    .tracking(1.2)
                    .foregroundStyle(Ink.textTertiary)
                Spacer()
                Text("m.zhuquan.xyz/mobile")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Ink.textTertiary.opacity(0.8))
            }
            WebView(url: AppConfig.mobileURL, probe: probe)
                .frame(height: 300)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(Ink.divider.opacity(0.7), lineWidth: 0.6)
                )
        }
    }

    private var footer: some View {
        Text("在 GitHub macOS runner 上由 xcrun simctl 实机启动；截图即验收。")
            .font(.system(size: 11))
            .foregroundStyle(Ink.textTertiary.opacity(0.85))
            .padding(.top, 2)
    }

    private var screenText: String {
        let b = UIScreen.main.bounds
        return "\(Int(b.width))×\(Int(b.height)) @\(Int(UIScreen.main.scale))x"
    }

    private var archText: String {
        #if arch(arm64)
        return "arm64"
        #elseif arch(x86_64)
        return "x86_64"
        #else
        return "unknown"
        #endif
    }
}
