import SwiftUI
import UIKit
import WebKit

// MARK: - 外壳状态

enum ShellPhase: Equatable {
    case loading
    case ready
    case failed(String)
}

/// 网页外壳的控制器：持有 WKWebView、原生扫码桥、配对自动填写与失败重试。
/// 凭据（token）始终留在页面的 IndexedDB 里，原生层只负责"把码送进去"。
/// 说明：WKWebView 的回调都在主线程，这里不加 @MainActor（避免 Swift 5 语言
/// 模式下与 ObjC 代理协议隔离语义打架），@Published 的写入天然在主线程。
final class ShellController: NSObject, ObservableObject {
    @Published var phase: ShellPhase = .loading
    @Published var paired = false
    @Published var requestScan = false

    /// 扫到的二维码与当前页面不同源时，交给外壳换地址重载。
    var onNeedsRebase: ((URL, String) -> Void)?
    /// 页面进入已配对状态时回调（用于把 baseURL 落盘）。
    var onPaired: (() -> Void)?

    let webView: WKWebView
    private let bridge = ScriptBridge()
    private var pendingCode: String?
    private var pollTimer: Timer?
    private var pollDeadline = Date()

    override init() {
        let conf = WKWebViewConfiguration()
        conf.defaultWebpagePreferences.allowsContentJavaScript = true
        conf.allowsInlineMediaPlayback = true
        conf.mediaTypesRequiringUserActionForPlayback = []
        conf.userContentController.addUserScript(
            WKUserScript(source: Self.scanAffordanceScript, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        )
        webView = WKWebView(frame: .zero, configuration: conf)
        super.init()
        bridge.owner = self
        conf.userContentController.add(bridge, name: "dsh")
        webView.navigationDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.keyboardDismissMode = .interactive
        webView.isOpaque = false
        webView.backgroundColor = UIColor(Ink.bg)
        webView.scrollView.backgroundColor = UIColor(Ink.bg)
    }

    func start(url: URL, pairCode: String?) {
        pendingCode = pairCode
        paired = false
        phase = .loading
        webView.load(URLRequest(url: url, timeoutInterval: 25))
    }

    func reload() {
        phase = .loading
        webView.reload()
    }

    func acceptScanPayload(_ payload: String) {
        guard let parsed = AppConfig.parsePairPayload(payload) else {
            injectPair(code: payload.trimmingCharacters(in: .whitespacesAndNewlines))
            return
        }
        if let currentHost = webView.url?.host, parsed.url.host == currentHost {
            injectPair(code: parsed.code)
        } else {
            onNeedsRebase?(parsed.url, parsed.code)
        }
    }

    // MARK: 页面内配对

    private func injectPair(code: String) {
        let safe = code.replacingOccurrences(of: "'", with: "")
        let js = """
        (function(){
          var el = document.getElementById('pair-code');
          if (!el) { return 'noform'; }
          var name = document.getElementById('pair-name');
          if (name && !name.value.trim()) { name.value = 'iPhone'; }
          el.value = '\(safe)';
          el.dispatchEvent(new Event('input', { bubbles: true }));
          var go = document.getElementById('pair-go');
          if (!go) { return 'nobutton'; }
          go.click();
          return 'ok';
        })();
        """
        webView.evaluateJavaScript(js) { [weak self] result, error in
            guard let self else { return }
            if let error {
                self.phase = .failed("配对脚本没有执行成功：\(error.localizedDescription)")
                return
            }
            let outcome = (result as? String) ?? "?"
            if outcome == "noform" || outcome == "nobutton" {
                self.phase = .failed("页面里没找到配对表单（网页端版本可能不一致）")
                return
            }
            self.startPollingForPaired()
        }
    }

    private func checkPaired() {
        // 注意：不能只看 DOM 里有没有 #pair-go —— 网页端可能只是把整个配对容器
        // 隐藏起来（display:none），节点仍在文档里。用 offsetParent 判断"真的不可见"。
        webView.evaluateJavaScript(
            "(function(){var el=document.getElementById('pair-go');"
            + "if(!el){return 'paired';}"
            + "return el.offsetParent===null?'paired':'pairing';})();"
        ) { [weak self] value, _ in
            guard let self, let state = value as? String else { return }
            if state == "paired" {
                self.finishPairing()
            } else if Date() > self.pollDeadline {
                self.stopPolling()
                self.phase = .failed("配对码可能已过期或网络不通，请重新配对")
            }
        }
    }

    private func startPollingForPaired() {
        stopPolling()
        pollDeadline = Date().addingTimeInterval(30)
        pollTimer = Timer.scheduledTimer(withTimeInterval: 1.2, repeats: true) { [weak self] _ in
            self?.checkPaired()
        }
    }

    private func stopPolling() {
        pollTimer?.invalidate()
        pollTimer = nil
    }

    private func finishPairing() {
        stopPolling()
        pendingCode = nil
        if !paired {
            paired = true
            phase = .ready
            onPaired?()
        }
    }

    fileprivate func handleBridgeMessage(_ body: Any) {
        guard let dict = body as? [String: Any], (dict["type"] as? String) == "scan" else { return }
        requestScan = true
    }

    // MARK: 注入脚本

    /// 网页端出现配对表单时，在右上角挂一颗原生「扫码配对」按钮——
    /// 这样解除绑定后回到网页配对页，也仍然能扫码，而不是只能手打。
    private static let scanAffordanceScript = """
    (function(){
      function ensure(){
        var form = document.getElementById('pair-go');
        var visible = form && form.offsetParent !== null;
        var btn = document.getElementById('dsh-scan');
        if (!visible) { if (btn) { btn.remove(); } return; }
        if (btn) { return; }
        btn = document.createElement('button');
        btn.id = 'dsh-scan';
        btn.type = 'button';
        btn.textContent = '📷 扫码配对';
        btn.style.cssText = 'position:fixed;right:12px;top:calc(env(safe-area-inset-top,0px) + 54px);z-index:99999;'
          + 'padding:9px 14px;border-radius:999px;border:1px solid rgba(217,184,121,.5);'
          + 'background:rgba(25,28,22,.92);color:#D9B879;font:600 13px -apple-system,sans-serif;'
          + 'box-shadow:0 6px 18px rgba(0,0,0,.45);-webkit-backdrop-filter:blur(8px)';
        btn.addEventListener('click', function(){
          try { window.webkit.messageHandlers.dsh.postMessage({ type: 'scan' }); } catch (e) {}
        });
        document.body.appendChild(btn);
      }
      ensure();
      setInterval(ensure, 900);
    })();
    """
}

// MARK: - WKScriptMessageHandler 代理（避免 userContentController 强引用控制器）

private final class ScriptBridge: NSObject, WKScriptMessageHandler {
    weak var owner: ShellController?

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        owner?.handleBridgeMessage(message.body)
    }
}

