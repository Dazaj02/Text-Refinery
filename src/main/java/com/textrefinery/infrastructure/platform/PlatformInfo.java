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

    /** Clave del mensaje (i18n/messages_*.properties) con la pista para el usuario cuando el atajo global no se activa. */
    public static String hotkeyProblemKey(Os os, boolean wayland) {
        return switch (os) {
            case MAC -> "hint.hotkey.mac";
            case LINUX -> wayland ? "hint.hotkey.linuxWayland" : "hint.hotkey.linuxX11";
            case WINDOWS -> "hint.hotkey.windows";
            case OTHER -> "hint.hotkey.other";
        };
    }
}
