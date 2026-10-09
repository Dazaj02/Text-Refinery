# Diseño de la interfaz

Propuesta visual (lienzo «Text Refinery Interfaz») e implementada en JavaFX. El lienzo refleja la app tal
como está implementada:

| Tablero | Contenido |
|---|---|
| 1 · Limpieza | Perfiles, vista previa en vivo, procesadores. Ajustes de tablero: **tema** (claro/oscuro) e **idioma** (es/en) |
| 2 · Atajos e IA | Atajos con teclas, motor Ollama, modo de IA. Mismos ajustes de tema e idioma |
| 3 · General | Iniciar con el sistema, tema (se aplica al instante), idioma de la aplicación y del texto. «Guardar cambios» lleva a Limpieza, como en la app |
| 4 · Bandeja y sistema de color | Menú de bandeja (es/en), avisos, escala roja y tabla de variables claro/oscuro |
| 5 · Logo | Símbolo, variantes, tamaños reales |
| Variantes | Limpieza oscuro, Atajos e IA en inglés, General oscuro e inglés |

Los tableros 1–3 son interactivos y usan variables de color (`--bg`, `--ink`, `--accent-text`…) con los mismos nombres y valores que
`theme.css`, de modo que el diseño y el CSS se corresponden uno a uno. La ventana tiene el tamaño real de la app (1040 × 760).

## Color

Rojo primario **`#C8102E`** (rojo 600). Contraste con texto blanco ≈ 6:1.

| Token | Valor | Uso |
|---|---|---|
| rojo 50 / 100 / 200 | `#FFF1F2` `#FFE0E3` `#FFC2C9` | fondos de selección, fichas, avisos |
| rojo 300 / 400 | `#FF94A0` `#F95A6C` | foco y bordes al pasar el ratón |
| **rojo 600** | `#C8102E` | botón primario, selección, interruptores activos |
| rojo 700 / 800 / 900 | `#A00D25` `#7A0A1C` `#4F0612` | hover, pulsado, texto sobre fondos rojos claros |
| vino 900 / 700 | `#2A1215` `#3A1B1F` | barra lateral |
| texto / gris cálido | `#261C1E` `#7A6A6D` | texto principal y de apoyo |
| borde / fondo | `#EBDFE0` `#FAF7F7` | bordes de tarjeta y fondo de ventana |
| éxito | `#1F8A5B` | estado «Conectado» (único color no rojo) |

Los neutros son cálidos (inclinados al vino) para que el rojo no desentone contra grises fríos.
Toda la paleta vive como *looked-up colors* en `src/main/resources/css/theme.css`; cambiar el primario es editar `-red-*`.

## Logo

Una **T** de trazos redondeados (*Text*) sobre un cuadrado rojo con degradado `#E8283F → #A00D25`, y una
**chispa** rosa `#FFC2C9` que marca el texto ya refinado. Diseñado como pantalla 4 del lienzo; incluye
variantes sobre vino, monocromo blanco y monocromo rojo.

`ui/components/BrandLogo` dibuja el mismo logo desde una única geometría (rejilla de 64 unidades) para
JavaFX (ventana y barra lateral) y AWT (icono de bandeja), de modo que no hay binarios que mantener.
A 16 px la chispa se reduce a un punto y la T sigue legible.

## Tema oscuro

Se activa en *General → Apariencia* y se aplica al instante. El CSS lo implementa redefiniendo las variables bajo `.root.dark`,
sin duplicar reglas. El rojo primario (`#C8102E`) no cambia; lo que cambia es el rojo **usado como texto**, que sobre fondo
oscuro necesita tonos más claros para mantener el contraste:

| Variable | Claro | Oscuro |
|---|---|---|
| `-bg` / `-surface` | `#FAF7F7` / `#FFFFFF` | `#190E10` / `#251618` |
| `-ink` / `-muted` | `#261C1E` / `#7A6A6D` | `#F6E9EA` / `#B9A0A4` |
| `-accent-text` (rojo como texto) | `#C8102E` | `#FF6B7D` |
| `-accent-soft-text` (texto en fichas rojas) | `#A00D25` | `#FFB3BC` |
| `-red-50` … `-red-200` (fondos rojos suaves) | `#FFF1F2` … `#FFC2C9` | `#3A1519` … `#6B2229` |

La barra lateral es vino oscuro en ambos temas. Un mismo color no puede ser a la vez fondo del botón al pasar el ratón (`-red-700`) y
texto sobre oscuro, por eso existen las variables semánticas `-accent-*` y `-danger-text`.

## Páginas

| Página | Contenido |
|---|---|
| Limpieza | Perfil de salida, vista previa en vivo con el pipeline real, procesadores activos |
| Atajos e IA | Atajos con captura de teclas, modo de IA, conexión con Ollama |
| General | Iniciar con el sistema, tema claro/oscuro, idioma de la aplicación y del texto (Español / English) |

## Idiomas (i18n)

Todos los textos que ve el usuario salen de `src/main/resources/i18n/messages_<es|en>.properties` a través de `MessagesPort`:
ventana de ajustes, menú de bandeja y avisos. Cambian en caliente al guardar (la ventana se reconstruye y el menú de bandeja se
vuelve a crear conservando el perfil y la pausa). Reglas:

- Cada clave debe estar en **los dos** archivos y con los mismos marcadores `{0}`; `MessagesTest` lo comprueba, igual que las
  claves compuestas a partir de enums (perfiles, modos de IA, procesadores): añadir un valor sin traducir rompe la prueba.
- Los nombres de los idiomas («Español», «English») no se traducen: cada uno se reconoce por el suyo.
- Los registros (`app.log`) y las excepciones técnicas quedan en español: son para quien desarrolla, no para quien usa.
- Para añadir un idioma: un valor en `Language`, un `messages_xx.properties` y una carpeta `prompts/xx/`.

## Guardar

«Guardar cambios» valida todas las páginas; si algo es inválido muestra el error en el pie y **no** cambia de página. Si todo
es válido guarda, reconstruye la ventana (aplica el idioma y el tema) y vuelve a la página principal (Limpieza).

## Estructura

```
SettingsWindow           barra lateral + páginas + pie (Restaurar / Guardar)
├── CleaningPage         perfil, vista previa en vivo, procesadores
├── ShortcutsPage        atajos (con captura de teclas) y conexión con Ollama
└── GeneralPage          arranque con el sistema, tema, idioma
ui/components            ToggleSwitch, Icons (SVG de trazo), Fonts
```

Cada página implementa `SettingsPage` (`load` / `apply`), así añadir una pantalla es añadir una clase.

## Decisiones

- **Vista previa real:** usa `PipelineFactory`, no una maqueta; lo que ves es lo que hará el atajo.
- **Validación en el pie:** los errores (atajos repetidos, tiempo inválido) salen en rojo junto a «Guardar», sin diálogos modales.
- **Captura de atajos:** exige un modificador y una letra, número o tecla F, para no registrar combinaciones que el hook global no reconoce.
- **Fuente monoespaciada:** se elige la primera instalada (Cascadia Mono, Consolas, Menlo, DejaVu Sans Mono) porque JavaFX CSS no resuelve listas de familias.
- **Bandeja:** el menú de bandeja es nativo (AWT) y no admite estilos; solo se coloreó su icono.
