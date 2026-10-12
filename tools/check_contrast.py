#!/usr/bin/env python3
"""Colour contrast of the Glow theme (D-099, NFR-08), run locally and in CI.

Reads the colour tokens of DarkGlow and LightGlow in app/.../ui/theme/Theme.kt and checks every pair the screens use against the
WCAG 2.1 ratios: 4.5:1 for normal text, 3:1 for large text (18 sp, or 14 sp bold, as Android's Accessibility Scanner counts it;
the button's words are 18 sp extra bold) and for icons.
Translucent colours (glass, tints) are composited over the background first.

    python3 tools/check_contrast.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEME = ROOT / "app/src/main/kotlin/com/personalfitnesscoach/app/ui/theme/Theme.kt"


def tokens(name, src):
    block = re.search(r"val " + name + r" = GlowColors\((.*?)\n\)", src, re.S).group(1)
    return {k: int(v, 16) for k, v in re.findall(r"(\w+) = Color\(0x([0-9A-Fa-f]{8})\)", block)}


def rgba(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, ((c >> 24) & 255) / 255.0)


def over(top, bottom):
    r, g, b, a = rgba(top)
    R, G, B, _ = rgba(bottom)
    return (round(r * a + R * (1 - a)), round(g * a + G * (1 - a)), round(b * a + B * (1 - a)))


def lum(rgb):
    def ch(v):
        v /= 255.0
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def ratio(a, b):
    la, lb = sorted([lum(a), lum(b)], reverse=True)
    return (la + 0.05) / (lb + 0.05)


def check(theme, t, problems, lines):
    bgs = {"background top": t["bgTop"], "background bottom": t["bgBottom"]}
    # Surfaces: the backgrounds, and the glass veils over the lighter (top) background.
    surfaces = {k: rgba(v)[:3] for k, v in bgs.items()}
    surfaces["glass card"] = over(t["glass"], t["bgTop"])
    surfaces["strong glass"] = over(t["glassStrong"], t["bgTop"])
    # (foreground token, minimum, what it is used for)
    texts = [("text", 4.5, "headings and body text"), ("muted", 4.5, "notes and labels"), ("link", 4.5, "Why? and other text actions"),
             ("caution", 4.5, "caution words"), ("alert", 4.5, "warning words"), ("cool", 3.0, "done marks, icons, large words")]
    for fg, need, use in texts:
        for sname, s in surfaces.items():
            r = ratio(rgba(t[fg])[:3], s)
            lines.append(f"{theme:5} {fg:8} on {sname:17} {r:5.2f}:1 (needs {need})")
            if r < need:
                problems.append(f"{theme}: {fg} on {sname} is {r:.2f}:1, needs {need}:1 ({use})")
    # Words on the tinted safety pills and cards.
    for fg, tint in (("caution", "cautionTint"), ("alert", "alertTint")):
        s = over(t[tint], t["bgTop"])
        r = ratio(rgba(t[fg])[:3], s)
        lines.append(f"{theme:5} {fg:8} on {tint:17} {r:5.2f}:1 (needs 4.5)")
        if r < 4.5:
            problems.append(f"{theme}: {fg} on {tint} is {r:.2f}:1, needs 4.5:1")
    # White on the main button: 18 sp extra bold is large text (3:1) across the whole gradient, ends included.
    for stop in ("warmStart", "warm", "warmEnd"):
        if stop == "warm":
            continue  # the accent colour, not part of the button gradient (warmBrush runs warmStart → warmEnd)
        r = ratio(rgba(t["onWarm"])[:3], rgba(t[stop])[:3])
        lines.append(f"{theme:5} onWarm   on {stop:17} {r:5.2f}:1 (needs 3.0, large bold text)")
        if r < 3.0:
            problems.append(f"{theme}: white on the button's {stop} is {r:.2f}:1, needs 3:1 (large bold text)")


def main():
    src = THEME.read_text(encoding="utf-8")
    problems, lines = [], []
    for name, theme in (("DarkGlow", "dark"), ("LightGlow", "light")):
        check(theme, tokens(name, src), problems, lines)
    if "-v" in sys.argv:
        print("\n".join(lines))
    if problems:
        print("\n".join(problems))
        print(f"FAIL: {len(problems)} contrast problem(s)")
        sys.exit(1)
    print(f"contrast OK: {len(lines)} colour pairs checked in the dark and light themes")


if __name__ == "__main__":
    main()
