import AVFoundation
import SwiftUI
import UIKit

// MARK: - 配对页（原生）
//
// 产品红线（沿用 Android 端 2026-09-29 立的规矩）：扫码是唯一显性主路径，
// 手填地址/配对码收进折叠区。扫完直接把码交给页面去配对，不额外要求用户操作。

struct PairingView: View {
    @ObservedObject var app: AppState

    @State private var manualOpen = false
    @State private var manualCode = ""
    @State private var manualBase = AppConfig.defaultBase.absoluteString
    @State private var scannerMessage: String?
    @State private var formError: String?
    @State private var showScanner = false
    @FocusState private var codeFocused: Bool

    private var manualDisabled: Bool {
        manualCode.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        GeometryReader { geo in
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    brand
                        .padding(.top, 10)
                    // 上下各一个弹性留白：把「标题 + 扫码 + 手动 + 帮助卡」当成一整块
                    // 居中放在品牌行与底部之间。之前帮助卡单独贴底，会在中间留出一个
                    // 明显空洞；整块居中后，空气只出现在块的上方和下方。
                    Spacer(minLength: 20)
                    headline
                    scanButton
                    if let formError {
                        Text(formError)
                            .font(.system(size: 12.5))
                            .lineSpacing(2)
                            .foregroundStyle(Ink.danger)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    manualSection
                    howto
                    // 下方留白封顶：整块内容略微上移，底部空气不无限增长。
                    Spacer(minLength: 20).frame(maxHeight: 210)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
                // 内容不满一屏时也撑到屏底：帮助文案落到底部，避免中间出现一大块死白。
                .frame(minHeight: geo.size.height, alignment: .top)
            }
        }
        .background(Ink.bg.ignoresSafeArea())
        .sheet(isPresented: $showScanner) {
            ScannerSheet(
                onCode: { payload in
                    showScanner = false
                    handleScan(payload)
                },
                onClose: { showScanner = false }
            )
        }
        .alert("这台设备没有可用摄像头", isPresented: Binding(
            get: { scannerMessage != nil },
            set: { if !$0 { scannerMessage = nil } }
        )) {
            Button("手动配对") {
                manualOpen = true
                scannerMessage = nil
            }
            Button("好", role: .cancel) { scannerMessage = nil }
        } message: {
            Text(scannerMessage ?? "")
        }
    }

    private var brand: some View {
        HStack(spacing: 10) {
            Text("DSH")
                .font(.system(size: 13, weight: .semibold))
                .tracking(1.4)
                .foregroundStyle(Ink.gold)
                .padding(.horizontal, 9)
                .padding(.vertical, 5)
                .overlay(
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .stroke(Ink.gold.opacity(0.5), lineWidth: 0.8)
                )
            Text("Mobile · iOS")
                .font(.system(size: 12.5))
                .foregroundStyle(Ink.textTertiary)
            Spacer()
            Text("v\(AppConfig.version)")
                .font(.system(size: 11.5, design: .monospaced))
                .foregroundStyle(Ink.textTertiary.opacity(0.8))
        }
    }

