package com.textrefinery.ui.components;

import java.util.List;
import javafx.scene.text.Font;

/** Selección de fuentes según lo instalado (JavaFX CSS no resuelve listas de familias de forma fiable). */
public final class Fonts {

    private static final List<String> MONO_CANDIDATES =
            List.of("Cascadia Mono", "Consolas", "Menlo", "DejaVu Sans Mono", "Liberation Mono", "Monospaced");

    private static String mono;

    private Fonts() {
    }

    /** Primera familia monoespaciada disponible en este sistema. Llamar desde el hilo de JavaFX. */
    public static synchronized String mono() {
        if (mono == null) {
            List<String> installed = Font.getFamilies();
            mono = MONO_CANDIDATES.stream().filter(installed::contains).findFirst().orElse("Monospaced");
        }
        return mono;
    }
}
