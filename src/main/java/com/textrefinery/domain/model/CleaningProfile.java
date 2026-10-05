package com.textrefinery.domain.model;

/** Perfil de limpieza: determina qué procesadores se encadenan y su orden. */
public enum CleaningProfile {
    WORD("Word"),
    LATEX("LaTeX"),
    MARKDOWN("Markdown"),
    PLAIN("Texto plano");

    private final String label;

    CleaningProfile(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
