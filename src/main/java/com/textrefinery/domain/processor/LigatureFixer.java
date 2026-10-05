package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;

/** Sustituye ligaduras tipográficas (ﬁ, ﬂ…) por sus letras. */
public final class LigatureFixer implements TextProcessor {

    @Override
    public String process(String input, ProcessingContext ctx) {
        return input
                .replace("ﬀ", "ff")
                .replace("ﬁ", "fi")
                .replace("ﬂ", "fl")
                .replace("ﬃ", "ffi")
                .replace("ﬄ", "ffl")
                .replace("ﬅ", "st")
                .replace("ﬆ", "st");
    }
}
