package com.textrefinery.domain.model;

/** Tipo de refinado que se pide al proveedor de IA. */
public enum AiMode {
    SUMMARIZE("Resumir"),
    GRAMMAR("Corregir gramática"),
    CITE("Párrafo citable");

    private final String label;

    AiMode(String label) {
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
