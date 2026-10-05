package com.textrefinery.domain.model;

/** Información compartida por todos los procesadores durante una ejecución. */
public record ProcessingContext(CleaningProfile profile) {

    public static ProcessingContext of(CleaningProfile profile) {
        return new ProcessingContext(profile);
    }
}
