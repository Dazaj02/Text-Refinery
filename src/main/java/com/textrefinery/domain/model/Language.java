package com.textrefinery.domain.model;

/**
 * Idioma del texto que se procesa. La limpieza es independiente del idioma (trabaja con Unicode);
 * este valor decide en qué idioma se pide el trabajo a la IA.
 */
public enum TextLanguage {
    ES("es", "Español"),
    EN("en", "English");

    private final String code;
    private final String label;

    TextLanguage(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** Carpeta de las plantillas de prompt, p. ej. {@code prompts/es/}. */
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
