package com.textrefinery.domain.port;

import com.textrefinery.domain.model.Language;

/** Textos de la interfaz en el idioma activo de la aplicación. */
public interface MessagesPort {

    /**
     * Texto asociado a la clave, con {@code {0}}, {@code {1}}… sustituidos por los argumentos.
     * Si la clave no existe devuelve {@code !clave!} (se nota en pantalla y una prueba impide que ocurra).
     */
    String get(String key, Object... args);

    Language language();
}
