package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;
import java.util.List;

/** Encadena procesadores en orden; la salida de cada uno es la entrada del siguiente. */
public final class ProcessorPipeline {

    private final List<TextProcessor> processors;

    public ProcessorPipeline(List<TextProcessor> processors) {
        this.processors = List.copyOf(processors);
    }

    public String process(String input, ProcessingContext ctx) {
        String text = input;
        for (TextProcessor p : processors) {
            text = p.process(text, ctx);
        }
        return text;
    }

    public List<String> processorNames() {
        return processors.stream().map(TextProcessor::name).toList();
    }
}
