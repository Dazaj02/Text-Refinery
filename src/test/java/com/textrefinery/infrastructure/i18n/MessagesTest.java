package com.textrefinery.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.textrefinery.application.PipelineFactory;
import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.model.Language;
import com.textrefinery.domain.model.ThemeMode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static Properties load(Language language) throws IOException {
        Properties props = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream("/i18n/messages_" + language.code() + ".properties")) {
            assertThat(in).as("archivo de " + language).isNotNull();
            props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return props;
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = Pattern.compile("\\{\\d+}").matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    @Test
    void losDosIdiomasTienenExactamenteLasMismasClaves() throws IOException {
        Set<String> es = new TreeSet<>(load(Language.ES).stringPropertyNames());
        Set<String> en = new TreeSet<>(load(Language.EN).stringPropertyNames());

        Set<String> soloEs = new TreeSet<>(es);
        soloEs.removeAll(en);
        Set<String> soloEn = new TreeSet<>(en);
        soloEn.removeAll(es);
        assertThat(soloEs).as("claves que faltan en inglés").isEmpty();
        assertThat(soloEn).as("claves que faltan en español").isEmpty();
    }

    @Test
    void ningunMensajeEstaVacioYLosMarcadoresCoinciden() throws IOException {
        Properties es = load(Language.ES);
        Properties en = load(Language.EN);
        for (String key : es.stringPropertyNames()) {
            assertThat(es.getProperty(key)).as("es/" + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en/" + key).isNotBlank();
            assertThat(placeholders(en.getProperty(key))).as("marcadores {n} de " + key)
                    .isEqualTo(placeholders(es.getProperty(key)));
        }
    }

    @Test
    void existenLosMensajesQueLaInterfazBuscaPorNombre() {
        // La interfaz compone estas claves a partir de enums y listas: si se añade un valor nuevo sin traducir, falla aquí.
        for (Language language : Language.values()) {
            ResourceBundleMessages messages = new ResourceBundleMessages(language);
            for (CleaningProfile p : CleaningProfile.values()) {
                assertThat(messages.get("profile." + p.name())).as(language + " profile").doesNotStartWith("!");
                assertThat(messages.get("profile." + p.name() + ".desc")).doesNotStartWith("!");
            }
            for (AiMode m : AiMode.values()) {
                assertThat(messages.get("ai.mode." + m.name())).doesNotStartWith("!");
                assertThat(messages.get("ai.mode." + m.name() + ".hint")).doesNotStartWith("!");
            }
            for (ThemeMode t : ThemeMode.values()) {
                assertThat(messages.get("theme." + t.name())).doesNotStartWith("!");
            }
            for (String processor : PipelineFactory.processorNames()) {
                assertThat(messages.get("processor." + processor + ".label")).doesNotStartWith("!");
                assertThat(messages.get("processor." + processor + ".hint")).doesNotStartWith("!");
            }
        }
    }

    @Test
    void sustituyeArgumentosYAceptaComillasSimples() {
        ResourceBundleMessages es = new ResourceBundleMessages(Language.ES);
        assertThat(es.get("notify.tooLong", 5)).isEqualTo("El texto es demasiado largo (máx. 5 caracteres)");
        assertThat(es.get("preview.result", "WORD")).isEqualTo("RESULTADO · WORD");
    }

    @Test
    void cambiarDeIdiomaSurteEfectoAlInstante() {
        ResourceBundleMessages messages = new ResourceBundleMessages(Language.ES);
        assertThat(messages.get("button.save")).isEqualTo("Guardar cambios");
        assertThat(messages.language()).isEqualTo(Language.ES);

        messages.setLanguage(Language.EN);
        assertThat(messages.get("button.save")).isEqualTo("Save changes");
        assertThat(messages.language()).isEqualTo(Language.EN);
    }

    @Test
    void unaClaveInexistenteSeNotaEnPantallaEnVezDeFallar() {
        assertThat(new ResourceBundleMessages(Language.ES).get("no.existe")).isEqualTo("!no.existe!");
    }

    @Test
    void elIdiomaPorDefectoSigueAlSistema() {
        assertThat(Language.fromLocale(Locale.forLanguageTag("en-US"))).isEqualTo(Language.EN);
        assertThat(Language.fromLocale(Locale.forLanguageTag("es-CO"))).isEqualTo(Language.ES);
        assertThat(Language.fromLocale(Locale.forLanguageTag("fr-FR"))).isEqualTo(Language.ES);
        assertThat(Language.fromLocale(null)).isEqualTo(Language.ES);
    }

    @Test
    void laMuestraDeLaVistaPreviaTrae_saltosYLigaduras() {
        for (Language language : Language.values()) {
            String sample = new ResourceBundleMessages(language).get("preview.sample");
            assertThat(sample).as(language.name()).contains("\n").contains("ﬁ").contains("-\n");
        }
    }
}
