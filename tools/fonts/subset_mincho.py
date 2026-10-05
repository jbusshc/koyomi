"""Recorta Zen Old Mincho (SIL OFL 1.1) a lo que Koyomi puede llegar a escribir con ella.

La fuente entera pesa 5,4 MB, más que el resto de la app junto. Se quedan el latín con sus
acentos, los kana, la puntuación japonesa y los kanji del nivel 1 de JIS X 0208 (los 2965 de uso
corriente). Un kanji raro en el título de un evento sale con la fuente del teléfono.

    python tools/fonts/subset_mincho.py ZenOldMincho-Regular.ttf app/src/main/res/font/zen_old_mincho_subset.ttf

Necesita fontTools (pip install fonttools).
"""
import sys

from fontTools import subset
from fontTools.ttLib import TTFont


def jis_level_1():
    # Filas 16 a 47 de JIS X 0208: los kanji de nivel 1, en orden de lectura
    for row in range(16, 48):
        for cell in range(1, 95):
            try:
                yield bytes([row + 0xA0, cell + 0xA0]).decode("euc_jp")
            except UnicodeDecodeError:
                pass


def wanted():
    chars = set(jis_level_1())
    chars.update(chr(c) for c in range(0x20, 0x7F))      # ASCII
    chars.update(chr(c) for c in range(0xA0, 0x180))     # latín con acentos
    chars.update(chr(c) for c in range(0x3000, 0x3100))  # puntuación japonesa, hiragana, katakana
    chars.update(chr(c) for c in range(0xFF00, 0xFFF0))  # formas de ancho completo
    chars.update("〇々〆〜・…‥—–‘’“”※")
    # Lo que la propia app escribe y cae fuera del nivel 1
    chars.update("曜睦彌皐")
    return chars


def main(source, target):
    options = subset.Options()
    options.layout_features = ["*"]  # incluye vert/vrt2, por si la fecha vertical las usa
    options.name_IDs = ["*"]
    options.notdef_outline = True
    font = TTFont(source)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=[ord(c) for c in wanted()])
    subsetter.subset(font)
    font.save(target)
    cmap = font.getBestCmap()
    print(f"{len(cmap)} caracteres")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
