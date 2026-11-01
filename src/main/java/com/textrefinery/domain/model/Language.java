package com.textrefinery.domain.model;

import java.util.Locale;

/**
 * Idioma. Se usa para dos ajustes distintos: el de la interfaz de la aplicación y el del texto que se pide a la IA.
 * La limpieza en sí es independiente del idioma (trabaja con Unicode).
 */
public enum Language {
    ES("es", "Español"),
    EN("en", "English");

    private final String code;
    private final String name;

    Language(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** Código ISO; también la carpeta de las plantillas de prompt ({@code prompts/es/}). */
    public String code() {
        return code;
    }

    /** Nombre del idioma en ese mismo idioma (no se traduce: cada uno se reconoce por el suyo). */
    public String label() {
        return name;
    }

    /** Idioma por defecto según el sistema: inglés si el sistema está en inglés, español en cualquier otro caso. */
    public static Language fromLocale(Locale locale) {
        return locale != null && "en".equalsIgnoreCase(locale.getLanguage()) ? EN : ES;
    }

    @Override
    public String toString() {
        return name;
    }
}
