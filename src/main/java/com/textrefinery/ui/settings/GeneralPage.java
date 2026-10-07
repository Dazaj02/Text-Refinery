package com.textrefinery.ui.settings;

import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.model.ThemeMode;
import com.textrefinery.domain.port.AutostartPort;
import com.textrefinery.ui.components.Icons;
import com.textrefinery.ui.components.ToggleSwitch;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Ajustes generales de la aplicación: arranque con el sistema, apariencia e idioma. */
final class GeneralPage implements SettingsPage {

    private final AutostartPort autostart;
    private final Consumer<ThemeMode> onThemeChosen;
    private final ToggleGroup themeGroup = new ToggleGroup();
    private final Map<ThemeMode, ToggleButton> themeButtons = new EnumMap<>(ThemeMode.class);
    private final ToggleSwitch launchAtLogin = new ToggleSwitch();
    private final Label launchHint = new Label();
    private final VBox sections = new VBox(16);
    private final ScrollPane root = new ScrollPane();

    GeneralPage(AutostartPort autostart, Consumer<ThemeMode> onThemeChosen) {
        this.autostart = autostart;
        this.onThemeChosen = onThemeChosen;

        Label title = new Label("General");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Comportamiento de la aplicación en tu equipo.");
        sub.getStyleClass().add("page-subtitle");

        launchAtLogin.setAccessibleText("Iniciar con el sistema");
        launchHint.getStyleClass().add("processor-hint");
        launchHint.setWrapText(true);
        sections.getChildren().add(card("Inicio", row("Iniciar con el sistema", launchHint, launchAtLogin)));

        Label themeHint = new Label("Se aplica al instante; se guarda con «Guardar cambios».");
        themeHint.getStyleClass().add("processor-hint");
        HBox themeSegments = new HBox(4);
        themeSegments.getStyleClass().add("segment-group");
        for (ThemeMode mode : ThemeMode.values()) {
            ToggleButton b = new ToggleButton(mode.label());
            b.getStyleClass().add("segment");
            b.setToggleGroup(themeGroup);
            b.setMinWidth(90);
            themeButtons.put(mode, b);
            themeSegments.getChildren().add(b);
        }
        themeGroup.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) { // siempre debe haber un tema elegido
                themeGroup.selectToggle(was);
                return;
            }
            onThemeChosen.accept(selectedTheme());
        });
        sections.getChildren().add(card("Apariencia", row("Tema", themeHint, themeSegments)));

        VBox content = new VBox(18, new VBox(4, title, sub), sections);
        content.setPadding(new Insets(30, 36, 8, 36));
        root.setContent(content);
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    @Override
    public String title() {
        return "General";
    }

    @Override
    public String iconPath() {
        return Icons.SLIDERS;
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void load(Settings s) {
        boolean supported = autostart.isSupported();
        launchAtLogin.setDisable(!supported);
        launchAtLogin.setSelected(supported && autostart.isEnabled()); // el estado real lo manda el sistema, no el JSON
        themeGroup.selectToggle(themeButtons.get(s.theme));
        launchHint.setText(supported
                ? "Abre Text Refinery en la bandeja al iniciar sesión."
                : "No disponible al ejecutar desde el código fuente; usa el instalador o el jar.");
    }

    @Override
    public void apply(Settings target) {
        target.launchAtLogin = launchAtLogin.isSelected();
        target.theme = selectedTheme();
    }

    private ThemeMode selectedTheme() {
        Toggle selected = themeGroup.getSelectedToggle();
        for (Map.Entry<ThemeMode, ToggleButton> e : themeButtons.entrySet()) {
            if (e.getValue() == selected) {
                return e.getKey();
            }
        }
        return ThemeMode.LIGHT;
    }

    // ---------------------------------------------------------------- piezas reutilizables

    /** Tarjeta con título y filas de ajustes. */
    static VBox card(String heading, Node... rows) {
        Label title = new Label(heading);
        title.getStyleClass().add("card-title");
        HBox header = new HBox(title);
        header.getStyleClass().add("card-header");

        VBox body = new VBox(4, rows);
        body.setPadding(new Insets(6, 0, 6, 0));
        VBox card = new VBox(header, body);
        card.getStyleClass().add("card");
        return card;
    }

    /** Fila «título + ayuda a la izquierda, control a la derecha». */
    static HBox row(String name, Label hint, Node control) {
        Label label = new Label(name);
        label.getStyleClass().add("processor-title");
        VBox texts = new VBox(2, label, hint);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(16, texts, spacer, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 18, 10, 18));
        return row;
    }
}
