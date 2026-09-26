"""Turn the shared Steady SVGs into Android VectorDrawables.

Three things happen in the conversion and each is a design decision, not a format
detail:

1. The 240x180 (4:3) drawing is re-framed into a 320x180 (16:9) viewport with the
   figure centred, because every tile and every hero in this app is 16:9 and the
   artwork has to reach both edges rather than sit letterboxed inside a rectangle.
2. The floor line, which is the first path in every source file, is redrawn from
   x=0 to x=320 so it genuinely bleeds off both sides of the tile.
3. The flat single-colour line art is split into four tones: floor, chair, body and
   the part of the body that moves. Only the moving part is ember, which is the one
   thing somebody across a room needs to pick out of the drawing.
"""
import math
import os
import re

# SRC is the folder of hand-drawn source SVGs this app's vector drawables were
# converted from; it lives outside this repo and is not shipped here (the
# converted output in OUT is what ships). OUT is this app's own drawable
# resource directory, relative to this file's location in tools/.
SRC = "./source-svgs"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")

FLOOR = ("#8A7A66", 4.0)
CHAIR = ("#C9BFAE", 5.0)
BODY = ("#F3ECDD", 6.0)
HOT = ("#E0A458", 7.5)

# index -> role, read off the source files. Keyed by index rather than by guessing
# from the path data so that editing a source SVG shows up as a wrong-looking
# drawing rather than as a silently mistinted one.
ROLES = {
    "ex-ankle-pumps":       ("art_cs_ankle",          {3, 4, 5, 6, 7},    {11, 12, 13, 14}),
    "ex-calf-raises":       ("art_st_calf_raise",     {4, 5, 6, 7, 8},    {10, 11, 12}),
    "ex-heel-to-toe":       ("art_st_heel_toe",       {4, 5, 6, 7, 8},    {10, 12}),
    "ex-one-leg-stand":     ("art_st_single_leg",     {4, 5, 6, 7, 8},    {11, 12, 13, 14, 15}),
    "ex-seated-arm-raises": ("art_cs_arm_raise",      {3, 4, 5, 6, 7},    {8, 12, 13}),
    "ex-seated-marching":   ("art_cs_march",          {3, 4, 5, 6, 7},    {12, 13, 14, 15, 16}),
    "ex-seated-side-reach": ("art_cs_side_bend",      {1, 2, 3, 4, 5, 6}, {8, 9, 13, 14}),
    "ex-side-steps":        ("art_st_side_step",      {1, 2, 3, 4, 5, 6}, {12, 13, 14, 15}),
    "ex-sit-to-stand":      ("art_cs_sit_to_stand",   {1, 2, 3, 4, 5},    {7, 12, 13}),
    "ex-standing-march":    ("art_st_march",          {4, 5, 6, 7, 8},    {11, 12, 13, 14, 15}),
    "session-ready":        ("art_session_ready",     {4, 5, 6, 7, 8},    set()),
    "session-complete":     ("art_session_complete",  set(),              {3, 4, 9, 10}),
}

GAUGE = {
    "feeling-too-easy": "art_feeling_too_easy",
    "feeling-just-right": "art_feeling_just_right",
    "feeling-too-much": "art_feeling_too_much",
}

HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Generated from the hand-drawn source SVG %s.svg (see tools/svg-to-vector.py).\n'
          '     Regenerate rather than hand-edit. -->\n')


def attr(s, name):
    return float(re.search(r'%s="([-0-9.]+)"' % name, s).group(1))


def circle_to_path(s):
    cx, cy, r = attr(s, "cx"), attr(s, "cy"), attr(s, "r")
    return ("M %g %g a %g %g 0 1 0 %g 0 a %g %g 0 1 0 %g 0"
            % (cx - r, cy, r, r, 2 * r, r, r, -2 * r))


def rect_to_path(s):
    x, y, w, h = attr(s, "x"), attr(s, "y"), attr(s, "width"), attr(s, "height")
    r = attr(s, "rx")
    return ("M %g %g H %g A %g %g 0 0 1 %g %g V %g A %g %g 0 0 1 %g %g H %g "
            "A %g %g 0 0 1 %g %g V %g A %g %g 0 0 1 %g %g Z"
            % (x + r, y, x + w - r, r, r, x + w, y + r, y + h - r, r, r,
               x + w - r, y + h, x + r, r, r, x, y + h - r, y + r, r, r, x + r, y))


def elements(path):
    body = open(path).read().split("</title>")[1]
    out = []
    for tag, attrs in re.findall(r"<(path|circle|rect)\b([^/>]*)/?>", body):
        if tag == "path":
            out.append(re.search(r'd="([^"]+)"', attrs).group(1))
        elif tag == "circle":
            out.append(circle_to_path(attrs))
        else:
            out.append(rect_to_path(attrs))
    return out


def stroke(d, colour, width, fill=None):
    fill_attr = '\n        android:fillColor="%s"' % fill if fill else ""
    return ('    <path\n'
            '        android:pathData="%s"%s\n'
            '        android:strokeColor="%s"\n'
            '        android:strokeWidth="%s"\n'
            '        android:strokeLineCap="round"\n'
            '        android:strokeLineJoin="round" />\n' % (d, fill_attr, colour, width))


def write_figure(stem):
    name, chair, hot = ROLES[stem]
    els = elements(os.path.join(SRC, stem + ".svg"))
    xml = [HEADER % stem]
    xml.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
               '    android:width="320dp"\n'
               '    android:height="180dp"\n'
               '    android:viewportWidth="320"\n'
               '    android:viewportHeight="180">\n')
    # The ground, redrawn edge to edge rather than the source's inset 20..220.
    xml.append(stroke("M 0 164 L 320 164", FLOOR[0], FLOOR[1]))
    xml.append('    <group android:translateX="40">\n')
    for i, d in enumerate(els):
        if i == 0:
            continue
        colour, width = HOT if i in hot else CHAIR if i in chair else BODY
        xml.append("    " + stroke(d, colour, width).replace("\n", "\n    ").rstrip() + "\n")
    xml.append('    </group>\n</vector>\n')
    open(os.path.join(OUT, name + ".xml"), "w").write("".join(xml))
    return name


def write_gauge(stem):
    name = GAUGE[stem]
    line, bar = elements(os.path.join(SRC, stem + ".svg"))
    xml = [HEADER % stem]
    xml.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
               '    android:width="96dp"\n'
               '    android:height="96dp"\n'
               '    android:viewportWidth="96"\n'
               '    android:viewportHeight="96">\n')
    # The line is the level the session is pitched at; the bar is how it landed.
    xml.append(stroke(line, "#C9BFAE", 8))
    xml.append(stroke(bar, "#E0A458", 8, fill="#40E0A458"))
    xml.append('</vector>\n')
    open(os.path.join(OUT, name + ".xml"), "w").write("".join(xml))
    return name


os.makedirs(OUT, exist_ok=True)
written = [write_figure(s) for s in sorted(ROLES)] + [write_gauge(s) for s in sorted(GAUGE)]
print("\n".join(sorted(written)), "\n", len(written), "drawables")
