package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;

/** Colapsa espacios, unifica espacios Unicode, recorta líneas y limita las líneas en blanco a una. */
public final class WhitespaceNormalizer implements TextProcessor {

    @Override
    public String process(String input, ProcessingContext ctx) {
        return input
                .replaceAll("[\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" ?\\n ?", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
