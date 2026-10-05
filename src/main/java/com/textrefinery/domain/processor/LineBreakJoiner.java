package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Une los saltos de línea artificiales dentro de un párrafo, respetando párrafos
 * (líneas en blanco), viñetas, listas numeradas, títulos y citas Markdown.
 */
public final class LineBreakJoiner implements TextProcessor {

    private static final Pattern BLOCK_START =
            Pattern.compile("^(?:[-*•●▪◦]\\s+|\\d+[.)]\\s+|#{1,6}\\s|>|```|\\|)");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s");

    @Override
    public String process(String input, ProcessingContext ctx) {
        String text = input.replaceAll("[ \\t]+\\n", "\n");
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : text.split("\\n{2,}", -1)) {
            String joined = joinLines(paragraph);
            if (!joined.isEmpty()) {
                paragraphs.add(joined);
            }
        }
        return String.join("\n\n", paragraphs);
    }

    private static String joinLines(String paragraph) {
        StringBuilder sb = new StringBuilder();
        String previous = "";
        for (String line : paragraph.split("\\n")) {
            String current = line.strip();
            if (current.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                boolean keepBreak = BLOCK_START.matcher(current).find() || HEADING.matcher(previous).find();
                sb.append(keepBreak ? '\n' : ' ');
            }
            sb.append(current);
            previous = current;
        }
        return sb.toString();
    }
}
