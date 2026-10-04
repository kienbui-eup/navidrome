#!/usr/bin/env python3
"""Troly Play identity builder (v1).

Extends Troly v2 (brand/source/build_identity.py in kienbui-eup/troly): same T mark,
same Be Vietnam Pro SemiBold outlines, same wordmark geometry. What is new:
  * the T's square dot becomes a rounded Play triangle in the SAME 24x24 box
  * suffixes "roly Play" / "rợ lý giải trí"
  * app icon with a Coral play triangle, TV banner, content-type icons

Usage:  python3 build_identity.py [--out ../svg] [--png ../png]
"""
import argparse, os, math
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, "BeVietnamPro-SemiBold.ttf")

# ---- Troly v2 primitives (brand/v2/tokens.json) + Troly Play additions ----
INK = "#20223A"; SNOW = "#F3F4FC"; COBALT = "#4055E8"; WHITE = "#FFFFFF"
NIGHT = "#111322"; PAPER = "#FAF8F2"; MIST = "#A5B4FF"; PEACH = "#F2B59B"
CORAL = "#FF6B4A"          # coral-500 — the Play colour (dark theme)
CORAL_700 = "#B93C1E"      # coral-700 — the Play colour on light grounds
STAGE = "#0B0C18"          # stage-950 — player / fullscreen ground

# The Troly T (master vector v2), 112 units tall. The square dot sits at (63,36) 24x24 rx5.
T_PATH = ("M25 36V28C25 16 35 11 48 7L65 1Q69 0 69 4V16C69 25 64 29 56 32V81C56 92 60 97 65 99V110"
          "Q65 112 61 112C37 112 25 99 25 82V57H14Q8 57 8 51V43Q8 36 15 36Z")
DOT = (63, 36, 24, 24, 5)  # x y w h rx

def play_triangle(x, y, w, h, r=4.0):
    """Rounded play triangle filling the dot box (x,y,w,h). Corner radius r, apex points right."""
    # triangle vertices
    A = (x, y); B = (x + w, y + h / 2); C = (x, y + h)
    def unit(p, q):
        dx, dy = q[0]-p[0], q[1]-p[1]; L = math.hypot(dx, dy); return dx/L, dy/L
    pts = [A, B, C]
    d = []
    for i, P in enumerate(pts):
        prev, nxt = pts[i-1], pts[(i+1) % 3]
        u1 = unit(P, prev); u2 = unit(P, nxt)
        # half-angle at P → distance from vertex to tangent point
        cosang = u1[0]*u2[0] + u1[1]*u2[1]
        ang = math.acos(max(-1, min(1, cosang)))
        t = r / math.tan(ang / 2)
        p1 = (P[0] + u1[0]*t, P[1] + u1[1]*t)
        p2 = (P[0] + u2[0]*t, P[1] + u2[1]*t)
        d.append((p1, P, p2))
    s = f"M{d[0][0][0]:.2f} {d[0][0][1]:.2f}"
    for i in range(3):
        p1, P, p2 = d[i]
        s += f"Q{P[0]:.2f} {P[1]:.2f} {p2[0]:.2f} {p2[1]:.2f}"
        n1 = d[(i+1) % 3][0]
        s += f"L{n1[0]:.2f} {n1[1]:.2f}"
    return s + "Z"

PLAY = play_triangle(*DOT[:4], r=4.5)

def mark(fill, play=True, dot_fill=None):
    """T mark group, 112 tall. play=True: Play triangle instead of the square dot."""
    dot_fill = dot_fill or fill
    if play:
        dot = f'<path fill="{dot_fill}" d="{PLAY}"/>'
    else:
        x, y, w, h, rx = DOT
        dot = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{dot_fill}"/>'
    return f'<g><path fill="{fill}" d="{T_PATH}"/>{dot}</g>'

# ---- text outlines (same geometry as Troly v2: translate(50 64) scale(0.064 -0.064)) ----
_font = TTFont(FONT); _cmap = _font.getBestCmap(); _glyphs = _font.getGlyphSet(); _hmtx = _font["hmtx"]
KERN = {}
try:
    from fontTools.ttLib.tables.otTables import PairPos  # noqa
except Exception:
    pass

