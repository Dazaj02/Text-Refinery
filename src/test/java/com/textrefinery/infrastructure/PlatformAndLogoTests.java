package com.textrefinery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.textrefinery.infrastructure.platform.PlatformInfo;
import com.textrefinery.infrastructure.platform.PlatformInfo.Os;
import com.textrefinery.ui.components.BrandLogo;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class PlatformAndLogoTests {

    @Test
    void reconoceLosSistemasOperativos() {
        assertThat(PlatformInfo.parse("Windows 11")).isEqualTo(Os.WINDOWS);
        assertThat(PlatformInfo.parse("Mac OS X")).isEqualTo(Os.MAC);
        assertThat(PlatformInfo.parse("Linux")).isEqualTo(Os.LINUX);
        assertThat(PlatformInfo.parse("FreeBSD")).isEqualTo(Os.LINUX);
        assertThat(PlatformInfo.parse("Plan9")).isEqualTo(Os.OTHER);
        assertThat(PlatformInfo.parse(null)).isEqualTo(Os.OTHER);
    }

    @Test
    void detectaWayland() {
        assertThat(PlatformInfo.isWayland("wayland", null)).isTrue();
        assertThat(PlatformInfo.isWayland("x11", "wayland-0")).isTrue();
        assertThat(PlatformInfo.isWayland("x11", null)).isFalse();
        assertThat(PlatformInfo.isWayland(null, "")).isFalse();
    }

    @Test
    void cadaSistemaTieneUnaPistaParaElAtajo() {
        assertThat(PlatformInfo.hotkeyProblemHint(Os.MAC, false)).contains("Accesibilidad");
        assertThat(PlatformInfo.hotkeyProblemHint(Os.LINUX, true)).contains("Wayland");
        assertThat(PlatformInfo.hotkeyProblemHint(Os.LINUX, false)).contains("X11");
        assertThat(PlatformInfo.hotkeyProblemHint(Os.WINDOWS, false)).isNotBlank();
    }

    @Test
    void elLogoSeDibujaSinPantalla() {
        BufferedImage logo = BrandLogo.awtImage(64);
        assertThat(logo.getWidth()).isEqualTo(64);
        assertThat(logo.getRGB(0, 0) >>> 24).as("esquina transparente").isZero();
        int centro = logo.getRGB(32, 20); // dentro de la barra de la T
        assertThat(centro & 0xFFFFFF).as("la T es blanca").isEqualTo(0xFFFFFF);
        int fondo = logo.getRGB(10, 32);  // fondo rojo
        assertThat((fondo >> 16) & 0xFF).as("canal rojo dominante").isGreaterThan(150);
        assertThat(fondo & 0xFF).as("poco azul").isLessThan(90);
    }
}
