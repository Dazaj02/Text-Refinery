package com.textrefinery.infrastructure.autostart;

import com.textrefinery.domain.port.AutostartException;
import com.textrefinery.domain.port.AutostartPort;
import com.textrefinery.infrastructure.platform.PlatformInfo;
import com.textrefinery.infrastructure.platform.PlatformInfo.Os;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Arranque con el sistema: clave Run del registro (Windows), LaunchAgent (macOS) o entrada .desktop (Linux).
 * Todo es por usuario: no requiere permisos de administrador.
 */
public final class SystemAutostart implements AutostartPort {

    static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    static final String VALUE_NAME = "TextRefinery";
    static final String PLIST_LABEL = "com.textrefinery.app";

    private final Os os;
    private final Path home;
    private final List<String> command;

    public SystemAutostart() {
        this(PlatformInfo.current(), Path.of(System.getProperty("user.home")), detectCommand());
    }

    /** Para pruebas: sistema, directorio personal y comando de lanzamiento (o {@code null}) inyectados. */
    SystemAutostart(Os os, Path home, List<String> command) {
        this.os = os;
        this.home = home;
        this.command = command;
    }

    @Override
    public boolean isSupported() {
        return command != null && os != Os.OTHER;
    }

    @Override
    public boolean isEnabled() {
        return switch (os) {
            case WINDOWS -> run("reg", "query", RUN_KEY, "/v", VALUE_NAME).exitCode == 0;
            case MAC -> Files.exists(plistPath());
            case LINUX -> Files.exists(desktopPath());
            case OTHER -> false;
        };
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!isSupported()) {
            throw new AutostartException("El arranque con el sistema no está disponible en esta instalación.");
        }
        try {
            switch (os) {
                case WINDOWS -> setWindows(enabled);
                case MAC -> setFile(plistPath(), enabled, plist(command));
                case LINUX -> setFile(desktopPath(), enabled, desktopEntry(command));
                case OTHER -> { }
            }
        } catch (IOException e) {
            throw new AutostartException("No se pudo cambiar el arranque con el sistema", e);
        }
    }

    // ---------------------------------------------------------------- por sistema

    private void setWindows(boolean enabled) throws IOException {
        if (enabled) {
            // `reg add /d` no recibe bien comillas internas desde Java (no las escapa); un .reg importado no tiene ese problema.
            Path file = Files.createTempFile("textrefinery-autostart", ".reg");
            try {
                Files.write(file, windowsRegFile(command));
                Result r = run("reg", "import", file.toString());
                if (r.exitCode != 0) {
                    throw new IOException("reg import terminó con código " + r.exitCode);
                }
            } finally {
                Files.deleteIfExists(file);
            }
        } else {
            Result r = run("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f");
            // Desactivar algo que ya no existe no es un error.
            if (r.exitCode != 0 && isEnabled()) {
                throw new IOException("reg delete terminó con código " + r.exitCode);
            }
        }
    }

    private static void setFile(Path file, boolean enabled, String content) throws IOException {
        if (enabled) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(file);
        }
    }

    private Path plistPath() {
        return home.resolve("Library").resolve("LaunchAgents").resolve(PLIST_LABEL + ".plist");
    }

    private Path desktopPath() {
        return desktopPath(home, System.getenv("XDG_CONFIG_HOME"));
    }

    /** Según la especificación XDG: {@code $XDG_CONFIG_HOME/autostart}, o {@code ~/.config/autostart} si no está definida. */
    static Path desktopPath(Path home, String xdgConfigHome) {
        Path base = (xdgConfigHome != null && !xdgConfigHome.isBlank()) ? Path.of(xdgConfigHome) : home.resolve(".config");
        return base.resolve("autostart").resolve("text-refinery.desktop");
    }

    // ---------------------------------------------------------------- formatos (puros, con pruebas)

    /** Valor del registro: cada argumento con espacios entre comillas. */
    static String windowsRunValue(List<String> cmd) {
        return cmd.stream().map(a -> a.contains(" ") ? "\"" + a + "\"" : a).collect(Collectors.joining(" "));
    }

    /** Contenido de un archivo .reg (UTF-16 LE con BOM, como exige la versión 5.00 del formato). */
    static byte[] windowsRegFile(List<String> cmd) {
        String value = windowsRunValue(cmd).replace("\\", "\\\\").replace("\"", "\\\"");
        String text = "Windows Registry Editor Version 5.00\r\n\r\n"
                + "[" + RUN_KEY.replace("HKCU", "HKEY_CURRENT_USER") + "]\r\n"
                + "\"" + VALUE_NAME + "\"=\"" + value + "\"\r\n";
        byte[] body = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[body.length + 2];
        out[0] = (byte) 0xFF; // BOM UTF-16 LE
        out[1] = (byte) 0xFE;
        System.arraycopy(body, 0, out, 2, body.length);
        return out;
    }

    static String plist(List<String> cmd) {
        String args = cmd.stream().map(a -> "        <string>" + xml(a) + "</string>").collect(Collectors.joining("\n"));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Label</key>
                    <string>%s</string>
                    <key>ProgramArguments</key>
                    <array>
                %s
                    </array>
                    <key>RunAtLoad</key>
                    <true/>
                </dict>
                </plist>
                """.formatted(PLIST_LABEL, args);
    }

    static String desktopEntry(List<String> cmd) {
        String exec = cmd.stream().map(SystemAutostart::desktopQuote).collect(Collectors.joining(" "));
        return """
                [Desktop Entry]
                Type=Application
                Name=Text Refinery
                Comment=Limpia y refina el texto del portapapeles
                Exec=%s
                Terminal=false
                X-GNOME-Autostart-enabled=true
                """.formatted(exec);
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Cita según la especificación de .desktop: comillas dobles y escape de \ " ` $. */
    private static String desktopQuote(String arg) {
        if (arg.chars().noneMatch(c -> " \t\"'\\$`<>~|&;*?#()".indexOf(c) >= 0)) {
            return arg;
        }
        return "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$") + "\"";
    }

    // ---------------------------------------------------------------- cómo se relanza la app

    /**
     * Comando para volver a abrir la app: el lanzador de jpackage si existe; si no, {@code java -jar app.jar};
     * {@code null} si se ejecuta desde clases sueltas (desarrollo) y no hay nada fiable que registrar.
     */
    static List<String> resolveCommand(String jpackageAppPath, String javaHome, String codeSourcePath, Os os) {
        if (jpackageAppPath != null && !jpackageAppPath.isBlank()) {
            return List.of(jpackageAppPath);
        }
        if (codeSourcePath != null && codeSourcePath.endsWith(".jar") && javaHome != null) {
            String java = Path.of(javaHome, "bin", os == Os.WINDOWS ? "javaw.exe" : "java").toString();
            return List.of(java, "-jar", codeSourcePath);
        }
        return null;
    }

    private static List<String> detectCommand() {
        String codeSource = null;
        try {
            codeSource = Path.of(SystemAutostart.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException | RuntimeException e) {
            // sin origen conocido: queda null
        }
        return resolveCommand(System.getProperty("jpackage.app-path"), System.getProperty("java.home"), codeSource,
                PlatformInfo.current());
    }

    // ---------------------------------------------------------------- procesos

    private record Result(int exitCode) { }

    private static Result run(String... args) {
        try {
            Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes(); // vaciar la salida para que el proceso termine
            return new Result(p.waitFor());
        } catch (IOException e) {
            return new Result(-1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1);
        }
    }
}
