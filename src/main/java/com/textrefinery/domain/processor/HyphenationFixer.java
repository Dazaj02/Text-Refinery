package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;
import java.util.regex.Pattern;

/**
 * Une palabras partidas por guion al final de línea ("infor-\nmación" → "información").
 * Solo actúa si la línea siguiente empieza en minúscula, para no romper compuestos
 * reales ("teórico-\nPráctico" se respeta).
 */
public final class HyphenationFixer implements TextProcessor {

    private static final Pattern SPLIT_WORD =
            Pattern.compile("(\\p{L})[-\\u2010][ \\t]*\\n[ \\t]*(\\p{Ll})");

    @Override
    public String process(String input, ProcessingContext ctx) {
        return SPLIT_WORD.matcher(input).replaceAll("$1$2");
    }
}
