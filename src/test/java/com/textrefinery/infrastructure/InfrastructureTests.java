package com.textrefinery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.infrastructure.ai.PromptTemplates;
import com.textrefinery.infrastructure.config.JsonSettingsAdapter;
import com.textrefinery.infrastructure.hotkey.Hotkey;
import com.textrefinery.infrastructure.lock.SingleInstanceLock;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InfrastructureTests {

    @Test
    void parseaAtajos() {
        assertThat(Hotkey.parse("Ctrl+Alt+V")).isEqualTo(new Hotkey(true, true, false, false, "V"));
        assertThat(Hotkey.parse("cmd + shift + k")).isEqualTo(new Hotkey(false, false, true, true, "K"));
    }

    @Test
    void rechazaAtajosInvalidos() {
        assertThatThrownBy(() -> Hotkey.parse("V")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hotkey.parse("Ctrl+")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hotkey.parse("Ctrl+A+B")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void todasLasPlantillasDePromptExisten() {
        for (AiMode mode : AiMode.values()) {
            assertThat(PromptTemplates.render(mode, "TEXTO-DE-PRUEBA")).contains("TEXTO-DE-PRUEBA").doesNotContain("{{texto}}");
        }
    }

    @Test
    void configuracionSeCreaYSeRelee(@TempDir Path dir) {
        Path file = dir.resolve("sub").resolve("settings.json");
        JsonSettingsAdapter adapter = new JsonSettingsAdapter(file);

        Settings first = adapter.load(); // crea el archivo con los valores por defecto
        assertThat(file).exists();
        assertThat(first.defaultProfile).isEqualTo(CleaningProfile.WORD);

        first.defaultProfile = CleaningProfile.LATEX;
        first.disabledProcessors.add("QuoteNormalizer");
        adapter.save(first);

        Settings second = adapter.load();
        assertThat(second.defaultProfile).isEqualTo(CleaningProfile.LATEX);
        assertThat(second.disabledProcessors).containsExactly("QuoteNormalizer");
    }

    @Test
    void configuracionCorruptaVuelveALosValoresPorDefecto(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.json");
        java.nio.file.Files.writeString(file, "{ esto no es json");
        assertThat(new JsonSettingsAdapter(file).load().cleanHotkey).isEqualTo("Ctrl+Alt+V");
    }

    @Test
    void soloUnaInstanciaPuedeTomarElBloqueo(@TempDir Path dir) {
        SingleInstanceLock a = new SingleInstanceLock(dir.resolve("app.lock"));
        SingleInstanceLock b = new SingleInstanceLock(dir.resolve("app.lock"));
        assertThat(a.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isFalse();
        a.release();
    }
}
