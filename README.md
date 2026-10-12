<p align="center"><img src="docs/img/logo.png" alt="Logo de Text Refinery" width="120"></p>

# Text Refinery Desktop

Aplicación residente en la bandeja del sistema que limpia el texto del portapapeles (saltos de línea
artificiales de PDF, guiones de partición, ligaduras, comillas, espacios) con un atajo global, y
opcionalmente lo refina con IA local (Ollama). Funciona en **Windows, macOS y Linux**.

## Uso

1. Copia texto (Ctrl/Cmd+C).
2. Pulsa **Ctrl+Shift+Y** → el portapapeles queda limpio. Pega donde quieras.
3. **Ctrl+Shift+G** → limpia y además lo envía a Ollama (resumir / corregir / párrafo citable).
   Si Ollama no responde, solo se aplica la limpieza.

¿Te arrepientes? **Restaurar texto original** en el menú de bandeja devuelve lo que habías copiado (solo en memoria; se pierde al cerrar la app).

Perfiles: **Word**, **LaTeX** (escapa `% & _ # $ { } ~ ^ \`), **Markdown** (convierte HTML copiado de la web) y **Texto plano**.
Los atajos, el perfil, el modelo de IA y los procesadores activos se cambian desde *Ajustes…* en el menú de bandeja.

**Cambiar un atajo** (*Ajustes → Atajos e IA*): pulsa **Cambiar**, mantén los modificadores (se ven en vivo) y pulsa una letra, número o
tecla F; **Esc**, un clic fuera o cambiar de ventana cancelan. Mientras grabas, los atajos globales se suspenden para que también
puedas elegir una combinación que ya está en uso, y si repites el de la otra acción te avisa al instante. **Restablecer** vuelve al
atajo predeterminado (`Ctrl+Shift+Y` limpiar, `Ctrl+Shift+G` limpiar con IA); queda desactivado cuando ya es el predeterminado.

## Interfaz

Tema propio en rojo (`#C8102E`) con escala tonal 50–900, barra lateral vino y componentes a medida
(interruptores, teclas, selector segmentado). La vista previa de *Limpieza* ejecuta el pipeline real:
editas el texto de la izquierda y ves el resultado según el perfil y los procesadores activos.
Los atajos se cambian pulsando **Cambiar** y escribiendo la combinación.

Tres páginas: **Limpieza**, **Atajos e IA** y **General** (arranque con el sistema, tema claro/oscuro e idioma de la IA).

| Limpieza | Atajos e IA |
|---|---|
| ![Limpieza](docs/img/limpieza.png) | ![Atajos e IA](docs/img/atajos-ia.png) |
| **General** | **Tema oscuro** |
| ![General](docs/img/general.png) | ![Limpieza en oscuro](docs/img/limpieza-oscuro.png) |
| **En inglés: Cleaning** | **En inglés: General** |
| ![Cleaning](docs/img/cleaning-en.png) | ![General en inglés](docs/img/general-en.png) |

**Idioma de la aplicación:** menús, ventanas y avisos están en español o inglés (*General → Idioma*). Se aplica al guardar, sin
reiniciar, y al guardar la ventana vuelve a la página principal. Por defecto sigue el idioma del sistema. Es independiente del
*idioma del texto*, que solo decide en qué idioma se pide el trabajo a la IA.

Detalles del diseño y de la paleta en [docs/ui-design.md](docs/ui-design.md).

## Requisitos

- JDK 17 o superior
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

| SO      | Comando                                  | Resultado                           | Requisito extra |
|---------|------------------------------------------|-------------------------------------|-----------------|
| Windows | `scripts\package.ps1`                    | instalador `.exe` (`-Type msi`)     | WiX Toolset 3.x |
| Windows | `scripts\package.ps1 -Type app-image`    | carpeta ejecutable, sin instalar    | —               |
| macOS   | `scripts/package.sh`                     | `.dmg`                              | Xcode CLT       |
| Linux   | `scripts/package.sh`                     | `.deb`                              | `fakeroot`      |

El paquete incluye su propio runtime de Java: quien lo instala no necesita tener Java. Con `-SkipBuild`
(o `SKIP_BUILD=1`) se reutiliza `target/` sin llamar a Maven. El resultado queda en `dist/`.
Verificado en Windows con `app-image`; los instaladores `.exe`/`.msi`, `.dmg` y `.deb` aún no se han probado.

Los iconos (`src/main/resources/icons/app.ico`, `.icns`, `.png`) se generan desde el logo con
`java -cp "target/classes;target/libs/*" scripts/GenerateIcons.java` (en macOS/Linux, `:` en lugar de `;`).

## Notas por plataforma

- **macOS**: la primera vez hay que conceder *Accesibilidad* (Ajustes del Sistema → Privacidad y seguridad) para que el atajo global funcione.
- **Linux**: el atajo global requiere X11 (con Wayland no recibe teclas de otras apps). La bandeja necesita una extensión de AppIndicator en GNOME; si no hay bandeja, las notificaciones van al log.
- **Configuración**: `%APPDATA%\TextRefinery\settings.json`, `~/Library/Application Support/TextRefinery/settings.json` o `~/.config/text-refinery/settings.json`.

## Registro y solución de problemas

La app escribe `app.log` junto a `settings.json` (carpeta de configuración, ver arriba), con fecha y hora, y lo rota
a `app.log.1` al superar 1 MB. Se abre desde el menú de bandeja con **Ver registro**. Sirve cuando la app se abre con
doble clic y no hay consola. Solo contiene eventos, longitudes de texto y tipos de error, nunca el contenido copiado.

| Síntoma | Qué mirar |
|---|---|
| El atajo no hace nada | Ver registro: ¿dice «No se pudo activar el atajo global»? Cambia la combinación en *Ajustes → Atajos e IA* |
| «IA no disponible» | *Atajos e IA → Probar conexión*; ¿está Ollama corriendo y el modelo descargado? |
| No arranca con el sistema | *General → Iniciar con el sistema*; no está disponible al ejecutar desde el código fuente |
| Windows lo muestra como «OpenJDK Platform binary» | Es lo normal con `java -jar`: el proceso es `javaw.exe`. Ejecuta el paquete (ver abajo) |

### ¿Por qué aparece «OpenJDK Platform binary»?

Con `java -jar …` el programa que corre es `javaw.exe`, y Windows usa **su** nombre, icono y descripción en el Administrador de
tareas y en los avisos. Opciones, de mejor a peor:

1. **Ejecutar el paquete** (`scripts\package.ps1 -Type app-image`, o el instalador): genera `Text Refinery.exe`, con su nombre, icono y
   descripción propios, y trae su propio Java. Es la solución real y es lo que se entrega.
2. **Acceso directo con el logo** apuntando a `javaw.exe -jar …`: cambia el icono del acceso, pero el proceso y los avisos
   siguen diciendo OpenJDK.
3. **Envoltorio `.exe`** (Launch4j o similar) que lanza el jar: da un `.exe` con nombre propio, pero añade otra herramienta y el
   proceso real sigue siendo Java; el paquete de `jpackage` ya resuelve lo mismo.
4. **Identidad propia en los avisos** (AppUserModelID mediante un acceso directo en el menú Inicio): requiere código nativo para un
   cambio solo visual; el instalador (`-Type exe`) ya registra el programa en el menú Inicio.

Para desarrollo (ejecutar desde código) no hay forma de evitarlo: es el nombre del ejecutable de Java.

## Privacidad

Todo es local. El contenido del portapapeles nunca se registra en logs ni se envía fuera de la máquina (Ollama corre en `localhost`).

## Documentación

- [Arquitectura](docs/architecture.md)
- [Decisiones (ADR)](docs/adr/)
