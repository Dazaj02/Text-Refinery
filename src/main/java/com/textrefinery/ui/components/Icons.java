package com.textrefinery.ui.components;

import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Iconos de trazo (rejilla de 24 px, estilo outline). El logo de la marca está en {@link BrandLogo}. */
public final class Icons {

    public static final String SPARKLE =
            "M12 3l1.9 4.6L18.5 9.5 13.9 11.4 12 16l-1.9-4.6L5.5 9.5l4.6-1.9z M19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z";
    public static final String KEYBOARD =
            "M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z M7 10h.01 M11 10h.01 M15 10h.01 M7 14h10";
    public static final String LOCK =
            "M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z M8 11V7a4 4 0 0 1 8 0v4";

    private Icons() {
    }

    /** Icono de trazo; el color lo define el CSS de su contenedor con {@code .icon { -fx-stroke: ... }}. */
    public static SVGPath outline(String path, double size) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("icon");
        icon.setFill(Color.TRANSPARENT);
        icon.setStrokeWidth(2);
        icon.setStrokeLineCap(StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(StrokeLineJoin.ROUND);
        double scale = size / 24.0;
        icon.setScaleX(scale);
        icon.setScaleY(scale);
        // El escalado no cambia el tamaño de layout: se compensa para que ocupe `size` px.
        icon.setTranslateX(-(24 - size) / 2);
        icon.setTranslateY(-(24 - size) / 2);
        return icon;
    }
}
