package com.textrefinery.infrastructure.ai;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arranca {@code ollama serve} cuando la IA lo necesita y no está en marcha, y lo cierra al salir
 * <b>solo si lo arrancó esta app</b> (un Ollama que ya corría antes nunca se toca).
 */
public class OllamaLauncher {

    private static final Logger log = LoggerFactory.getLogger(OllamaLauncher.class);

    private Process started;

    /** Solo se arranca un servidor local: con una dirección remota no hay nada que lanzar aquí. */
    public static boolean isLocal(String url) {
        try {
            String host = URI.create(url.strip()).getHost();
            return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")
                    || host.equals("::1"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Rutas donde suele estar el ejecutable, en orden de preferencia (primero las carpetas del PATH). */
    static List<Path> candidates(String osName, String pathEnv, String localAppData) {
        String os = osName.toLowerCase(Locale.ROOT);
        boolean windows = os.contains("win");
        String exe = windows ? "ollama.exe" : "ollama";
        List<Path> result = new ArrayList<>();
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank()) {
                    result.add(Paths.get(dir.strip()).resolve(exe));
                }
            }
        }
        if (windows) {
            if (localAppData != null && !localAppData.isBlank()) {
                result.add(Paths.get(localAppData, "Programs", "Ollama", exe));
            }
        } else if (os.contains("mac")) {
            result.add(Paths.get("/Applications/Ollama.app/Contents/Resources/ollama"));
            result.add(Paths.get("/opt/homebrew/bin/ollama"));
            result.add(Paths.get("/usr/local/bin/ollama"));
        } else {
            result.add(Paths.get("/usr/local/bin/ollama"));
            result.add(Paths.get("/usr/bin/ollama"));
        }
        return result;
    }

    Optional<Path> locate() {
        return candidates(System.getProperty("os.name", ""), System.getenv("PATH"), System.getenv("LOCALAPPDATA"))
                .stream().filter(Files::isRegularFile).findFirst();
    }

    /** Lanza el servidor si hay ejecutable. Devuelve {@code false} si Ollama no está instalado o no pudo arrancar. */
    public synchronized boolean start() {
        if (started != null && started.isAlive()) {
            return true;
        }
        Optional<Path> exe = locate();
        if (exe.isEmpty()) {
            log.info("Ollama no está instalado: no se puede arrancar automáticamente.");
            return false;
        }
        try {
            started = new ProcessBuilder(exe.get().toString(), "serve")
                    .redirectInput(ProcessBuilder.Redirect.from(nullFile()))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            log.info("Ollama arrancado por la app.");
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo arrancar Ollama: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Cierra el servidor únicamente si lo arrancó esta app. */
    public synchronized void stop() {
        if (started == null) {
            return;
        }
        started.descendants().forEach(ProcessHandle::destroy);
        started.destroy();
        log.info("Ollama cerrado (lo había arrancado la app).");
        started = null;
    }

    private static java.io.File nullFile() {
        return new java.io.File(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "NUL" : "/dev/null");
    }
}
