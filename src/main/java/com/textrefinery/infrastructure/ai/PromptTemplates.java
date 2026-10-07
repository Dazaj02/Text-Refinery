package com.textrefinery.infrastructure.ai;

import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.TextLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Carga las plantillas de /prompts/&lt;idioma&gt;/*.txt y sustituye el marcador {{texto}}. */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static String render(AiMode mode, TextLanguage language, String text) {
        return load(mode, language).replace("{{texto}}", text);
    }

    static String load(AiMode mode, TextLanguage language) {
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
