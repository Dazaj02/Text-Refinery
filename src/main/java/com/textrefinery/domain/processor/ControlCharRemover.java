package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;

/** Normaliza saltos de línea y elimina caracteres de control e invisibles (típicos de PDF/web). */
public final class ControlCharRemover implements TextProcessor {

    @Override
    public String process(String input, ProcessingContext ctx) {
        String s = input.replace("\r\n", "\n").replace('\r', '\n').replace('\f', '\n');
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\t') {
                sb.append(c);
            } else if (!Character.isISOControl(c) && !isInvisible(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isInvisible(char c) {
        return c == '​' // espacio de ancho cero
                || c == '﻿' // BOM
                || c == '­' // guion blando
                || c == '⁠'; // word joiner
    }
}
