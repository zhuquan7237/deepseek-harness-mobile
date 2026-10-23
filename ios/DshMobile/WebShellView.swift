import SwiftUI
import UIKit
import Combine
import WebKit

// MARK: - 加载探针

/// 记录 WKWebView 的真实加载结果。CI 里靠它判断"iOS 能不能渲染网页端"，
/// 而不是只看进程有没有起来。
final class WebProbe: NSObject, ObservableObject, WKNavigationDelegate {
    @Published var phase: String = "未开始"
    @Published var target: String = ""
    @Published var detail: String = ""
    @Published var navigations: Int = 0
    @Published var domNodes: Int = -1
    @Published var textLength: Int = -1

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        navigations += 1
        phase = "加载中"
        target = webView.url?.absoluteString ?? target
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        phase = "已加载"
        target = webView.url?.absoluteString ?? target
        detail = webView.title ?? ""
        webView.evaluateJavaScript(
            "JSON.stringify({n: document.querySelectorAll('*').length, t: (document.body ? document.body.innerText.length : 0)})"
        ) { [weak self] value, _ in
            guard let self, let json = value as? String else { return }
            if let data = json.data(using: .utf8),
               let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                self.domNodes = dict["n"] as? Int ?? -1
                self.textLength = dict["t"] as? Int ?? -1
            }
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        phase = "失败"
        detail = error.localizedDescription
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        phase = "失败"
        detail = error.localizedDescription
    }
}

// MARK: - WKWebView 包装

struct WebView: UIViewRepresentable {
    let url: URL
    let probe: WebProbe

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = probe
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.isOpaque = false
        webView.backgroundColor = UIColor(Ink.bg)
        webView.scrollView.backgroundColor = UIColor(Ink.bg)
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

// MARK: - 网页外壳（第一个可跑的 iOS 形态）

struct WebShellView: View {
    let bootURL: URL
    @StateObject private var probe = WebProbe()

    var body: some View {
        ZStack(alignment: .top) {
            Ink.bg.ignoresSafeArea()
            WebView(url: bootURL, probe: probe)
            if probe.phase != "已加载" {
                statusBar
            }
        }
    }

    private var statusBar: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(probe.phase == "失败" ? Ink.danger : Ink.gold)
                .frame(width: 7, height: 7)
            Text(probe.phase == "失败" ? "加载失败 · \(probe.detail)" : "正在连接电脑端…")
                .font(.system(size: 12.5, weight: .medium))
                .foregroundStyle(Ink.textPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Ink.surface.opacity(0.96))
        .overlay(alignment: .bottom) {
            Rectangle().fill(Ink.divider).frame(height: 0.5)
        }
    }
}
