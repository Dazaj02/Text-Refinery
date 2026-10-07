# ADR 0002: Java 17, bandeja AWT y JavaFX solo para ajustes

**Estado:** aceptada

**Contexto.** La app es residente: el consumo de memoria importa y la mayor parte del tiempo no hay ventana. Debe funcionar en Windows, macOS y Linux.

**Decisión.**
- Java 17 (LTS) con `--release 17`: es la versión mínima que exige JavaFX 21 y la más extendida en equipos de clase. Corre igual en 21 o superior.
- Bandeja y notificaciones con `java.awt.SystemTray`, más estable que la de JavaFX.
- JavaFX solo para la ventana de ajustes, iniciado con `Platform.startup` y sin cierre implícito.
- Atajo global con JNativeHook (hook nativo para los tres SO).
- Sin `module-info`: JNativeHook y flexmark no son modulares; se ejecuta en classpath.

**Consecuencias.**
- macOS necesita permiso de Accesibilidad; en Linux el atajo global requiere X11.
- Si no hay bandeja (algunos escritorios Linux), se usa `LogNotifier` y el atajo sigue funcionando.
- JavaFX en classpath muestra un aviso de configuración no soportada que no afecta al funcionamiento.
- Cada SO empaqueta con `jpackage` en su propia máquina (CI con matriz).
