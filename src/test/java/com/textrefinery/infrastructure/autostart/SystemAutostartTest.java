package com.textrefinery.infrastructure.autostart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.textrefinery.domain.port.AutostartException;
import com.textrefinery.infrastructure.platform.PlatformInfo.Os;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemAutostartTest {

    private static final List<String> JAR_CMD = List.of("/opt/Java 17/bin/java", "-jar", "/home/ana/Text Refinery/app.jar");

    @Test
    void usaElLanzadorDeJpackageSiExiste() {
        assertThat(SystemAutostart.resolveCommand("C:\\Apps\\Text Refinery.exe", "C:\\jdk", "C:\\x\\app.jar", Os.WINDOWS))
                .containsExactly("C:\\Apps\\Text Refinery.exe");
    }

    @Test
    void sinLanzadorUsaJavaMasElJar() {
        List<String> win = SystemAutostart.resolveCommand(null, "C:\\jdk", "C:\\x\\app.jar", Os.WINDOWS);
        assertThat(win.get(0)).endsWith("javaw.exe");
        assertThat(win).containsSubsequence("-jar", "C:\\x\\app.jar");

        List<String> linux = SystemAutostart.resolveCommand("", "/usr/lib/jvm/17", "/opt/app.jar", Os.LINUX);
        assertThat(linux.get(0)).endsWith("java").doesNotContain("javaw");
    }

    @Test
    void desdeClasesSueltasNoHayNadaFiableQueRegistrar() {
        assertThat(SystemAutostart.resolveCommand(null, "/jdk", "/proyecto/target/classes", Os.LINUX)).isNull();
        assertThat(SystemAutostart.resolveCommand(null, "/jdk", null, Os.LINUX)).isNull();
    }

    @Test
    void elValorDelRegistroCitaLosArgumentosConEspacios() {
        assertThat(SystemAutostart.windowsRunValue(List.of("C:\\Program Files\\Java\\javaw.exe", "-jar", "C:\\Mis Docs\\app.jar")))
                .isEqualTo("\"C:\\Program Files\\Java\\javaw.exe\" -jar \"C:\\Mis Docs\\app.jar\"");
    }

    @Test
    void elArchivoRegEscapaBarrasYComillasYLlevaBom() {
        byte[] reg = SystemAutostart.windowsRegFile(List.of("C:\\Mis Docs\\javaw.exe", "-jar", "C:\\app.jar"));
        assertThat(reg[0] & 0xFF).isEqualTo(0xFF);
        assertThat(reg[1] & 0xFF).isEqualTo(0xFE);
        String text = new String(reg, 2, reg.length - 2, java.nio.charset.StandardCharsets.UTF_16LE);
        assertThat(text).startsWith("Windows Registry Editor Version 5.00");
        assertThat(text).contains("[HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run]");
        assertThat(text).contains("\"TextRefinery\"=\"\\\"C:\\\\Mis Docs\\\\javaw.exe\\\" -jar C:\\\\app.jar\"");
    }

    @Test
    void elPlistEsXmlValidoYEscapaCaracteres() {
        String plist = SystemAutostart.plist(List.of("/Applications/Text Refinery & Co.app/Contents/MacOS/Text Refinery"));
        assertThat(plist).contains("<key>RunAtLoad</key>", "<true/>", "com.textrefinery.app");
        assertThat(plist).contains("Text Refinery &amp; Co.app").doesNotContain("& Co");
    }

    @Test
    void laEntradaDesktopCitaSegunLaEspecificacion() {
        String entry = SystemAutostart.desktopEntry(JAR_CMD);
        assertThat(entry).contains("[Desktop Entry]", "Type=Application", "X-GNOME-Autostart-enabled=true");
        assertThat(entry).contains("Exec=\"/opt/Java 17/bin/java\" -jar \"/home/ana/Text Refinery/app.jar\"");
    }

    @Test
    void enLinuxActivarYDesactivarCreaYBorraLaEntrada(@TempDir Path home) throws Exception {
        SystemAutostart autostart = new SystemAutostart(Os.LINUX, home, JAR_CMD);
        assertThat(autostart.isSupported()).isTrue();
        assertThat(autostart.isEnabled()).isFalse();

        autostart.setEnabled(true);
        assertThat(autostart.isEnabled()).isTrue();
        Path file = home.resolve(".config").resolve("autostart").resolve("text-refinery.desktop");
        assertThat(Files.readString(file)).contains("Exec=");

        autostart.setEnabled(false);
        assertThat(autostart.isEnabled()).isFalse();
        assertThat(file).doesNotExist();
        autostart.setEnabled(false); // desactivar dos veces no falla
    }

    @Test
    void enMacActivarYDesactivarCreaYBorraElLaunchAgent(@TempDir Path home) {
        SystemAutostart autostart = new SystemAutostart(Os.MAC, home, List.of("/Applications/Text Refinery.app/Contents/MacOS/Text Refinery"));
        autostart.setEnabled(true);
        Path plist = home.resolve("Library").resolve("LaunchAgents").resolve("com.textrefinery.app.plist");
        assertThat(plist).exists();
        assertThat(autostart.isEnabled()).isTrue();
        autostart.setEnabled(false);
        assertThat(plist).doesNotExist();
    }

    @Test
    void sinComandoFiableNoSePuedeActivar(@TempDir Path home) {
        SystemAutostart autostart = new SystemAutostart(Os.LINUX, home, null);
        assertThat(autostart.isSupported()).isFalse();
        assertThatThrownBy(() -> autostart.setEnabled(true)).isInstanceOf(AutostartException.class);
    }
}
