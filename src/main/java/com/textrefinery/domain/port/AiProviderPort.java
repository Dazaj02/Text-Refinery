package com.textrefinery.domain.port;

import com.textrefinery.domain.model.AiMode;

public interface AiProviderPort {

    /** Devuelve el texto refinado. Lanza {@link AiProviderException} si el motor no responde. */
    String refine(String text, AiMode mode);

    boolean isAvailable();
}
