# Arquitectura

Hexagonal ligera (puertos y adaptadores). Las dependencias apuntan siempre hacia adentro:

```
ui / infrastructure ──► application ──► domain
```

| Capa | Paquete | Contenido |
|------|---------|-----------|
| Dominio | `domain.model`, `domain.processor`, `domain.port` | Perfiles, ajustes, procesadores de texto, interfaces (puertos). Sin dependencias de SO, UI ni red (salvo Jsoup/flexmark en los procesadores HTML). |
| Aplicación | `application` | `CleanClipboardUseCase`, `RefineWithAiUseCase`, `PipelineFactory`. |
| Infraestructura | `infrastructure.*` | Adaptadores: `clipboard` (AWT), `hotkey` (JNativeHook), `ai` (Ollama), `config` (JSON y rutas por SO), `lock` (instancia única), `notify`, `autostart` (registro / LaunchAgent / .desktop), `logging` (app.log), `platform` (SO y limitaciones). |
| UI | `ui.*` | `tray` (AWT `SystemTray`), `settings` (ventana JavaFX por páginas), `components` (ToggleSwitch, Icons, BrandLogo, Fonts). |
| Composición | `App` | Único lugar donde se instancian y conectan los adaptadores. |

Puertos del dominio: `ClipboardPort`, `AiProviderPort`, `NotifierPort`, `SettingsPort`, `AutostartPort` y `MessagesPort`
(textos de la interfaz en el idioma activo; implementado por `infrastructure.i18n.ResourceBundleMessages`, que cambia en caliente).
Los casos de uso piden sus avisos a `MessagesPort`, de modo que cambiar el idioma no requiere reconstruirlos.

## Estado en memoria (privacidad)

`CleanClipboardUseCase` guarda el último texto original para «Restaurar texto original». Vive solo en memoria,
nunca en disco ni en el registro, y se pierde al cerrar la app. Si el portapapeles ya contiene lo último que se escribió
(atajo pulsado dos veces, o refinado con IA) no se sobrescribe el original verdadero.

## Degradación ante fallos

| Fallo | Comportamiento |
|---|---|
| Atajo global no disponible (permiso, Wayland, combinación ocupada) | Se avisa con una pista según el SO; la app y el menú de bandeja siguen funcionando |
| Sin bandeja del sistema | Se abre la ventana de ajustes; al cerrarla termina la app |
| Ollama caído, lento o con error | Se aplica solo la limpieza y se avisa |
| Portapapeles bloqueado | Reintenta ~1 s; si no, avisa en vez de decir «Texto limpio» |
| No se puede escribir `app.log` | La app sigue; solo hay consola |

## Flujo

```
atajo → ClipboardPort.read → PipelineFactory.build(perfil) → pipeline.process
      → [opcional] AiProviderPort.refine → ClipboardPort.write → NotifierPort.show
```

## Orden del pipeline

1. `HtmlToMarkdownConverter` (perfil MARKDOWN) o `HtmlStripper` (perfil PLAIN)
2. `ControlCharRemover`
3. `LigatureFixer`
4. `HyphenationFixer` — antes de unir líneas
5. `LineBreakJoiner`
6. `QuoteNormalizer`
7. `WhitespaceNormalizer`
8. `LatexEscaper` (perfil LATEX) — siempre el último

## Hilos

- Hilo nativo de JNativeHook: solo encola trabajo (debe devolver rápido).
- `executor` (2 hilos): limpieza y llamadas a IA, con `orTimeout`.
- Hilo de JavaFX: solo la ventana de ajustes. `Platform.setImplicitExit(false)` evita que la app termine al cerrarla.

## Pruebas

- `ProcessorTests`: una clase anidada por procesador.
- `GoldenFileTest`: recorre `src/test/resources/golden/<perfil>/<caso>/{entrada,esperado}.txt`, comprueba el resultado y la idempotencia. Para añadir un caso real basta crear la carpeta.
- `UseCaseTests`: casos de uso con puertos falsos (sin SO ni red).

## Privacidad y seguridad

- Nunca se registra el contenido del portapapeles; los logs solo llevan longitudes y tipos de error.
- Límite de tamaño (`maxClipboardChars`) y rechazo de contenido no textual.
- Las plantillas de prompt delimitan el texto del usuario y piden ignorar instrucciones incrustadas.
