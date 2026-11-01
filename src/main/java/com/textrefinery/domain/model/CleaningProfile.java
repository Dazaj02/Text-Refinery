package com.textrefinery.domain.model;

/** Perfil de limpieza: determina qué procesadores se encadenan y su orden. El nombre visible está en i18n/messages_*. */
public enum CleaningProfile {
    WORD,
    LATEX,
    MARKDOWN,
    PLAIN
}
