package com.textrefinery.domain.port;

import java.util.Optional;

public interface ClipboardPort {

    /** Texto plano del portapapeles; vacío si no hay contenido textual. */
    Optional<String> readText();

    /** Fragmento HTML del portapapeles (p. ej. copiado desde un navegador), si existe. */
    Optional<String> readHtml();

    void writeText(String text);
}
