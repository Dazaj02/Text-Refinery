package com.textrefinery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.textrefinery.domain.model.Language;
import com.textrefinery.infrastructure.i18n.ResourceBundleMessages;
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
    void cadaSistemaTieneUnaPistaParaElAtajoEnAmbosIdiomas() {
        for (Language language : Language.values()) {
            ResourceBundleMessages messages = new ResourceBundleMessages(language);
            for (Os os : Os.values()) {
                for (boolean wayland : new boolean[]{false, true}) {
                    String key = PlatformInfo.hotkeyProblemKey(os, wayland);
                    assertThat(messages.get(key)).as(language + "/" + key).isNotBlank().doesNotStartWith("!");
                }
            }
        }
        assertThat(PlatformInfo.hotkeyProblemKey(Os.MAC, false)).isEqualTo("hint.hotkey.mac");
        assertThat(PlatformInfo.hotkeyProblemKey(Os.LINUX, true)).isEqualTo("hint.hotkey.linuxWayland");
        assertThat(PlatformInfo.hotkeyProblemKey(Os.LINUX, false)).isEqualTo("hint.hotkey.linuxX11");
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
