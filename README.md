# DeepSeek Harness Mobile

电脑端 [DeepSeek Harness](https://github.com/zhuquan7237/deepseek-harness-desktop) 的安卓随身控制器：手机扫码或输码配对后，就能看会话、发提示词、切模型，离开电脑也不掉线。

## 项目定位

DeepSeek Harness 的主体跑在电脑上（真正的终端、文件、模型都在那里）。这个 App 是它的遥控器——**不重复实现 Agent，只做一块随身屏**：桥接插件在电脑端起一个本地 HTTP + WebSocket 服务，App 通过它读会话、下发提示词、切换模型。配对码 5 分钟有效，配对成功后拿到的是**按设备签发**的令牌，可随时在电脑端解除绑定。

## 功能

- **扫码 / 输码配对**：电脑端生成 5 分钟有效的配对码，App 内输入或直接扫码；一台电脑可绑定多台手机，一台手机也能记住多台电脑。
- **会话列表**：搜索、看运行状态与工作目录，随时进入任意会话，也能新建会话。
- **聊天**：读历史消息、发提示词，工具调用与结果、折叠的思考过程都能展开看；回答逐字显示，运行中的会话可中断。
- **实时连接**：WebSocket 推送消息与状态，顶部三态指示（连接中 / 在线 / 离线），断线自动重连。
- **模型管理**：查看与管理 provider（名称、Base URL、密钥是否已配置），在会话里切换模型；权限受配对时授予的 scope 限制。
- **自动更新**：启动后检查电脑端清单与 GitHub Releases，发现新版本可应用内下载、校验、覆盖安装。
- **深浅色主题**：跟随系统，也可手动指定。

## 安装

方式一，直接下载 APK（走自己的域名，国内可达）：

```
https://m.zhuquan.xyz/mobile/dsh-mobile.apk
```

方式二，从本仓库的 [Releases](https://github.com/zhuquan7237/deepseek-harness-mobile/releases) 下载最新 APK。

装之前请在手机上允许「安装未知来源应用」。系统要求 Android 8.0（API 26）及以上。

## 配对流程

1. 电脑端打开 DeepSeek Harness 的**设置 → 移动端（配对地址）**，点「生成配对码」，得到一个 5 分钟内有效的配对码（也可以直接复制配对链接）。
2. 手机打开 App，在配对页**输入配对码**，或点「扫码配对」扫电脑端展示的二维码。
3. 首次配对会请求相机权限（仅扫码需要，手输可不给）。配对成功后 App 记住令牌，下次打开自动回到会话列表。
4. 电脑端可以查看已绑定设备、解除绑定；手机丢失时在电脑端撤销即可，令牌立即失效。

> 电脑端默认通过隧道暴露 `https://m.zhuquan.xyz`；同一 Wi-Fi 下也可以直连 `http://<电脑局域网 IP>:17731`。

## 自动更新

App 启动后会同时检查两处，取版本更高的一方，任一可达即可：

1. **电脑端清单** `https://m.zhuquan.xyz/mobile/app-update.json`（由 `scripts/release.sh` 发布时写入，体积小、国内快）；
2. **GitHub Releases** 的本仓库最新 Release（tag 即版本号，取第一个 `.apk` 附件）。

发现新版本后可以在 App 内直接下载安装，下载完会**校验字节数与 sha256**，不匹配就丢弃重来，然后交给系统安装器覆盖安装。

⚠️ **签名说明**：APK 固定使用 debug key 签名（同一把 key 长期不变），所以覆盖升级要求新包必须是同一把 key 签的。如果你自己重新签名构建，App 内的更新会因签名不一致而安装失败，需要先卸载旧版本。

## 从源码构建

依赖：JDK 17、Android SDK（`compileSdk 35`）、Gradle wrapper 自带。

```bash
export JAVA_HOME="D:/hermes_workspace/tools/jdk-17.0.9+9"          # 换成你自己的 JDK 17
export ANDROID_HOME="C:/Users/<you>/AppData/Local/Android/Sdk"     # 换成你的 Android SDK

./gradlew.bat assembleDebug     # 调试包：app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat assembleRelease   # 发布包（R8 + 资源压缩，debug key 签名）
```

`local.properties` 里的 `sdk.dir` 也可以代替 `ANDROID_HOME`。首次构建会下载 Gradle 发行包与依赖。

## 发布流程

```bash
scripts/release.sh VERSION "NOTES_TEXT"

# 例如：
scripts/release.sh 0.2.0 "会话列表支持置顶；修复深色模式对比度问题。"
```

脚本会依次完成：校验参数与干净工作区 → 把 `app/build.gradle.kts` 的 `versionName` 改成 `VERSION`、`versionCode` 自增 1 → `assembleRelease` → 计算 sha256 并把 APK 复制到 `plugins/mobile-bridge/app/dsh-mobile.apk`（即 `https://m.zhuquan.xyz/mobile/dsh-mobile.apk` 的落盘位置）→ 写 `app-update.json` → 提交、打标签 `vVERSION`、推送 master 与标签 → 用 `gh` 创建 GitHub Release 并附上 APK。

桌面端本体在 [deepseek-harness-desktop](https://github.com/zhuquan7237/deepseek-harness-desktop) 仓库。
