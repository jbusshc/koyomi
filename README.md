# Koyomi 暦

Una pequeña capa japonesa sobre el teléfono: la fecha, el calendario, la hora y el tiempo escritos
en japonés, sin cambiar el idioma de Android. Lee el calendario que ya tienes y lo presenta con
和暦, 曜日, 祝日 y la estética de cada tema.

Hermana pequeña de Tateyoko: mismas versiones de Kotlin, Compose y Gradle, misma convención de
módulos y de pruebas, y ningún código compartido.

## Qué hace

| | |
| --- | --- |
| **今日** | La fecha en grande, la hora, el tiempo con tres días, y los eventos de hoy y mañana |
| **月** | El mes a la japonesa (domingo primero, 祝日 en rojo) y el panel de cada día |
| **変換** | 西暦 ↔ 和暦, 漢数字, 干支, 旧暦, 六曜, 二十四節気 y la lectura en kana |
| **設定** | Tema, claro u oscuro, formatos, ciudad del tiempo, aprendizaje, 暦注 y calendarios |

Temas, cada uno claro y oscuro: **Standard**, **桜** (pasteles y flor de cerezo), **藍** (añil con
las olas 青海波 y dorado), **夜景** (ciudad de noche en neón), **和風** (papel, tinta y bermellón) y
**RPG** (ventana de menú de RPG; se puede ajustar el color de las ventanas y el grosor del marco).
Cada tema decide también cómo se dibuja cada pieza (`KoyomiSkin`), no solo sus colores, y el día de
la semana es siempre protagonista.

Widgets: **Resumen** y **Reloj**. El Resumen se configura al ponerlo (y al volver a configurarlo):

- Contenido: automático (enseña más cuanto más grande es), solo la fecha, el tiempo, la agenda o el
  mes entero con sus eventos; y qué piezas mostrar (tiempo, tres días, eventos, mes, lecturas).
- Actualización: cada 30 minutos, 1, 3, 6 o 12 horas, o solo a mano; un botón ↻ en la esquina pide
  el tiempo al instante. Sin eventos hoy, la lista sigue con los próximos días.
- Aspecto: tema y modo propios o los de la app, y fondo pleno, de cristal o sin fondo.

El Resumen se ofrece en tres tamaños de salida (2×2, 4×2 y 4×4): son el mismo widget y se pueden
redimensionar después. El del mes abre la app en el mes.

## Estructura

```
engine/   Kotlin puro (sin Android): eras, formato, 祝日, 旧暦, lecturas, eventos por día, iconos
app/      Compose, Calendar Provider, DataStore, widgets
tools/    Generadores de datos: kyureki (tablas astronómicas), fonts (recorte), icon (el sello)
```

El motor se prueba entero en la JVM, sin emulador. Las reglas que se pueden romper sin que se note
(la era según la fecha exacta, los eventos de todo el día en otra zona, las lecturas irregulares)
tienen sus pruebas.

## Compilar y probar

Hace falta un JDK 17 o más. En esta máquina el JDK no está en el `PATH`:

```
export JAVA_HOME="/c/Users/Jorge/.jdks/jdk-21.0.12.1+1"
./gradlew test                  # el motor
./gradlew assembleDebug         # app.koyomi, con la herramienta de pruebas de widgets
./gradlew :app:betaApk          # APK optimizado con su versión en dist/
```

La build **staging** (`app.koyomi.staging`, «Koyomi β») es la de publicación tal cual, con R8 y
recorte de recursos, y se instala junto a la de depuración. **Prueba siempre en ella**: R8 mueve
las clases de paquete y ya rompió una vez la carga de las tablas del motor.

### Ver un widget a varios tamaños

La build de depuración trae un receptor que pinta un widget a los tamaños que se pidan y deja los
PNG en la carpeta de archivos de la app (`kind`: auto, date, weather, agenda, month, icons; `--es bg
solid|glass|clear`):

```
adb shell am broadcast -n app.koyomi/app.koyomi.debug.RenderReceiver \
    --es kind auto --es sizes 168x115,347x244 --es skin JRPG --es mode DARK \
    --es place Tokio:35.68:139.69
adb exec-out run-as app.koyomi cat files/auto_JRPG_DARK_168x115.png > salida.png
```

`kind` es `summary`, `weather` o `icons` (la hoja de iconos del tiempo). Los tamaños son los que da
el lanzador, en dp.

## Privacidad

- Solo lectura del calendario; nunca escribe en él.
- Los eventos no salen del teléfono. Crear uno abre el formulario de la app de calendario.
- **Lo único que usa internet es el tiempo**, y solo si se elige una ciudad: se envían sus
  coordenadas, redondeadas, a Open-Meteo. El paquete `weather` no ve el calendario.
- Sin cuentas, sin anuncios, sin analítica.

## Datos y licencias

- 祝日: tabla de la Oficina del Gabinete (内閣府) hasta 2021; desde 2022, las reglas de la ley vigente.
- 旧暦, 六曜 y 二十四節気: calculados con `tools/kyureki` para 1990–2050 (los equinoccios coinciden
  con los festivos oficiales).
- Fuentes: DotGothic16 y Zen Old Mincho (recortada), ambas SIL OFL 1.1. Las licencias van dentro de
  la app, en 設定 → ライセンス.
- El tiempo: Open-Meteo.com, CC BY 4.0. **Su servicio gratuito es solo para uso no comercial**: si
  la app se vende, hay que pagarlo o cambiar de proveedor. El código de red está aislado para que el
  cambio sea pequeño.

## Qué queda fuera, a propósito

Crear o editar eventos, traducir los títulos de los eventos, cuentas, sincronización, tareas y
notificaciones propias.
