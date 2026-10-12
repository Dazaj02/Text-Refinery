package com.textrefinery.infrastructure.platform;

import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identidad propia de la app en Windows (AppUserModelID). Sin ella Windows atribuye todo lo que hace el proceso al ejecutable
 * de Java: los avisos de la bandeja salen como «OpenJDK Platform binary» y la barra de tareas agrupa bajo ese nombre.
 *
 * Dos pasos, ambos necesarios (medido con el Centro de notificaciones: sin ellos los avisos no se atribuyen a la app):
 * <ol>
 *   <li>{@link #setProcessId()}: el proceso declara su identificador. Antes de crear ventanas o el icono de bandeja.</li>
 *   <li>{@link #register}: el identificador se registra en HKCU con el nombre y el icono que Windows debe mostrar.</li>
 * </ol>
 * Todo es por usuario (sin administrador). En otros sistemas las funciones no hacen nada.
 */
public final class WindowsAppIdentity {

    private static final Logger log = LoggerFactory.getLogger(WindowsAppIdentity.class);

    public static final String APP_ID = "TextRefinery.App";
    static final String REGISTRY_KEY = "Software\\Classes\\AppUserModelId\\" + APP_ID;

    private WindowsAppIdentity() {
    }

    /** Se carga solo si se usa: en macOS y Linux la clase JNA ni se toca. */
    private interface Shell32 extends StdCallLibrary {
        Shell32 INSTANCE = Native.load("shell32", Shell32.class);

        int SetCurrentProcessExplicitAppUserModelID(WString appId);
    }

    public static boolean isSupported() {
        return PlatformInfo.current() == PlatformInfo.Os.WINDOWS;
    }

    /** Declara el identificador del proceso. Debe llamarse antes de crear ventanas o iconos de bandeja. */
    public static boolean setProcessId() {
        if (!isSupported()) {
            return false;
        }
        try {
            return Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(new WString(APP_ID)) == 0;
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            log.warn("No se pudo fijar la identidad de la app en Windows: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Registra el nombre y el icono con los que Windows presenta la app. Idempotente: se llama en cada arranque. */
    public static boolean register(String displayName, Path iconPng) {
        if (!isSupported()) {
            return false;
        }
        try {
            Path file = Files.createTempFile("textrefinery-identity", ".reg");
            try {
                Files.write(file, regFile(displayName, iconPng));
                Process p = new ProcessBuilder("reg", "import", file.toString()).redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                return p.waitFor() == 0;
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            log.warn("No se pudo registrar la identidad de la app: {}", e.getClass().getSimpleName());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Quita el registro (para pruebas o una desinstalación manual). */
    public static void unregister() {
        if (!isSupported()) {
            return;
        }
        try {
            new ProcessBuilder("reg", "delete", "HKCU\\" + REGISTRY_KEY, "/f").redirectErrorStream(true).start().waitFor();
        } catch (IOException e) {
            log.debug("No se pudo quitar el registro de la identidad");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Contenido de un .reg (UTF-16 LE con BOM). Se importa un archivo porque `reg add` no recibe bien comillas desde Java. */
    static byte[] regFile(String displayName, Path iconPng) {
        String text = "Windows Registry Editor Version 5.00\r\n\r\n"
                + "[HKEY_CURRENT_USER\\" + REGISTRY_KEY + "]\r\n"
                + "\"DisplayName\"=\"" + escape(displayName) + "\"\r\n"
                + "\"IconUri\"=\"" + escape(iconPng.toAbsolutePath().toString()) + "\"\r\n"
                + "\"IconBackgroundColor\"=\"FFC8102E\"\r\n";
        byte[] body = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[body.length + 2];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xFE;
        System.arraycopy(body, 0, out, 2, body.length);
        return out;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
