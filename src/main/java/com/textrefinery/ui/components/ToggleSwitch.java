package com.textrefinery.ui.components;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/** Interruptor tipo "switch" (el ToggleButton de JavaFX no se puede estilizar así sin perder accesibilidad). */
public final class ToggleSwitch extends StackPane {

    private static final PseudoClass ON = PseudoClass.getPseudoClass("on");
    private static final double TRAVEL = 18;

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Circle knob = new Circle(8);

    public ToggleSwitch() {
        getStyleClass().add("toggle-switch");
        knob.getStyleClass().add("toggle-knob");
        setPrefSize(40, 22);
        setMinSize(40, 22);
        setMaxSize(40, 22);
        setAlignment(Pos.CENTER_LEFT);
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        getChildren().add(knob);
        StackPane.setMargin(knob, new Insets(0, 0, 0, 3));

        setOnMouseClicked(e -> selected.set(!selected.get()));
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                selected.set(!selected.get());
                e.consume();
            }
        });
        selected.addListener((obs, was, now) -> {
            pseudoClassStateChanged(ON, now);
            TranslateTransition move = new TranslateTransition(Duration.millis(120), knob);
            move.setToX(now ? TRAVEL : 0);
            move.play();
        });
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
        // Sin animación al cargar: el listener solo corre si cambia el valor.
        pseudoClassStateChanged(ON, value);
        knob.setTranslateX(value ? TRAVEL : 0);
    }
}
