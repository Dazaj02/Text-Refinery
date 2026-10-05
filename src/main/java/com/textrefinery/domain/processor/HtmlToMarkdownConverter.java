package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;

/** Convierte HTML a Markdown (perfil MARKDOWN). Si la entrada no es HTML, la deja igual. */
public final class HtmlToMarkdownConverter implements TextProcessor {

    private final FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder(
            new MutableDataSet().set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false)).build();

    @Override
    public String process(String input, ProcessingContext ctx) {
        if (!HtmlStripper.looksLikeHtml(input)) {
            return input;
        }
        return converter.convert(input);
    }
}
