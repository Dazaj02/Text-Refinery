package com.textrefinery.domain.port;

import com.textrefinery.domain.model.AiMode;

public interface AiProviderPort {

    /** Devuelve el texto refinado. Lanza {@link AiProviderException} si el motor no responde. */
    String refine(String text, AiMode mode);

    boolean isAvailable();

    /** Segundos extra, además del tiempo de respuesta, que el motor puede necesitar (p. ej. cargar el modelo en memoria). */
    default int extraWaitSeconds() {
        return 0;
    }
}
