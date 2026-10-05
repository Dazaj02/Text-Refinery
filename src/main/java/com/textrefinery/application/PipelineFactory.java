package com.textrefinery.application;

import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.processor.ControlCharRemover;
import com.textrefinery.domain.processor.HtmlStripper;
import com.textrefinery.domain.processor.HtmlToMarkdownConverter;
import com.textrefinery.domain.processor.HyphenationFixer;
import com.textrefinery.domain.processor.LatexEscaper;
import com.textrefinery.domain.processor.LigatureFixer;
import com.textrefinery.domain.processor.LineBreakJoiner;
import com.textrefinery.domain.processor.ProcessorPipeline;
import com.textrefinery.domain.processor.QuoteNormalizer;
import com.textrefinery.domain.processor.TextProcessor;
import com.textrefinery.domain.processor.WhitespaceNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Arma el pipeline según el perfil. El orden importa: ver docs/architecture.md. */
public class PipelineFactory {

    public ProcessorPipeline build(CleaningProfile profile, Set<String> disabled) {
        List<TextProcessor> chain = new ArrayList<>();

        switch (profile) {
            case MARKDOWN -> chain.add(new HtmlToMarkdownConverter());
            case PLAIN -> chain.add(new HtmlStripper());
            default -> { }
        }

        chain.add(new ControlCharRemover());
        chain.add(new LigatureFixer());
        chain.add(new HyphenationFixer()); // antes de unir líneas
        chain.add(new LineBreakJoiner());
        chain.add(new QuoteNormalizer());
        chain.add(new WhitespaceNormalizer());

        if (profile == CleaningProfile.LATEX) {
            chain.add(new LatexEscaper()); // siempre el último
        }

        chain.removeIf(p -> disabled.contains(p.name()));
        return new ProcessorPipeline(chain);
    }

    /** Nombres de todos los procesadores que el usuario puede activar/desactivar. */
    public static List<String> processorNames() {
        return List.of(
                HtmlToMarkdownConverter.class.getSimpleName(),
                HtmlStripper.class.getSimpleName(),
                ControlCharRemover.class.getSimpleName(),
                LigatureFixer.class.getSimpleName(),
                HyphenationFixer.class.getSimpleName(),
                LineBreakJoiner.class.getSimpleName(),
                QuoteNormalizer.class.getSimpleName(),
                WhitespaceNormalizer.class.getSimpleName(),
                LatexEscaper.class.getSimpleName());
    }
}
