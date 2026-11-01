package com.textrefinery.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Solo la parte pura: tocar el registro real alteraría la identidad registrada por la app del usuario. */
class WindowsAppIdentityTest {

    private static String text(byte[] reg) {
        return new String(reg, 2, reg.length - 2, StandardCharsets.UTF_16LE);
    }

    @Test
    void elArchivoRegRegistraNombreIconoYColor() {
        Path icon = Path.of("TextRefinery", "app-icon.png").toAbsolutePath();
        byte[] reg = WindowsAppIdentity.regFile("Text Refinery", icon);
        assertThat(reg[0] & 0xFF).as("BOM UTF-16 LE").isEqualTo(0xFF);
        assertThat(reg[1] & 0xFF).isEqualTo(0xFE);
        String text = text(reg);
        assertThat(text).startsWith("Windows Registry Editor Version 5.00");
        assertThat(text).contains("[HKEY_CURRENT_USER\\Software\\Classes\\AppUserModelId\\TextRefinery.App]");
        assertThat(text).contains("\"DisplayName\"=\"Text Refinery\"");
        // las barras invertidas se duplican en un .reg (en Windows son las del separador de la ruta)
        assertThat(text).contains("\"IconUri\"=\"" + icon.toString().replace("\\", "\\\\") + "\"");
        assertThat(text).contains("\"IconBackgroundColor\"=\"FFC8102E\"");
    }

    @Test
    void escapaComillasYBarrasEnLasRutas() {
        String text = text(WindowsAppIdentity.regFile("Mi \"app\"", Path.of("C:\\Mis Docs\\logo.png")));
        assertThat(text).contains("\"DisplayName\"=\"Mi \\\"app\\\"\"");
        assertThat(text).contains("C:\\\\Mis Docs\\\\logo.png");
    }

    @Test
    void elIdentificadorEsEstableYSinEspacios() {
        // Windows lo usa como clave de registro y para agrupar: si cambia, se pierde la asociación con avisos y barra de tareas.
        assertThat(WindowsAppIdentity.APP_ID).isEqualTo("TextRefinery.App").doesNotContain(" ");
    }

    @Test
    void fueraDeWindowsNoHaceNada() {
        if (!WindowsAppIdentity.isSupported()) {
            assertThat(WindowsAppIdentity.setProcessId()).isFalse();
            assertThat(WindowsAppIdentity.register("x", Path.of("x.png"))).isFalse();
        }
    }
}
