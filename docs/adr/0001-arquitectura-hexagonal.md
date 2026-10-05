# ADR 0001: Arquitectura hexagonal ligera

**Estado:** aceptada

**Contexto.** La lógica de limpieza es lo valioso del proyecto y debe probarse sin abrir ventanas, sin portapapeles real y sin Ollama. El SO (atajos, bandeja, portapapeles) es lo más cambiante entre Windows, macOS y Linux.

**Decisión.** El dominio define puertos (`ClipboardPort`, `AiProviderPort`, `NotifierPort`, `SettingsPort`); la infraestructura los implementa; `App` es el único punto de composición.

**Consecuencias.** Se pueden probar casos de uso con dobles de prueba y cambiar Ollama por otro motor sin tocar el dominio. Coste: algo más de ficheros que un diseño plano.
