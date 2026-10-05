# Text Refinery Desktop

Aplicación residente en la bandeja del sistema que limpia el texto del portapapeles (saltos de línea
artificiales de PDF, guiones de partición, ligaduras, comillas, espacios) con un atajo global, y
opcionalmente lo refina con IA local (Ollama). Funciona en **Windows, macOS y Linux**.

## Uso

1. Copia texto (Ctrl/Cmd+C).
2. Pulsa **Ctrl+Alt+V** → el portapapeles queda limpio. Pega donde quieras.
3. **Ctrl+Alt+B** → limpia y además lo envía a Ollama (resumir / corregir / párrafo citable).
   Si Ollama no responde, solo se aplica la limpieza.

Perfiles: **Word**, **LaTeX** (escapa `% & _ # $ { } ~ ^ \`), **Markdown** (convierte HTML copiado de la web) y **Texto plano**.
Los atajos, el perfil, el modelo de IA y los procesadores activos se cambian desde *Ajustes…* en el menú de bandeja.

## Requisitos

- JDK 21 o superior
- Maven 3.9+
- (opcional) [Ollama](https://ollama.com) con un modelo descargado: `ollama pull llama3.2`

## Desarrollo

```bash
mvn test                  # pruebas unitarias + golden files
mvn compile exec:java     # ejecutar la app
mvn package               # genera target/text-refinery-*.jar y target/libs/
```

## Empaquetado (instalador nativo)

Cada sistema empaqueta con su propio JDK; no hay compilación cruzada.

| SO      | Comando                       | Resultado       | Requisito extra |
|---------|-------------------------------|-----------------|-----------------|
| Windows | `scripts\package.ps1`         | `.exe` / `.msi` | WiX Toolset 3.x |
| macOS   | `scripts/package.sh`          | `.dmg`          | Xcode CLT       |
| Linux   | `scripts/package.sh`          | `.deb`          | `fakeroot`      |

## Notas por plataforma

- **macOS**: la primera vez hay que conceder *Accesibilidad* (Ajustes del Sistema → Privacidad y seguridad) para que el atajo global funcione.
- **Linux**: el atajo global requiere X11 (con Wayland no recibe teclas de otras apps). La bandeja necesita una extensión de AppIndicator en GNOME; si no hay bandeja, las notificaciones van al log.
- **Configuración**: `%APPDATA%\TextRefinery\settings.json`, `~/Library/Application Support/TextRefinery/settings.json` o `~/.config/text-refinery/settings.json`.

## Privacidad

Todo es local. El contenido del portapapeles nunca se registra en logs ni se envía fuera de la máquina (Ollama corre en `localhost`).

## Documentación

- [Arquitectura](docs/architecture.md)
- [Decisiones (ADR)](docs/adr/)
