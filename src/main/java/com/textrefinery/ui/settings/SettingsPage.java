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

    /** Copia los valores del formulario en {@code target}. Lanza {@link IllegalArgumentException} si hay un valor inválido. */
    void apply(Settings target);
}
