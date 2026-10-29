#!/usr/bin/env python3
"""从一张白底的吉祥物图生成全部 App 图标资源。

用法：
    python scripts/make-app-icon.py [源图路径]

默认源图 assets/icon/source.png（白底、纯色扁平插画）。

产物：
  Android 自适应图标（108dp 画布，内容占 60%）
    app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_background.png
    app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png
  iOS（不透明，系统自己套圆角）
    ios/DshMobile/Assets.xcassets/AppIcon.appiconset/icon-1024.png

设计约定（改图标时沿用，别凭感觉调）：
  * 自适应图标的可见区 = 中央 72/108dp，圆形遮罩的安全区只有 66dp 直径。
    **遮罩作用在「中央 72dp 再放大」之后**，所以内容宽 0.60×108dp 实际会占满可见
    区的 90%，圆形遮罩会把右下那撮卷发尖削掉 1~2%。素材最远点到中心的距离 ≈
    0.604 × 内容宽（实测），取 0.55 → Rmax≈35.9dp，正好落进 36dp 的圆形半径里
    （0.56 起会削掉 20 来个像素的发梢尖，0.60 会削 320 个），可见区占比 82.5%。
  * 素材是「白底 + 白色脸部」的画法，抠图必须用**从边缘漫水填充**，不能按颜色
    删白：脸部/白色荷叶边是封闭区域，删了整张图就散了。
  * 背景取 #A9C7F2：白脸和白荷叶边在这个蓝上才有体积感（#EAF3FF 那种近白底会
    让白色整片消失，48px 下只剩线稿）。
"""

from __future__ import annotations

import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = REPO / "assets" / "icon" / "source.png"

BG = (169, 199, 242)          # #A9C7F2
NEAR_WHITE = 232              # 通道最小值 ≥ 此值算「白」
ANDROID_CONTENT = 0.55        # 内容宽 / 108dp 画布
ANDROID_DY = 0.0              # 上下留白靠素材自身的包围盒对齐即可，别再手动偏移
IOS_CONTENT = 0.78
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def cut_out(path: Path) -> Image.Image:
    """去掉外圈白底，保留脸/荷叶边这些封闭的白色区域，返回裁到内容框的 RGBA。"""
    im = Image.open(path).convert("RGB")
    a = np.array(im).astype(np.int16)
    h, w = a.shape[:2]

    white = a.min(axis=2) >= NEAR_WHITE
    outside = np.zeros((h, w), bool)
    queue: deque[tuple[int, int]] = deque()

    def push(y: int, x: int) -> None:
        if white[y, x] and not outside[y, x]:
            outside[y, x] = True
            queue.append((y, x))

    for x in range(w):
        push(0, x)
        push(h - 1, x)
    for y in range(h):
        push(y, 0)
        push(y, w - 1)

    while queue:
        y, x = queue.popleft()
        if y > 0:
            push(y - 1, x)
        if y < h - 1:
            push(y + 1, x)
        if x > 0:
            push(y, x - 1)
        if x < w - 1:
            push(y, x + 1)

    rgba = np.dstack([np.array(im), np.where(outside, 0, 255).astype(np.uint8)])
    return Image.fromarray(rgba, "RGBA").crop(Image.fromarray(rgba, "RGBA").getbbox())


def render(art: Image.Image, size: int, content: float, dy: float = 0.0, opaque: bool = False) -> Image.Image:
    """把素材按 content（内容宽 / 画布）居中放到画布上。

    opaque=False（自适应图标的前景层）必须留透明底：启动器会单独给前景做视差位移，
    前景自带底色的话位移时就会露出自己的色块。
    """
    canvas = Image.new("RGBA", (size, size), BG + (255,) if opaque else (0, 0, 0, 0))
    aw, ah = art.size
    tw = max(1, round(size * content))
    th = max(1, round(tw * ah / aw))
    resized = art.resize((tw, th), Image.LANCZOS)
    canvas.alpha_composite(resized, ((size - tw) // 2, (size - th) // 2 + round(dy * size)))
    return canvas


def main() -> int:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SOURCE
    if not src.exists():
        print(f"找不到源图：{src}", file=sys.stderr)
        return 1

    art = cut_out(src)
    print(f"素材：{src.name} → 内容框 {art.size}（已抠掉外圈白底）")

    # Android：背景与前景分开，前景画布 = 108dp × 密度
    for name, scale in DENSITIES.items():
        size = round(108 * scale)
        out_dir = REPO / "app" / "src" / "main" / "res" / f"mipmap-{name}"
        out_dir.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (size, size), BG).save(out_dir / "ic_launcher_background.png")
        render(art, size, ANDROID_CONTENT, ANDROID_DY).save(out_dir / "ic_launcher_foreground.png")
        print(f"  mipmap-{name}: {size}×{size}")

    # iOS：不透明整幅（系统自己切圆角），内容比 Android 大一些
    ios = REPO / "ios" / "DshMobile" / "Assets.xcassets" / "AppIcon.appiconset" / "icon-1024.png"
    if ios.parent.exists():
        render(art, 1024, IOS_CONTENT, opaque=True).convert("RGB").save(ios)
        print(f"  iOS: {ios.relative_to(REPO)}")

    # 预览图：一起入库，改图标时在 PR/Release 里就能直接核对
    # ⚠️ 启动器的真实规则是「先取中央 72/108 的可见区，再套遮罩，再放大」——
    # 直接给整张 108dp 画布套遮罩会让人误判内容占比（曾据此把内容定得过大）。
    preview = REPO / "docs" / "app-icon-preview.png"
    preview.parent.mkdir(parents=True, exist_ok=True)

    def android_tile(size: int, shape: str) -> Image.Image:
        full = Image.alpha_composite(
            Image.new("RGBA", (432, 432), BG + (255,)),
            render(art, 432, ANDROID_CONTENT, ANDROID_DY),
        )
        inset = round(432 * 18 / 108)
        visible = full.crop((inset, inset, 432 - inset, 432 - inset))
        side = visible.size[0]
        mask = Image.new("L", (side, side), 0)
        if shape == "circle":
            ImageDraw.Draw(mask).ellipse([0, 0, side - 1, side - 1], fill=255)
        else:
            ImageDraw.Draw(mask).rounded_rectangle(
                [0, 0, side - 1, side - 1], radius=round(side * 0.22), fill=255
            )
        visible.putalpha(Image.composite(visible.getchannel("A"), Image.new("L", (side, side), 0), mask))
        return visible.resize((size, size), Image.LANCZOS)

    tiles = [
        ("圆形 48px", android_tile(48, "circle"), None),
        ("圆形 96px", android_tile(96, "circle"), None),
        ("圆形 192px", android_tile(192, "circle"), None),
        ("方圆 192px", android_tile(192, "squircle"), None),
        ("iOS 1024", render(art, 1024, IOS_CONTENT, opaque=True).convert("RGB"), None),
    ]
    pad, top = 16, 26
    width = pad + sum(t[1].size[0] + pad for t in tiles)
    height = top + max(t[1].size[1] for t in tiles) + pad
    sheet = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(sheet)
    x = pad
    for label, tile, _ in tiles:
        side = tile.size[0]
        sheet.paste(tile, (x, top), tile if tile.mode == "RGBA" else None)
        draw.text((x, 8), label, fill=(0, 0, 0))
        x += side + pad
    sheet.save(preview)
    print(f"  预览：{preview.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
