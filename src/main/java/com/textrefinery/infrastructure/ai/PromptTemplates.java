package com.textrefinery.infrastructure.ai;

import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Carga las plantillas de /prompts/&lt;idioma&gt;/*.txt y sustituye los marcadores:
 * {@code {{texto}}} (el texto del usuario) y {@code {{palabras}}} (longitud máxima sugerida para un resumen).
 */
public final class PromptTemplates {

    /**
     * Un resumen debería ocupar menos de la mitad del original, entre estos límites. Con 0,35 el modelo de 3B (llama3.2)
     * quedaba tan escueto que perdía conclusiones y cifras; con 0,45 conserva lo esencial y sigue siendo claramente más corto.
     */
    private static final double SUMMARY_RATIO = 0.45;
    private static final int SUMMARY_MIN_WORDS = 25;
    private static final int SUMMARY_MAX_WORDS = 150;

    private PromptTemplates() {
    }

    public static String render(AiMode mode, Language language, String text) {
        // Primero los marcadores de la plantilla y por último el texto del usuario: así un {{palabras}} dentro del texto no se toca.
        return load(mode, language)
                .replace("{{palabras}}", String.valueOf(summaryWords(text)))
                .replace("{{texto}}", text);
    }

    /**
     * Longitud objetivo del resumen en palabras. Sin un número concreto un modelo pequeño devuelve casi el mismo texto
     * (comprobado con llama3.2: 92 palabras de entrada, 93 de «resumen»).
     */
    static int summaryWords(String text) {
        int words = text.isBlank() ? 0 : text.strip().split("\\s+").length;
        long target = Math.round(words * SUMMARY_RATIO);
        return (int) Math.max(SUMMARY_MIN_WORDS, Math.min(SUMMARY_MAX_WORDS, target));
    }

    static String load(AiMode mode, Language language) {
        String path = "/prompts/" + language.code() + "/" + mode.name().toLowerCase() + ".txt";
        try (InputStream in = PromptTemplates.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Falta la plantilla " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
