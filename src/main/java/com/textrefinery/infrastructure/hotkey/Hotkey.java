package com.textrefinery.infrastructure.hotkey;

import java.util.Locale;

/** Combinación de teclas parseada desde texto, p. ej. "Ctrl+Alt+V". */
public record Hotkey(boolean ctrl, boolean alt, boolean shift, boolean meta, String key) {

    public static Hotkey parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Atajo vacío");
        }
        boolean ctrl = false, alt = false, shift = false, meta = false;
        String key = null;
        for (String raw : text.split("\\+")) {
            String token = raw.strip().toLowerCase(Locale.ROOT);
            switch (token) {
                case "ctrl", "control" -> ctrl = true;
                case "alt", "option", "opt" -> alt = true;
                case "shift" -> shift = true;
                case "meta", "cmd", "command", "win", "super" -> meta = true;
                default -> {
                    if (token.isEmpty() || key != null) {
                        throw new IllegalArgumentException("Atajo inválido: " + text);
                    }
                    key = token.toUpperCase(Locale.ROOT);
                }
            }
        }
        if (key == null || !(ctrl || alt || shift || meta)) {
            throw new IllegalArgumentException("El atajo necesita un modificador y una tecla: " + text);
        }
        return new Hotkey(ctrl, alt, shift, meta, key);
    }
}
