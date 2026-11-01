package com.textrefinery.infrastructure;

import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.Language;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.infrastructure.ai.OllamaAiProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prueba manual contra un Ollama REAL (no corre en CI: el nombre no termina en Test y necesita el servidor y el modelo).
 * Usa el mismo cliente y las mismas plantillas que la app, e imprime resultado, tiempo y una comprobación automática de fidelidad:
 * todas las cifras del resultado deben existir en el texto original (la IA no debe inventar datos).
 *
 * Uso: mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.textrefinery.infrastructure.OllamaLiveCheck
 *      [-Dexec.args="es|en [modelo] [archivo.txt]"]
 */
public final class OllamaLiveCheck {

    private static final String SAMPLE_ES = "La eficiencia energética en edificios residenciales ha ganado relevancia en la última década. "
            + "Según el estudio de López y Martínez (2021), la instalación de aislamiento térmico redujo el consumo de calefacción "
            + "en un 32 % en una muestra de 140 viviendas de Bogotá. Sin embargo, los autores advierten que el efecto rebote, es decir, "
            + "el aumento del uso de energía tras mejorar la eficiencia, compensó cerca de un 8 % del ahorro. Concluyen que las políticas "
            + "públicas deben combinar incentivos económicos con campañas de educación para sostener los ahorros a largo plazo.";

    private static final String SAMPLE_EN = "Energy efficiency in residential buildings has gained relevance over the past decade. "
            + "According to the study by Lopez and Martinez (2021), installing thermal insulation reduced heating consumption "
            + "by 32% in a sample of 140 homes in Bogota. However, the authors warn that the rebound effect, that is, "
            + "the increase in energy use after efficiency improves, offset about 8% of the savings. They conclude that public "
            + "policies must combine financial incentives with education campaigns to sustain the savings in the long term.";

    private static final String ERRORS_ES = "Los resultado de la investigacion muestran que la mayoria de los estudiantes no entienden "
            + "los concepto basicos, sin embargo ellos mejoran cuando se les explica con ejemplos.";

    private static final String ERRORS_EN = "The results of the research shows that most of the student does not understand "
            + "the basic concept, however they improves when it are explained with examples.";

    private OllamaLiveCheck() {
    }

    private static Set<String> numbers(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\\d+(?:[.,]\\d+)?").matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    private static int words(String text) {
        return text.isBlank() ? 0 : text.strip().split("\\s+").length;
    }

    public static void main(String[] args) throws Exception {
        Language language = args.length > 0 && args[0].equalsIgnoreCase("en") ? Language.EN : Language.ES;
        Settings settings = new Settings();
        settings.textLanguage = language;
        settings.aiTimeoutSeconds = 180;
        if (args.length > 1) {
            settings.aiModel = args[1];
        }
        String sample = args.length > 2 ? Files.readString(Path.of(args[2])) : (language == Language.EN ? SAMPLE_EN : SAMPLE_ES);
        String errors = language == Language.EN ? ERRORS_EN : ERRORS_ES;

        OllamaAiProvider ai = new OllamaAiProvider(() -> settings);
        System.out.println("Modelo: " + settings.aiModel + " · idioma del texto: " + language + " · servidor disponible: " + ai.isAvailable());
        if (!ai.isAvailable()) {
            System.out.println("Ollama no responde en " + settings.ollamaUrl);
            return;
        }

        for (AiMode mode : AiMode.values()) {
            String input = mode == AiMode.GRAMMAR ? errors : sample;
            long start = System.nanoTime();
            String result = ai.refine(input, mode).strip();
            double seconds = (System.nanoTime() - start) / 1e9;

            Set<String> invented = numbers(result);
            invented.removeAll(numbers(input));

            System.out.println();
            System.out.println("=== " + mode + " === " + String.format("%.1f s", seconds) + " · " + words(input) + " → " + words(result) + " palabras");
            System.out.println("ENTRADA:   " + input);
            System.out.println("RESULTADO: " + result);
            System.out.println("Fidelidad: " + (invented.isEmpty() ? "OK, no aparecen cifras que no estén en el original"
                    : "ATENCION, cifras nuevas que no están en el original: " + invented));
        }
    }
}
