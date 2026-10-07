# Diseño de la interfaz

Propuesta visual (lienzo «Text Refinery Interfaz», 3 pantallas) e implementada en JavaFX.

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

## Estructura

```
SettingsWindow           barra lateral + páginas + pie (Restaurar / Guardar)
├── CleaningPage         perfil, vista previa en vivo, procesadores
└── ShortcutsPage        atajos (con captura de teclas) y conexión con Ollama
ui/components            ToggleSwitch, Icons (SVG de trazo), Fonts
```

Cada página implementa `SettingsPage` (`load` / `apply`), así añadir una pantalla es añadir una clase.

## Decisiones

- **Vista previa real:** usa `PipelineFactory`, no una maqueta; lo que ves es lo que hará el atajo.
- **Validación en el pie:** los errores (atajos repetidos, tiempo inválido) salen en rojo junto a «Guardar», sin diálogos modales.
- **Captura de atajos:** exige un modificador y una letra, número o tecla F, para no registrar combinaciones que el hook global no reconoce.
- **Fuente monoespaciada:** se elige la primera instalada (Cascadia Mono, Consolas, Menlo, DejaVu Sans Mono) porque JavaFX CSS no resuelve listas de familias.
- **Bandeja:** el menú de bandeja es nativo (AWT) y no admite estilos; solo se coloreó su icono.
