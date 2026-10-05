package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;

/**
 * Escapa los caracteres especiales de LaTeX. Va siempre al final del pipeline y
 * NO es idempotente por naturaleza (escapar dos veces duplica las barras).
 */
public final class LatexEscaper implements TextProcessor {

    @Override
    public String process(String input, ProcessingContext ctx) {
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\textbackslash{}");
                case '&', '%', '$', '#', '_', '{', '}' -> sb.append('\\').append(c);
                case '~' -> sb.append("\\textasciitilde{}");
                case '^' -> sb.append("\\textasciicircum{}");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
