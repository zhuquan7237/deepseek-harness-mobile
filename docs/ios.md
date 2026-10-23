# iOS 端（云 Mac + 模拟器流水线）

## 为什么不是本机模拟器

iOS 模拟器是 macOS 的框架（`CoreSimulator`），Xcode 只在 macOS 上跑；Windows 上
不存在可用的 iOS 模拟器（市面上叫这个名字的都是套壳浏览器，跑不了 IPA/APP）。
所以这个仓库的 iOS 验证放在 **GitHub Actions 的 macOS runner** 上：

- 仓库是 public → 标准 runner（含 macOS）免费、不计分钟数；
- 每次 iOS 改动 push 就自动跑一遍：`xcodegen` 生成工程 → `xcodebuild` 出
  `iphonesimulator` 包 → `xcrun simctl boot/install/launch` → 截图 + 断言；
- 产物（6 张截图、日志）以 artifact 回传，本机 `gh run download` 就能看。

一次完整流程约 6–8 分钟（含 brew 装 xcodegen 与模拟器冷启动）。

## 现在 iOS 版是什么形态

**原生外壳 + 网页端**：`WKWebView` 承载桌面端桥接插件内置的 `/mobile` 页面
（功能本体），SwiftUI 外壳负责原生能力：

| 能力 | 实现 |
| --- | --- |
| 扫码配对 | `AVFoundation` 原生扫码；解析二维码里的 `?pair=CODE`，注入网页表单并点「配对」 |
| 配对状态 | 轮询页面里配对表单是否**可见**（`offsetParent`），成功后把 baseURL 落盘 |
| 冷启动 | 有 baseURL 直接进外壳；失效时原生故障卡可「重试 / 重新配对」 |
| 解除绑定后 | 网页配对页右上角注入原生「扫码配对」按钮（走 `WKScriptMessageHandler`） |
| 键盘 | 用 `keyboardLayoutGuide` 收缩 WebView，网页的 100% 布局自己重排 |
| 安全区 | WebView 全屏 + 页面 `env(safe-area-inset-*)`（`viewport-fit=cover`） |

**为什么不直接移植 Android 的 Compose**：`app/` 里 2.2 万行 Kotlin 混着 CameraX、
DataStore、前台浮窗服务、Intent 下载等平台专属件；上 Compose Multiplatform 需要先
把工程改成 KMP（换网络栈、换存储、砍浮窗），是多轮改造。外壳版先把「可安装、可
配对、可聊天」的闭环立住，之后按页面逐步原生化（会话列表 → 聊天 → 设置）。

## 目录

```
ios/
  project.yml            # xcodegen 工程定义（工程文件不入库，CI 里生成）
  DshMobile/
    DshMobileApp.swift   # 入口、路由、AppState（baseURL 持久化）、解析二维码
    PairingView.swift    # 原生配对页 + AVFoundation 扫码面板
    ShellView.swift      # WKWebView 外壳、扫码桥、配对注入、故障卡
    SelfCheckView.swift  # 自检页（CI 用 -selfcheck 启动，运行时取值）
    Assets.xcassets      # 图标（由 Android 自适应图标两层合成 1024）
.github/workflows/ios.yml     # 云 Mac 流水线
.github/scripts/mock_bridge.py # CI 用的假电脑端（只实现配对）
```

## 本地怎么触发与验收

```bash
# 手动触发（也可 push 到 ios/** 自动触发）
gh workflow run ios.yml --ref master
gh run watch

# 拉回截图
gh run download <run-id> -n ios-simulator-<n> -D ios-art
```

CI 会跑六组画面并逐项断言：

1. `shot-1-pairing` 首启原生配对页
2. `shot-2-selfcheck` 自检页（机型/系统/构建/WKWebView DOM 计数）
3. `shot-3-live-shell` 真实网页端（公网）在 WKWebView 里的渲染 + 注入的扫码按钮
4. `shot-4-paired` 假电脑端配对 E2E（断言 `base.url` 已写进 UserDefaults）
5. `shot-5-relaunch` 冷启动直接进外壳（不带参数）
6. `shot-6-offline` 电脑端不在线时的原生故障卡

## 发布

版本号只有一个来源：`app/build.gradle.kts` 的 `versionName` / `versionCode`，
CI 里读出来注入 `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION`，所以 iOS 与
Android 永远同版本。

`scripts/release.sh` 发布 Android 后，GitHub 的 `release: published` 事件会触发
iOS 流水线，把 `dsh-mobile-ios-simulator-v<版本>.zip`（含 `simctl` 可直接安装的
`.app`）与 sha256 附到同一个 Release；脚本会等它落地再打印汇总。

安装到模拟器（需要一台 Mac）：

```bash
unzip dsh-mobile-ios-simulator-v0.5.4.zip
xcrun simctl boot "iPhone 16 Pro"
xcrun simctl install booted DshMobile.app
xcrun simctl launch booted com.dsh.mobile.ios
```

## 已知限制

- **真机分发**：没有 Apple 开发者账号时，签名只在模拟器有效。真机长期可用要么
  $99/年 走 TestFlight，要么免费 Apple ID 侧载（7 天重签一次）。这是取舍，不做默认。
- **模拟器没有摄像头**：扫码路径在模拟器里会提示「没有可用摄像头」，走手填路径；
  真机不受影响。
- **换网络/换地址**：网页端的 token 存在 origin 的 IndexedDB 里，换域名就要重新
  配对（外壳会自动判断二维码与当前页面是否同源）。
