package com.textrefinery.ui.settings;

import com.textrefinery.domain.model.Settings;
import javafx.scene.Node;

/** Una página de la ventana de ajustes: sabe mostrarse, cargar valores y volcarlos de vuelta. */
interface SettingsPage {

    /** Texto del botón en la barra lateral. */
    String title();

    /** Trazo SVG del icono de la barra lateral. */
    String iconPath();

    Node view();

    void load(Settings settings);

    /** Libera lo que la página tenga en marcha (p. ej. una grabación de atajo) al cerrar o reconstruir la ventana. */
    default void dispose() {
    }

    /** Copia los valores del formulario en {@code target}. Lanza {@link IllegalArgumentException} si hay un valor inválido. */
    void apply(Settings target);
}