def text_path(text, scale=0.064):
    """Return (svg path d in final coords, advance width in final units)."""
    x = 0.0; parts = []
    for ch in text:
        g = _cmap[ord(ch)]
        pen = SVGPathPen(_glyphs)
        tp = TransformPen(pen, (1, 0, 0, 1, x, 0))
        _glyphs[g].draw(tp)
        d = pen.getCommands()
        if d: parts.append(d)
        x += _hmtx[g][0]
    return " ".join(parts), x * scale

def wordmark(suffix, fill, label, play=True, dot_fill=None):
    d, adv = text_path(suffix)
    width = 50 + adv + 4
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.3f}" height="84" viewBox="0 0 {width:.3f} 84" '
            f'role="img" aria-label="{label}" data-identity="troly-play-v1" data-suffix="{suffix}">'
            f'<g transform="translate(4 8) scale(.5)">{mark(fill, play, dot_fill)}</g>'
            f'<path fill="{fill}" transform="translate(50 64) scale(0.064 -0.064)" d="{d}"/></svg>')

def icon_svg(fill, size=256, bg=None, play=True, dot_fill=None):
    s = size / 112 * 0.625  # same proportion as Troly app icon (scale 1.43 at 256)
    off = (size - 112 * s * 0.75) / 2
    bgrect = f'<rect width="{size}" height="{size}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}" '
            f'role="img" aria-label="Troly Play" data-identity="troly-play-v1">{bgrect}'
            f'<g transform="translate({size*0.238:.1f} {size*0.1875:.1f}) scale({size/179:.4f})">{mark(fill, play, dot_fill)}</g></svg>')

def tv_banner(bg=COBALT, fill=WHITE, dot=CORAL):
    """Android TV / Kodi banner 320x180: the contiguous wordmark ([T]roly Play) + tagline. Same geometry as wordmark()."""
    d, adv = text_path("roly Play", scale=1.0)
    k = min(0.95, 276 / (92 + adv * 0.128))        # fit 320 - 24 left - 20 right
    tx, ty = 22 + 92 * k, 42 + 112 * k             # text origin relative to the mark origin (wordmark: 46/.5, 56/.5)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180" viewBox="0 0 320 180" role="img" aria-label="Troly Play">'
            f'<rect width="320" height="180" fill="{bg}"/>'
            f'<g transform="translate(22 42) scale({k:.4f})">{mark(fill, True, dot)}</g>'
            f'<path fill="{fill}" transform="translate({tx:.1f} {ty:.1f}) scale({0.128*k:.4f} -{0.128*k:.4f})" d="{d}"/>'
            f'<text x="{tx:.1f}" y="{ty+26:.1f}" font-family="Be Vietnam Pro, Inter, system-ui, sans-serif" font-size="12" font-weight="500" fill="{fill}" fill-opacity=".85">Trợ lý giải trí của cả nhà</text></svg>')

# ---- content-type icons, 24px, stroke 1.5 round, with the T's square dot where it fits ----
def icon_wrap(body, label):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" '
            f'stroke="{INK}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" role="img" aria-label="{label}">{body}</svg>')
