package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;

/** Regla de limpieza con una única responsabilidad. Debe ser pura e idempotente. */
public interface TextProcessor {

    String process(String input, ProcessingContext ctx);

    /** Identificador estable, usado para activar/desactivar el procesador en la configuración. */
    default String name() {
        return getClass().getSimpleName();
    }
}