// MARK: - 导航代理

extension ShellController: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        if case .failed = phase { phase = .loading }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if case .failed = phase { return }
        phase = .ready
        if let code = pendingCode {
            injectPair(code: code)
        } else {
            checkPaired()
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        fail(error)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        fail(error)
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationResponse: WKNavigationResponse,
        decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void
    ) {
        if let http = navigationResponse.response as? HTTPURLResponse, http.statusCode >= 500 {
            decisionHandler(.cancel)
            phase = .failed("电脑端返回 \(http.statusCode)——桥接服务可能没在跑")
            return
        }
        decisionHandler(.allow)
    }

    private func fail(_ error: Error) {
        let ns = error as NSError
        // 取消（-999）是正常打断，不当故障
        if ns.code == NSURLErrorCancelled { return }
        phase = .failed(ns.localizedDescription)
    }
}

// MARK: - 容器（键盘出现时收缩 WebView，网页端的 100% 布局自己跟着重排）

struct WebContainer: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor(Ink.bg)
        webView.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            webView.topAnchor.constraint(equalTo: container.topAnchor),
            webView.bottomAnchor.constraint(equalTo: container.keyboardLayoutGuide.topAnchor),
        ])
        return container
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}

// MARK: - 外壳视图

struct ShellView: View {
    @ObservedObject var app: AppState
    @StateObject private var shell = ShellController()
    @State private var scannerOpen = false

    var body: some View {
        ZStack(alignment: .top) {
            WebContainer(webView: shell.webView)
                .ignoresSafeArea()

            if case .loading = shell.phase {
                loadingBar
            }
            if case .failed(let message) = shell.phase {
                failureCard(message)
            }
        }
        .onAppear {
            shell.onPaired = { app.markPaired() }
            shell.onNeedsRebase = { url, code in
                app.beginPairing(with: url, code: code)
                shell.start(url: url, pairCode: code)
            }
            shell.start(url: app.baseURL, pairCode: app.pendingPairCode)
        }
        .onChange(of: app.pendingPairCode) { code in
            if let code, !code.isEmpty {
                shell.start(url: app.baseURL, pairCode: code)
            }
        }
        .onChange(of: shell.requestScan) { wants in
            if wants {
                scannerOpen = true
                shell.requestScan = false
            }
        }
        .sheet(isPresented: $scannerOpen) {
            ScannerSheet(
                onCode: { payload in
                    scannerOpen = false
                    shell.acceptScanPayload(payload)
                },
                onClose: { scannerOpen = false }
            )
        }
    }

    private var loadingBar: some View {
        GeometryReader { geo in
            Rectangle()
                .fill(Ink.gold.opacity(0.85))
                .frame(width: geo.size.width * 0.3, height: 2)
                .offset(y: geo.safeAreaInsets.top)
                .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: shell.phase)
        }
        .frame(height: 3)
        .allowsHitTesting(false)
    }

    private func failureCard(_ message: String) -> some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(alignment: .leading, spacing: 14) {
                Text("连不上电脑端")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Ink.textPrimary)
                Text(message)
                    .font(.system(size: 13))
                    .lineSpacing(3)
                    .foregroundStyle(Ink.textSecondary)
                Text("确认电脑上的 DeepSeek Harness 在运行；换了网络就重新扫一次二维码。")
                    .font(.system(size: 12.5))
                    .lineSpacing(3)
                    .foregroundStyle(Ink.textTertiary)
                HStack(spacing: 10) {
                    Button("重试") { shell.reload() }
                        .font(.system(size: 14.5, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Ink.gold)
                        .foregroundStyle(Color(hex: 0x24271F))
                        .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                    Button("重新配对") { app.unpairLocally() }
                        .font(.system(size: 14.5, weight: .medium))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .foregroundStyle(Ink.textPrimary)
                        .overlay(
                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                .stroke(Ink.divider, lineWidth: 0.8)
                        )
                }
                .buttonStyle(.plain)
            }
            .padding(18)
            .background(Ink.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Ink.divider.opacity(0.8), lineWidth: 0.8)
            )
            .padding(.horizontal, 20)
            Spacer()
        }
        .background(Color.black.opacity(0.35).ignoresSafeArea())
    }
}
