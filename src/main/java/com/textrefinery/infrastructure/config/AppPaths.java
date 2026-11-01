package com.textrefinery.infrastructure.config;

import com.textrefinery.infrastructure.platform.PlatformInfo;
import java.nio.file.Path;

/** Directorio de configuración según el sistema operativo. */
public final class AppPaths {

    private AppPaths() {
    }

    public static Path configDir() {
        String home = System.getProperty("user.home");
        return switch (PlatformInfo.current()) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield Path.of(appData != null ? appData : home, "TextRefinery");
            }
            case MAC -> Path.of(home, "Library", "Application Support", "TextRefinery");
            default -> {
                String xdg = System.getenv("XDG_CONFIG_HOME");
                Path base = (xdg != null && !xdg.isBlank()) ? Path.of(xdg) : Path.of(home, ".config");
                yield base.resolve("text-refinery");
            }
        };
    }
}
