package com.textrefinery.infrastructure.platform;

import java.util.Locale;

/** Detección del sistema operativo y de las limitaciones conocidas de cada uno. */
public final class PlatformInfo {

    public enum Os { WINDOWS, MAC, LINUX, OTHER }

    private PlatformInfo() {
    }

    public static Os parse(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Os.WINDOWS;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Os.MAC;
        }
        if (os.contains("nux") || os.contains("nix") || os.contains("bsd")) {
            return Os.LINUX;
        }
        return Os.OTHER;
    }

    public static Os current() {
        return parse(System.getProperty("os.name"));
    }

    /** Wayland no deja a una app escuchar teclas de otras aplicaciones. */
    public static boolean isWayland(String sessionType, String waylandDisplay) {
        return "wayland".equalsIgnoreCase(sessionType) || (waylandDisplay != null && !waylandDisplay.isBlank());
    }

    public static boolean isWayland() {
        return isWayland(System.getenv("XDG_SESSION_TYPE"), System.getenv("WAYLAND_DISPLAY"));
    }

    /** Mensaje para el usuario cuando el atajo global no se pudo activar. */
    public static String hotkeyProblemHint(Os os, boolean wayland) {
        return switch (os) {
            case MAC -> "Concede el permiso de Accesibilidad en Ajustes del Sistema › Privacidad y seguridad.";
            case LINUX -> wayland
                    ? "Wayland no permite atajos globales; inicia sesión con X11 o usa el menú de la bandeja."
                    : "No se pudo registrar el atajo; comprueba que se ejecuta bajo X11.";
            case WINDOWS -> "Otra aplicación puede estar usando el atajo; cámbialo en Ajustes.";
            case OTHER -> "Este sistema no admite atajos globales; usa el menú de la bandeja.";
        };
    }
}