ICONS = {
    "music":     icon_wrap('<path d="M9 18V6l10-2v12"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="16.5" cy="16" r="2.5"/>', "Nhạc"),
    "film":      icon_wrap('<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M7 5v14M17 5v14M3 10h4M3 14h4M17 10h4M17 14h4"/>', "Phim"),
    "radio":     icon_wrap('<rect x="3" y="9" width="18" height="11" rx="3"/><path d="M7 9l10-5"/><circle cx="8.5" cy="14.5" r="2"/><rect x="14" y="13.5" width="4" height="2" rx=".5" fill="currentColor" stroke="none"/>', "Radio"),
    "podcast":   icon_wrap('<rect x="9" y="3" width="6" height="10" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3M9 21h6"/>', "Podcast"),
    "audiobook": icon_wrap('<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H12v16H6.5A2.5 2.5 0 0 0 4 21z"/><path d="M20 5.5A2.5 2.5 0 0 0 17.5 3H12v16h5.5a2.5 2.5 0 0 1 2.5 2z"/><path d="M8 8h1.5M8 11h1.5"/>', "Sách nói"),
    "play":      icon_wrap(f'<path d="{play_triangle(6, 4, 14, 16, 2.5)}" fill="currentColor" stroke="none"/>', "Phát"),
    "hires":     icon_wrap('<path d="M3 12h2l2-5 3 10 3-8 2 5 2-2h4"/>', "Hi-Res"),
}

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--out", default=os.path.join(HERE, "..", "svg"))
    ap.add_argument("--png", default=None); a = ap.parse_args()
    out = os.path.abspath(a.out); os.makedirs(out, exist_ok=True)
    files = {}
    # wordmarks (EN + VI) in the four Troly colourways
    for tag, col in [("ink", INK), ("light", SNOW), ("white", WHITE), ("cobalt", COBALT)]:
        files[f"trolyplay-wordmark-{tag}.svg"] = wordmark("roly Play", col, "Troly Play")
        files[f"trolyplay-wordmark-vi-{tag}.svg"] = wordmark("rợ lý giải trí", col, "Trợ lý giải trí")
    # two-tone wordmark: Play triangle in Coral (dark grounds) / Coral-700 (light grounds)
    files["trolyplay-wordmark-light-coral.svg"] = wordmark("roly Play", SNOW, "Troly Play", dot_fill=CORAL)
    files["trolyplay-wordmark-ink-coral.svg"] = wordmark("roly Play", INK, "Troly Play", dot_fill=CORAL_700)
    files["trolyplay-wordmark-vi-light-coral.svg"] = wordmark("rợ lý giải trí", SNOW, "Trợ lý giải trí", dot_fill=CORAL)
    files["trolyplay-wordmark-vi-ink-coral.svg"] = wordmark("rợ lý giải trí", INK, "Trợ lý giải trí", dot_fill=CORAL_700)
    # marks
    files["playmark-ink.svg"] = icon_svg(INK, 112)
    files["playmark-light.svg"] = icon_svg(SNOW, 112)
    files["playmark-cobalt.svg"] = icon_svg(COBALT, 112)
    files["playmark-coral.svg"] = icon_svg(INK, 112, dot_fill=CORAL_700)
    files["playmark-light-coral.svg"] = icon_svg(SNOW, 112, dot_fill=CORAL)
    # app icons
    files["trolyplay-app-icon.svg"] = icon_svg(WHITE, 256, bg=COBALT, dot_fill=CORAL)
    files["trolyplay-app-icon-mono.svg"] = icon_svg(WHITE, 256, bg=COBALT)
    files["trolyplay-app-icon-night.svg"] = icon_svg(SNOW, 256, bg=STAGE, dot_fill=CORAL)
    files["trolyplay-tv-banner.svg"] = tv_banner()
    files["trolyplay-tray.svg"] = icon_svg("#000000", 112)
    files["trolyplay-tray-white.svg"] = icon_svg(WHITE, 112)
    for k, v in ICONS.items():
        files[f"icon-{k}.svg"] = v
    for name, svg in files.items():
        with open(os.path.join(out, name), "w", encoding="utf-8") as fh: fh.write(svg)
    print(f"{len(files)} SVG → {out}")
    if a.png:
        import cairosvg
        png = os.path.abspath(a.png); os.makedirs(png, exist_ok=True)
        app = files["trolyplay-app-icon.svg"].encode()
        for n, s in [("favicon-16x16.png", 16), ("favicon-32x32.png", 32), ("android-chrome-192x192.png", 192),
                     ("android-chrome-512x512.png", 512), ("apple-touch-icon.png", 180), ("apple-touch-icon-180x180.png", 180),
                     ("apple-touch-icon-152x152.png", 152), ("apple-touch-icon-120x120.png", 120), ("apple-touch-icon-76x76.png", 76),
                     ("apple-touch-icon-60x60.png", 60), ("mstile-70x70.png", 70), ("mstile-144x144.png", 144),
                     ("mstile-150x150.png", 150), ("mstile-310x310.png", 310), ("logo-192x192.png", 192)]:
            cairosvg.svg2png(bytestring=app, write_to=os.path.join(png, n), output_width=s, output_height=s)
        cairosvg.svg2png(bytestring=files["trolyplay-tv-banner.svg"].encode(), write_to=os.path.join(png, "mstile-310x150.png"), output_width=310, output_height=150)
        cairosvg.svg2png(bytestring=files["trolyplay-tv-banner.svg"].encode(), write_to=os.path.join(png, "tv-banner-320x180.png"), output_width=320, output_height=180)
        print(f"PNG → {png}")

if __name__ == "__main__":
    main()
