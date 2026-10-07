package com.textrefinery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.AiProviderException;
import com.textrefinery.domain.port.AiProviderPort;
import com.textrefinery.domain.port.ClipboardPort;
import com.textrefinery.domain.port.NotifierPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UseCaseTests {

    static class FakeClipboard implements ClipboardPort {
        String text;
        String html;
        boolean locked;

        @Override
        public Optional<String> readText() {
            return Optional.ofNullable(text);
        }

        @Override
        public Optional<String> readHtml() {
            return Optional.ofNullable(html);
        }

        @Override
        public boolean writeText(String t) {
            if (locked) {
                return false;
            }
            text = t;
            return true;
        }
    }

    static class FakeNotifier implements NotifierPort {
        final List<String> infos = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void info(String m) {
            infos.add(m);
        }

        @Override
        public void error(String m) {
            errors.add(m);
        }
    }

    private final FakeClipboard clipboard = new FakeClipboard();
    private final FakeNotifier notifier = new FakeNotifier();
    private final Settings settings = new Settings();
    private final Executor direct = Runnable::run;
    private CleanClipboardUseCase clean;

    @BeforeEach
    void setUp() {
        clean = new CleanClipboardUseCase(clipboard, new PipelineFactory(), notifier, () -> settings);
    }

    @Test
    void limpiaYEscribeEnElPortapapeles() {
        clipboard.text = "infor-\nmación   \nútil";
        clean.execute();
        assertThat(clipboard.text).isEqualTo("información útil");
        assertThat(notifier.infos).containsExactly("Texto limpio ✓");
    }

    @Test
    void noDiceExitoSiNoPudoEscribir() {
        clipboard.text = "hola  mundo";
        clipboard.locked = true;
        clean.execute();
        assertThat(notifier.infos).isEmpty();
        assertThat(notifier.errors).hasSize(1);
    }

    @Test
    void restaurarDevuelveElTextoAnteriorALaLimpieza() {
        clipboard.text = "infor-\nmacion   sucia";
        clean.execute();
        assertThat(clipboard.text).isEqualTo("informacion sucia");

        clean.restoreOriginal();
        assertThat(clipboard.text).isEqualTo("infor-\nmacion   sucia");
        assertThat(notifier.infos).contains("Texto original restaurado ✓");
    }

    @Test
    void limpiarDosVecesSeguidasNoPierdeElOriginalVerdadero() {
        clipboard.text = "uno   dos\ntres";
        clean.execute();
        clean.execute(); // el portapapeles ya tiene el texto limpio
        clean.restoreOriginal();
        assertThat(clipboard.text).isEqualTo("uno   dos\ntres");
    }

    @Test
    void restaurarSinNadaGuardadoAvisa() {
        clean.restoreOriginal();
        assertThat(notifier.errors).containsExactly("No hay ningún texto anterior que restaurar");
    }

    @Test
    void conLaIaTambienSeRestauraElOriginalNoElRefinado() {
        clipboard.text = "hola   mundo";
        AiProviderPort ai = new AiProviderPort() {
            public String refine(String text, AiMode mode) { return "REFINADO"; }
            public boolean isAvailable() { return true; }
        };
        RefineWithAiUseCase refine = new RefineWithAiUseCase(clean, ai, clipboard, notifier, () -> settings, direct);
        refine.execute(AiMode.GRAMMAR).join();
        assertThat(clipboard.text).isEqualTo("REFINADO");

        refine.execute(AiMode.GRAMMAR).join(); // segunda pasada sobre el texto ya refinado
        clean.restoreOriginal();
        assertThat(clipboard.text).isEqualTo("hola   mundo");
    }

    @Test
    void enMarkdownSeRestauraElTextoPlanoNoElHtml() {
        settings.defaultProfile = com.textrefinery.domain.model.CleaningProfile.MARKDOWN;
        clipboard.html = "<p>Hola <b>mundo</b></p>";
        clipboard.text = "Hola mundo";
        clean.execute();
        assertThat(clipboard.text).contains("**mundo**");

        clean.restoreOriginal();
        assertThat(clipboard.text).isEqualTo("Hola mundo");
    }

    @Test
    void avisaSiNoHayTexto() {
        clean.execute();
        assertThat(notifier.errors).hasSize(1);
    }

    @Test
    void ignoraTextosDemasiadoLargos() {
        settings.maxClipboardChars = 5;
        clipboard.text = "123456";
        clean.execute();
        assertThat(clipboard.text).isEqualTo("123456");
        assertThat(notifier.errors).hasSize(1);
    }

    @Test
    void iaRefinaElTexto() {
        clipboard.text = "hola  mundo";
        AiProviderPort ai = new AiProviderPort() {
            @Override
            public String refine(String text, AiMode mode) {
                return "[" + text + "]";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
        new RefineWithAiUseCase(clean, ai, clipboard, notifier, () -> settings, direct)
                .execute(AiMode.GRAMMAR).join();
        assertThat(clipboard.text).isEqualTo("[hola mundo]");
    }

    @Test
    void siLaIaFallaQuedaSoloLaLimpieza() {
        clipboard.text = "hola  mundo";
        AiProviderPort ai = new AiProviderPort() {
            @Override
            public String refine(String text, AiMode mode) {
                throw new AiProviderException("caído");
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
        new RefineWithAiUseCase(clean, ai, clipboard, notifier, () -> settings, direct)
                .execute(AiMode.GRAMMAR).join();
        assertThat(clipboard.text).isEqualTo("hola mundo");
        assertThat(notifier.errors).hasSize(1);
    }
}
