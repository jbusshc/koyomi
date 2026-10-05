"""Calcula las tablas del calendario antiguo (旧暦) que Koyomi lleva dentro.

El 六曜 de un día sale de su fecha en el calendario lunisolar, y esa fecha sale de cuándo cae cada
luna nueva y cada 中気 (los doce términos solares «principales») en hora de Japón. Es cálculo
astronómico: se hace aquí una vez y la app solo consulta el resultado.

    python tools/kyureki/make_kyureki.py engine/src/main/resources/app/koyomi/engine/kyureki

Escribe dos archivos:
  kyureki.csv  una línea por mes lunar: día en que empieza, número de mes, 1 si es intercalar (閏月)
  sekki.csv    una línea por término solar (二十四節気): día, índice (0 = 春分, de 15° en 15°)

Las reglas son las del calendario 天保暦, como se sigue aplicando por costumbre: el mes que contiene
el 冬至 es el 11, y en un año de trece lunas es intercalar la primera sin 中気. Para 2033-2034 esa
regla no basta y no hay acuerdo oficial; lo que sale aquí es una de las lecturas posibles.

Necesita ephem (pip install ephem).
"""
import datetime as dt
import math
import os
import sys

import ephem

FIRST_YEAR = 1990
LAST_YEAR = 2050
JST = 9 * ephem.hour


def jst_date(moment):
    """El día de Japón en que cae un instante."""
    return ephem.Date(moment + JST).datetime().date()


def sun_longitude(moment):
    """Longitud eclíptica aparente del Sol, en grados, para la fecha."""
    sun = ephem.Sun(moment)
    return math.degrees(ephem.Ecliptic(sun, epoch=moment).lon) % 360


def term_moment(year, index):
    """Cuándo llega el Sol a 15°·index dentro de [year]. El 0 es el 春分, hacia el 20 de marzo."""
    target = 15.0 * index
    guess = ephem.Date(dt.datetime(year, 3, 20, 12)) + ((index * 15.2184) if index < 19 else (index - 24) * 15.2184)
    low, high = guess - 4, guess + 4

    def offset(moment):
        return (sun_longitude(moment) - target + 180) % 360 - 180

    for _ in range(60):
        mid = (low + high) / 2
        if offset(mid) < 0:
            low = mid
        else:
            high = mid
    return ephem.Date((low + high) / 2)


def new_moons():
    moment = ephem.Date(dt.datetime(FIRST_YEAR - 1, 10, 1))
    end = ephem.Date(dt.datetime(LAST_YEAR + 1, 4, 1))
    days = []
    while moment < end:
        moment = ephem.next_new_moon(moment)
        days.append(jst_date(moment))
        moment = ephem.Date(moment + 1)
    return days


def main(target):
    terms = []  # (día, índice)
    for year in range(FIRST_YEAR - 1, LAST_YEAR + 2):
        for index in range(24):
            terms.append((jst_date(term_moment(year, index)), index))
    terms.sort()

    starts = new_moons()
    # Los 中気 son los términos pares: 春分 (0), 穀雨 (2)… El 冬至 es el 18
    chuki = [[index for day, index in terms if index % 2 == 0 and start <= day < end] for start, end in zip(starts, starts[1:])]
    solstices = [i for i, found in enumerate(chuki) if 18 in found]

    months = {}  # posición -> (número, intercalar)
    for a, b in zip(solstices, solstices[1:]):
        months[a] = (11, False)
        number = 11
        leap_used = b - a == 12
        for i in range(a + 1, b):
            if not leap_used and not chuki[i]:
                months[i] = (number, True)
                leap_used = True
            else:
                number = number % 12 + 1
                months[i] = (number, False)
        assert number == 10, (starts[a], number)
    # El último mes del 冬至 cierra la tabla: no tiene tramo propio que lo numere
    months[solstices[-1]] = (11, False)

    os.makedirs(target, exist_ok=True)
    first, last = dt.date(FIRST_YEAR, 1, 1), dt.date(LAST_YEAR, 12, 31)
    with open(os.path.join(target, "kyureki.csv"), "w", encoding="utf-8", newline="\n") as out:
        for i in sorted(months):
            # Hace falta el mes en curso el 1 de enero del primer año, y nada después del último
            if starts[i + 1] <= first or starts[i] > last:
                continue
            number, leap = months[i]
            out.write(f"{starts[i].isoformat()},{number},{1 if leap else 0}\n")
    with open(os.path.join(target, "sekki.csv"), "w", encoding="utf-8", newline="\n") as out:
        for day, index in terms:
            if first <= day <= last:
                out.write(f"{day.isoformat()},{index}\n")
    print(f"{len(months)} meses, {sum(1 for d, _ in terms if first <= d <= last)} términos")


if __name__ == "__main__":
    main(sys.argv[1])
