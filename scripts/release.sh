#!/usr/bin/env bash
#
# release.sh — 构建、发布并打标签一个 DeepSeek Harness Mobile 版本。
#
# 用法：
#   scripts/release.sh VERSION "NOTES_TEXT"
#
# 示例：
#   scripts/release.sh 0.2.0 "会话列表支持置顶；修复部分机型深色模式下的对比度问题。"
#
#   VERSION     语义化版本号，如 0.2.0（带前导 v 会被去掉）
#   NOTES_TEXT  发布说明，一段自由文本（记得加引号）
#
# 依次做这些事：
#   1. 校验参数、要求工作区干净（分支必须是 master，且 vVERSION 标签尚不存在）
#   2. 把 app/build.gradle.kts 的 versionName 改成 VERSION，versionCode 自增 1
#   3. 用 JDK 17 + Android SDK 执行 ./gradlew.bat assembleRelease
#   4. 计算 APK 的 sha256 与字节数，复制到桥接插件的下载目录
#   5. 写 app-update.json（手机端自更新的清单文件）
#   6. commit "release vVERSION"、打标签 vVERSION、推送 master 与标签
#   7. gh release create 发布 GitHub Release 并附带 APK
#
set -euo pipefail

# ---------------------------------------------------------------- 常量
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_FILE="app/build.gradle.kts"
APK="app/build/outputs/apk/release/app-release.apk"
PUBLISH_DIR="C:/Users/zhuquan/AppData/Roaming/DeepSeek/plugins/mobile-bridge/app"
APK_NAME="dsh-mobile.apk"
APK_URL="https://m.zhuquan.xyz/mobile/dsh-mobile.apk"
REPO_SLUG="zhuquan7237/deepseek-harness-mobile"
RELEASE_BRANCH="master"

export JAVA_HOME="D:/hermes_workspace/tools/jdk-17.0.9+9"
export ANDROID_HOME="C:/Users/zhuquan/AppData/Local/Android/Sdk"

die() { printf 'release: %s\n' "$1" >&2; exit 1; }

usage() {
    printf '用法：scripts/release.sh VERSION "NOTES_TEXT"\n' >&2
    printf '示例：scripts/release.sh 0.2.0 "修复若干问题"\n' >&2
}

# ---------------------------------------------------------------- 1. 校验
if [ "$#" -ne 2 ]; then
    usage
    die "需要且只需要两个参数（收到 $# 个）"
fi

VERSION="${1#v}"
NOTES="$2"

printf '%s' "$VERSION" | grep -Eq '^[0-9]+(\.[0-9]+){1,3}$' \
    || die "VERSION 必须是 0.2.0 这样的版本号（收到：$1）"
[ -n "$NOTES" ] || die "NOTES_TEXT 不能为空"

cd "$REPO_ROOT"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[ "$BRANCH" = "$RELEASE_BRANCH" ] || die "当前分支是 $BRANCH，发布必须在 $RELEASE_BRANCH 上进行"

if [ -n "$(git status --porcelain)" ]; then
    git status --short >&2
    die "工作区不干净：请先提交或还原上面的改动"
fi

if git rev-parse -q --verify "refs/tags/v$VERSION" >/dev/null; then
    die "标签 v$VERSION 已经存在，换个版本号或先删除旧标签"
fi

[ -f "$GRADLE_FILE" ] || die "找不到 $GRADLE_FILE（请在仓库根目录运行）"
[ -d "$ANDROID_HOME" ] || die "ANDROID_HOME 不存在：$ANDROID_HOME"
[ -x "$JAVA_HOME/bin/java.exe" ] || die "JAVA_HOME 下没有 java：$JAVA_HOME"

PY_BIN="$(command -v python || command -v python3 || true)"
[ -n "$PY_BIN" ] || die "PATH 里找不到 python"
command -v sha256sum >/dev/null || die "PATH 里找不到 sha256sum"
command -v gh >/dev/null || die "PATH 里找不到 gh（GitHub CLI）"

# ---------------------------------------------------------------- 2. 版本号
NEW_CODE="$("$PY_BIN" - "$VERSION" "$GRADLE_FILE" <<'PY'
import pathlib
import re
import sys

version, path = sys.argv[1], sys.argv[2]
file = pathlib.Path(path)
text = file.read_text(encoding="utf-8")

code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
if code_match is None:
    sys.exit("release: app/build.gradle.kts 里找不到 versionCode")
new_code = int(code_match.group(1)) + 1
text = text[: code_match.start(1)] + str(new_code) + text[code_match.end(1) :]

name_match = re.search(r'versionName\s*=\s*"[^"]*"', text)
if name_match is None:
    sys.exit("release: app/build.gradle.kts 里找不到 versionName")
text = text[: name_match.start()] + 'versionName = "%s"' % version + text[name_match.end() :]

file.write_text(text, encoding="utf-8")
print(new_code)
PY
)"

printf '==> 版本 v%s（versionCode %s）\n' "$VERSION" "$NEW_CODE"

# ---------------------------------------------------------------- 3. 构建
printf '==> 构建 release APK\n'
./gradlew.bat assembleRelease

[ -f "$APK" ] || die "构建结束但没有找到 $APK"

# ---------------------------------------------------------------- 4. 指纹与分发
SHA256="$(sha256sum "$APK" | cut -d' ' -f1)"
SIZE="$(wc -c < "$APK" | tr -d '[:space:]')"
[ -n "$SHA256" ] || die "sha256sum 没有输出"
[ "$SIZE" -gt 0 ] || die "APK 是空文件：$APK"

mkdir -p "$PUBLISH_DIR"
cp -f "$APK" "$PUBLISH_DIR/$APK_NAME"
printf '==> 已复制到 %s/%s（%s 字节）\n' "$PUBLISH_DIR" "$APK_NAME" "$SIZE"

# ---------------------------------------------------------------- 5. 更新清单
"$PY_BIN" - "$VERSION" "$NEW_CODE" "$APK_URL" "$SHA256" "$SIZE" "$NOTES" \
    "$PUBLISH_DIR/app-update.json" <<'PY'
import datetime
import json
import pathlib
import sys

version, code, url, sha256, size, notes, path = sys.argv[1:8]

doc = {
    "version": version,
    "versionCode": int(code),
    "apkUrl": url,
    "sha256": sha256,
    "size": int(size),
    "notes": notes,
    "publishedAt": datetime.datetime.now(datetime.timezone.utc).astimezone().isoformat(
        timespec="seconds"
    ),
}

target = pathlib.Path(path)
target.parent.mkdir(parents=True, exist_ok=True)
with target.open("w", encoding="utf-8") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("==> 已写入 %s" % path)
PY

# ---------------------------------------------------------------- 6. 提交与推送
printf '==> 提交并推送\n'
git commit -am "release v$VERSION"
git tag "v$VERSION"
git push origin "$RELEASE_BRANCH" --tags

# ---------------------------------------------------------------- 7. GitHub Release
printf '==> 发布 GitHub Release\n'
gh release create "v$VERSION" "$APK" --title "v$VERSION" --notes "$NOTES"

# ---------------------------------------------------------------- 8. 汇总
cat <<EOF

发布完成 v$VERSION
  versionCode : $NEW_CODE
  APK         : $APK
  大小        : $SIZE 字节
  sha256      : $SHA256
  公网下载    : $APK_URL
  GitHub 发布 : https://github.com/$REPO_SLUG/releases/tag/v$VERSION
  更新清单    : $PUBLISH_DIR/app-update.json
EOF
