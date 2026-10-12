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
    void losAtajosPredeterminadosSonValidosDistintosYSinAlt() {
        Hotkey clean = Hotkey.parse(Settings.DEFAULT_CLEAN_HOTKEY);
        Hotkey ai = Hotkey.parse(Settings.DEFAULT_AI_HOTKEY);
        assertThat(clean).isNotEqualTo(ai);
        assertThat(clean.alt()).isFalse(); // Ctrl+Alt es AltGr en Windows y choca con Word
        assertThat(ai.alt()).isFalse();
        // Un Settings nuevo arranca con ellos: «Restablecer» de la ventana vuelve exactamente a este valor.
        assertThat(new Settings().cleanHotkey).isEqualTo(Settings.DEFAULT_CLEAN_HOTKEY);
        assertThat(new Settings().aiHotkey).isEqualTo(Settings.DEFAULT_AI_HOTKEY);
    }

    @Test
    void rechazaAtajosInvalidos() {
        assertThatThrownBy(() -> Hotkey.parse("V")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hotkey.parse("Ctrl+")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hotkey.parse("Ctrl+A+B")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void todasLasPlantillasDePromptExistenEnCadaIdioma() {
        for (var language : com.textrefinery.domain.model.Language.values()) {
            for (AiMode mode : AiMode.values()) {
                assertThat(PromptTemplates.render(mode, language, "TEXTO-DE-PRUEBA"))
                        .as(language + "/" + mode)
                        .contains("TEXTO-DE-PRUEBA").doesNotContain("{{texto}}");
            }
        }
    }

    @Test
    void lasPlantillasEstanEnElIdiomaIndicado() {
        String es = PromptTemplates.render(AiMode.CITE, com.textrefinery.domain.model.Language.ES, "x");
        String en = PromptTemplates.render(AiMode.CITE, com.textrefinery.domain.model.Language.EN, "x");
        assertThat(es).contains("párrafo citable").contains("NO inventes");
        assertThat(en).contains("citable paragraph").contains("Do NOT invent");
        assertThat(en).doesNotContain("inventes");
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
        first.theme = com.textrefinery.domain.model.ThemeMode.DARK;
        first.launchAtLogin = true;
        first.textLanguage = com.textrefinery.domain.model.Language.EN;
        adapter.save(first);

        Settings second = adapter.load();
        assertThat(second.defaultProfile).isEqualTo(CleaningProfile.LATEX);
        assertThat(second.disabledProcessors).containsExactly("QuoteNormalizer");
        assertThat(second.theme).isEqualTo(com.textrefinery.domain.model.ThemeMode.DARK);
        assertThat(second.launchAtLogin).isTrue();
        assertThat(second.textLanguage).isEqualTo(com.textrefinery.domain.model.Language.EN);
        assertThat(second.copy().theme).isEqualTo(com.textrefinery.domain.model.ThemeMode.DARK);
    }

    @Test
    void configuracionCorruptaVuelveALosValoresPorDefecto(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.json");
        java.nio.file.Files.writeString(file, "{ esto no es json");
        assertThat(new JsonSettingsAdapter(file).load().cleanHotkey).isEqualTo("Ctrl+Shift+Y");
    }

    @Test
    void unaConfiguracionAnteriorSinTemaSigueSiendoValida(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.json");
        java.nio.file.Files.writeString(file, "{\"defaultProfile\":\"LATEX\",\"cleanHotkey\":\"Ctrl+Shift+Y\"}");
        Settings s = new JsonSettingsAdapter(file).load();
        assertThat(s.defaultProfile).isEqualTo(CleaningProfile.LATEX);
        assertThat(s.theme).isEqualTo(com.textrefinery.domain.model.ThemeMode.LIGHT);
        assertThat(s.launchAtLogin).isFalse();
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
