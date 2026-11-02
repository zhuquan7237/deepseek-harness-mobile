# DSH Mobile

- `app/` 是 Kotlin + Jetpack Compose Android 随身控制器（`com.dsh.mobile`，minSdk 26）；Agent、文件和模型在电脑端运行，不在手机重建引擎。
- `app/src/main/java/com/dsh/mobile/data/` 下的 `BridgeApi.kt` 定义 `/mobile/*` HTTP 与 Bearer 鉴权，`BridgeRepository.kt` 管会话与连接。改配对、scope、WebSocket 时须与桌面仓 mobile-bridge 契约一起核对。
- 配对地址来自用户电脑实际公布的中继、隧道或 LAN 地址；LAN 桥接为 17732，17731 是电脑本机引擎回环端口，不得混用或默认绑定维护者电脑。
- `ios/` 是 SwiftUI + WKWebView 外壳；版本读取 `app/build.gradle.kts`，模拟器构建与配对验收由 `.github/workflows/ios.yml` 完成。
- Android release 当前使用固定 debug 签名以支持覆盖升级；不得随意换签名。更新下载校验大小及 SHA-256。
- `scripts/release.sh` 会改版本、写桌面桥接 APK/更新清单、打标签、创建 Release，并经 SSH 同步线上镜像；文档改动不运行该脚本。
