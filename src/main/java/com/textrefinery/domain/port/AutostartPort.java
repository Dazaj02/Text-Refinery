package com.textrefinery.domain.port;

/** Arranque automático de la app al iniciar sesión. */
public interface AutostartPort {

    /** {@code false} si no hay forma fiable de relanzar la app (p. ej. se ejecuta desde un directorio de clases). */
    boolean isSupported();

    /** Estado real en el sistema operativo (no el guardado en los ajustes). */
    boolean isEnabled();

    /** Activa o desactiva el arranque con el sistema. Lanza {@link AutostartException} si el sistema lo rechaza. */
    void setEnabled(boolean enabled);
}
