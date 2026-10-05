package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;

/** Convierte comillas tipográficas “ ” ‘ ’ a rectas. Las comillas latinas « » se conservan. */
public final class QuoteNormalizer implements TextProcessor {

    @Override
    public String process(String input, ProcessingContext ctx) {
        return input
                .replace('“', '"').replace('”', '"')
                .replace('„', '"').replace('‟', '"')
                .replace('‘', '\'').replace('’', '\'')
                .replace('‚', '\'').replace('‛', '\'');
    }
}
