package com.textrefinery.domain.model;

/** Apariencia de la ventana de ajustes. */
public enum ThemeMode {
    LIGHT("Claro"),
    DARK("Oscuro");

    private final String label;

    ThemeMode(String label) {
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
