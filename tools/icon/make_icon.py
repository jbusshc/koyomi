"""Genera el icono de Koyomi: el kanji 暦 de la mincho de la app, en un sello 朱 sobre papel.

El glifo se saca de la fuente y se escribe como ruta de vector, así que el icono no depende de
ninguna imagen. Zen Old Mincho es SIL OFL 1.1 (ver las licencias de la app).

    python tools/icon/make_icon.py ZenOldMincho-Regular.ttf app/src/main/res

Necesita fontTools (pip install fonttools).
"""
import os
import sys

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

GLYPH = "暦"
PAPER = "#F4EEDD"
SEAL = "#C73E3A"

# Un icono adaptable es de 108×108 y solo se garantiza la zona central de 66×66
SIZE = 108
SEAL_SIDE = 62
SEAL_RADIUS = 10
GLYPH_SIDE = 40


def glyph_path(font_path, side, center_x, center_y):
    """La ruta del glifo escalada a [side] y centrada en el punto dado, con el eje y hacia abajo."""
    font = TTFont(font_path)
    name = font.getBestCmap()[ord(GLYPH)]
    glyphs = font.getGlyphSet()
    bounds = BoundsPen(glyphs)
    glyphs[name].draw(bounds)
    x0, y0, x1, y1 = bounds.bounds
    scale = side / max(x1 - x0, y1 - y0)
    # y crece hacia arriba en la fuente y hacia abajo en un vector
    tx = center_x - (x0 + x1) / 2 * scale
    ty = center_y + (y0 + y1) / 2 * scale
    pen = SVGPathPen(glyphs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
    glyphs[name].draw(TransformPen(pen, (scale, 0, 0, -scale, tx, ty)))
    return pen.getCommands()


def rounded_square(cx, cy, side, radius):
    x0, y0, x1, y1 = cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2
    r = radius
    return (
        f"M{x0 + r},{y0}H{x1 - r}a{r},{r} 0 0 1 {r},{r}V{y1 - r}a{r},{r} 0 0 1 {-r},{r}"
        f"H{x0 + r}a{r},{r} 0 0 1 {-r},{-r}V{y0 + r}a{r},{r} 0 0 1 {r},{-r}z"
    )


def vector(*paths):
    body = "\n".join(f'    <path android:fillColor="{color}" android:pathData="{data}" />' for color, data in paths)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generado por tools/icon/make_icon.py; 暦 de Zen Old Mincho (SIL OFL 1.1). No editar a mano. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{SIZE}dp"\n    android:height="{SIZE}dp"\n'
        f'    android:viewportWidth="{SIZE}"\n    android:viewportHeight="{SIZE}">\n'
        f"{body}\n</vector>\n"
    )


def main(font_path, res):
    c = SIZE / 2
    drawable = os.path.join(res, "drawable")
    os.makedirs(drawable, exist_ok=True)

    def write(name, text):
        with open(os.path.join(drawable, name), "w", encoding="utf-8", newline="\n") as out:
            out.write(text)

    write("ic_launcher_background.xml", vector((PAPER, f"M0,0h{SIZE}v{SIZE}h-{SIZE}z")))
    # El sello con el glifo en el color del papel: se recorta del sello con un trazo de relleno par-impar
    write(
        "ic_launcher_foreground.xml",
        vector(
            (SEAL, rounded_square(c, c, SEAL_SIDE, SEAL_RADIUS)),
            (PAPER, glyph_path(font_path, GLYPH_SIDE, c, c)),
        ),
    )
    # Los iconos con tema de Android 13 pintan una sola silueta: el glifo, algo más grande
    write("ic_launcher_monochrome.xml", vector(("#FF000000", glyph_path(font_path, GLYPH_SIDE + 12, c, c))))
    print("icono escrito en", drawable)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
