# Arquitectura

Hexagonal ligera (puertos y adaptadores). Las dependencias apuntan siempre hacia adentro:

```
ui / infrastructure ──► application ──► domain
```

| Capa | Paquete | Contenido |
|------|---------|-----------|
| Dominio | `domain.model`, `domain.processor`, `domain.port` | Perfiles, ajustes, procesadores de texto, interfaces (puertos). Sin dependencias de SO, UI ni red (salvo Jsoup/flexmark en los procesadores HTML). |
| Aplicación | `application` | `CleanClipboardUseCase`, `RefineWithAiUseCase`, `PipelineFactory`. |
| Infraestructura | `infrastructure.*` | Adaptadores: portapapeles AWT, JNativeHook, Ollama, JSON, bloqueo de instancia, notificaciones. |
| UI | `ui.*` | Bandeja (AWT `SystemTray`) y ventana de ajustes (JavaFX, bajo demanda). |
| Composición | `App` | Único lugar donde se instancian y conectan los adaptadores. |

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
