package com.textrefinery.infrastructure.config;

import java.nio.file.Path;
import java.util.Locale;

/** Directorio de configuración según el sistema operativo. */
public final class AppPaths {

    private AppPaths() {
    }

    public static Path configDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home, "TextRefinery");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "TextRefinery");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = (xdg != null && !xdg.isBlank()) ? Path.of(xdg) : Path.of(home, ".config");
        return base.resolve("text-refinery");
    }
}