    private var headline: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("配对这台手机")
                .font(.system(size: 30, weight: .medium))
                .tracking(0.4)
                .foregroundStyle(Ink.textPrimary)
            Text("用摄像头扫一下电脑上的二维码就能连上。\n会话、文件、模型都还留在电脑上。")
                .font(.system(size: 14))
                .lineSpacing(4)
                .foregroundStyle(Ink.textSecondary)
        }
    }

    private var scanButton: some View {
        Button {
            showScanner = true
        } label: {
            HStack(spacing: 9) {
                Image(systemName: "qrcode.viewfinder")
                    .font(.system(size: 17, weight: .semibold))
                Text("扫码配对")
                    .font(.system(size: 16, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background(Ink.gold)
            .foregroundStyle(Color(hex: 0x24271F))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var manualSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) { manualOpen.toggle() }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "keyboard")
                        .font(.system(size: 12, weight: .medium))
                    Text("手动配对")
                        .font(.system(size: 13.5, weight: .medium))
                    Image(systemName: manualOpen ? "chevron.up" : "chevron.down")
                        .font(.system(size: 9, weight: .semibold))
                }
                .foregroundStyle(Ink.textSecondary)
                .padding(.horizontal, 12)
                .frame(height: 38)
                .background(Ink.surface)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(Ink.divider.opacity(0.7), lineWidth: 0.7))
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)

            if manualOpen {
                VStack(spacing: 12) {
                    field("配对码", text: $manualCode, placeholder: "XXXX-XXXX", mono: true)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .focused($codeFocused)
                    field("服务器地址", text: $manualBase, placeholder: "https://m.zhuquan.xyz/mobile/", mono: true)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button {
                        submitManual()
                    } label: {
                        Text("连接")
                            .font(.system(size: 15, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(manualDisabled ? Ink.surfaceHi : Ink.surfaceHi)
                            .foregroundStyle(manualDisabled ? Ink.textTertiary.opacity(0.6) : Ink.textPrimary)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(Ink.divider, lineWidth: 0.8)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(manualDisabled)
                }
                .padding(14)
                .background(Ink.surface)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Ink.divider.opacity(0.7), lineWidth: 0.8)
                )
            }
        }
    }

    private func field(_ label: String, text: Binding<String>, placeholder: String, mono: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 11.5, weight: .medium))
                .foregroundStyle(Ink.textTertiary)
            TextField("", text: text, prompt: Text(placeholder).foregroundColor(Ink.textTertiary.opacity(0.55)))
                .font(.system(size: 15, design: mono ? .monospaced : .default))
                .foregroundStyle(Ink.textPrimary)
                .padding(.horizontal, 12)
                .frame(height: 44)
                .background(Ink.bg)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(Ink.divider, lineWidth: 0.8)
                )
        }
    }

    private var howto: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "info.circle")
                    .font(.system(size: 11.5, weight: .medium))
                Text("怎么拿到二维码")
                    .font(.system(size: 11.5, weight: .semibold))
                    .tracking(0.8)
            }
            .foregroundStyle(Ink.textSecondary)
            Text("电脑端：设置 → 移动端 → 显示二维码。\n配对码 5 分钟内有效；同一 Wi‑Fi 下也能直连。")
                .font(.system(size: 12.5))
                .lineSpacing(5)
                .foregroundStyle(Ink.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Ink.surface.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Ink.divider.opacity(0.5), lineWidth: 0.6)
        )
        .padding(.bottom, 8)
    }

    private func submitManual() {
        let code = manualCode.trimmingCharacters(in: .whitespaces)
        guard !code.isEmpty else { return }
        let raw = manualBase.trimmingCharacters(in: .whitespaces)
        guard let base = URL(string: raw), base.scheme != nil else {
            formError = "服务器地址不是合法 URL"
            return
        }
        formError = nil
        app.beginPairing(with: base, code: code)
    }

    private func handleScan(_ payload: String) {
        if let parsed = AppConfig.parsePairPayload(payload) {
            formError = nil
            app.beginPairing(with: parsed.url, code: parsed.code)
            return
        }
        // 只扫到裸配对码（个别客户端会这样显示）时，用手填区兜底。
        let trimmed = payload.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.range(of: #"^[A-Za-z0-9]{4,12}(-[A-Za-z0-9]{2,12})?$"#, options: .regularExpression) != nil {
            manualCode = trimmed
            manualOpen = true
            formError = "这张二维码里没有电脑地址，请确认服务器地址后点连接"
        } else {
            formError = "二维码内容不认识：\(trimmed.prefix(48))"
        }
    }
}

// MARK: - 扫码（AVFoundation）

struct ScannerSheet: View {
    let onCode: (String) -> Void
    let onClose: () -> Void
    @State private var failure: String?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            ScannerHost(onCode: onCode, onFailure: { failure = $0 })
                .ignoresSafeArea()
            viewfinder
            VStack {
                HStack {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 38, height: 38)
                            .background(.black.opacity(0.45))
                            .clipShape(Circle())
                    }
                    Spacer()
                }
                .padding(.horizontal, 18)
                .padding(.top, 8)
                Spacer()
                Text(failure ?? "把电脑上的二维码放进框内")
                    .font(.system(size: 13.5))
                    .foregroundStyle(.white.opacity(0.9))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.black.opacity(0.45))
                    .clipShape(Capsule())
                    .padding(.bottom, 42)
            }
        }
    }

    private var viewfinder: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height) * 0.62
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(Color.white.opacity(0.85), style: StrokeStyle(lineWidth: 2.5, dash: [26, 16]))
                .frame(width: side, height: side)
                .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
    }
}

private struct ScannerHost: UIViewControllerRepresentable {
    let onCode: (String) -> Void
    let onFailure: (String) -> Void

    func makeUIViewController(context: Context) -> ScannerVC {
        let vc = ScannerVC()
        vc.onCode = onCode
        vc.onFailure = onFailure
        return vc
    }

    func updateUIViewController(_ uiViewController: ScannerVC, context: Context) {}
}

final class ScannerVC: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onCode: ((String) -> Void)?
    var onFailure: ((String) -> Void)?

    private let session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var delivered = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        requestAndConfigure()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        guard session.inputs.isEmpty == false else { return }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self, !self.session.isRunning else { return }
            self.session.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if session.isRunning {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.session.stopRunning()
            }
        }
    }

    private func requestAndConfigure() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted { self?.configure() } else { self?.onFailure?("没有相机权限，请到系统设置里允许") }
                }
            }
        default:
            onFailure?("没有相机权限，请到系统设置里允许")
        }
    }

    private func configure() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input)
        else {
            onFailure?("这台设备没有可用摄像头")
            return
        }
        session.beginConfiguration()
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        if session.canAddOutput(output) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr, .aztec, .code128]
        }
        session.commitConfiguration()

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = view.layer.bounds
        view.layer.addSublayer(preview)
        previewLayer = preview

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
        }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !delivered,
              let obj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = obj.stringValue, !value.isEmpty
        else { return }
        delivered = true
        session.stopRunning()
        onCode?(value)
    }
}
