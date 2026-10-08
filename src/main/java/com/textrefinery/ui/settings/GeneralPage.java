package com.textrefinery.ui.settings;

import com.textrefinery.domain.model.Language;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.model.ThemeMode;
import com.textrefinery.domain.port.AutostartPort;
import com.textrefinery.domain.port.MessagesPort;
import com.textrefinery.ui.components.Icons;
import com.textrefinery.ui.components.ToggleSwitch;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
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

/** Ajustes generales: arranque con el sistema, apariencia e idiomas (de la aplicación y del texto que se pide a la IA). */
final class GeneralPage implements SettingsPage {

    private final MessagesPort messages;
    private final AutostartPort autostart;
    private final ToggleGroup themeGroup = new ToggleGroup();
    private final Map<ThemeMode, ToggleButton> themeButtons = new EnumMap<>(ThemeMode.class);
    private final ToggleGroup appLanguageGroup = new ToggleGroup();
    private final Map<Language, ToggleButton> appLanguageButtons = new EnumMap<>(Language.class);
    private final ToggleGroup textLanguageGroup = new ToggleGroup();
    private final Map<Language, ToggleButton> textLanguageButtons = new EnumMap<>(Language.class);
    private final ToggleSwitch launchAtLogin = new ToggleSwitch();
    private final Label launchHint = new Label();
    private final VBox sections = new VBox(16);
    private final ScrollPane root = new ScrollPane();

    GeneralPage(MessagesPort messages, AutostartPort autostart, Consumer<ThemeMode> onThemeChosen) {
        this.messages = messages;
        this.autostart = autostart;

        Label title = new Label(messages.get("general.title"));
        title.getStyleClass().add("page-title");
        Label sub = new Label(messages.get("general.subtitle"));
        sub.getStyleClass().add("page-subtitle");

        launchAtLogin.setAccessibleText(messages.get("general.startup.row"));
        launchHint.getStyleClass().add("processor-hint");
        launchHint.setWrapText(true);
        sections.getChildren().add(card(messages.get("general.startup.card"),
                row(messages.get("general.startup.row"), launchHint, launchAtLogin)));

        Label themeHint = hint(messages.get("general.theme.hint"));
        HBox themeSegments = segments(ThemeMode.values(), themeGroup, themeButtons, m -> messages.get("theme." + m.name()));
        themeGroup.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) { // siempre debe haber un tema elegido
                themeGroup.selectToggle(was);
                return;
            }
            onThemeChosen.accept(selected(themeGroup, themeButtons, ThemeMode.LIGHT));
        });
        sections.getChildren().add(card(messages.get("general.appearance.card"),
                row(messages.get("general.theme.row"), themeHint, themeSegments)));

        Label appLanguageHint = hint(messages.get("general.appLanguage.hint"));
        HBox appLanguageSegments = segments(Language.values(), appLanguageGroup, appLanguageButtons, Language::label);
        keepOneSelected(appLanguageGroup);

        Label textLanguageHint = hint(messages.get("general.textLanguage.hint"));
        textLanguageHint.setMaxWidth(430);
        HBox textLanguageSegments = segments(Language.values(), textLanguageGroup, textLanguageButtons, Language::label);
        keepOneSelected(textLanguageGroup);

        sections.getChildren().add(card(messages.get("general.language.card"),
                row(messages.get("general.appLanguage.row"), appLanguageHint, appLanguageSegments),
                row(messages.get("general.textLanguage.row"), textLanguageHint, textLanguageSegments)));

        VBox content = new VBox(18, new VBox(4, title, sub), sections);
        content.setPadding(new Insets(30, 36, 8, 36));
        root.setContent(content);
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    @Override
    public String title() {
        return messages.get("nav.general");
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
        appLanguageGroup.selectToggle(appLanguageButtons.get(s.appLanguage));
        textLanguageGroup.selectToggle(textLanguageButtons.get(s.textLanguage));
        launchHint.setText(messages.get(supported ? "general.startup.hint.supported" : "general.startup.hint.unsupported"));
    }

    @Override
    public void apply(Settings target) {
        target.launchAtLogin = launchAtLogin.isSelected();
        target.theme = selected(themeGroup, themeButtons, ThemeMode.LIGHT);
        target.appLanguage = selected(appLanguageGroup, appLanguageButtons, Language.ES);
        target.textLanguage = selected(textLanguageGroup, textLanguageButtons, Language.ES);
    }

    // ---------------------------------------------------------------- piezas reutilizables

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("processor-hint");
        label.setWrapText(true);
        return label;
    }

    /** Grupo de botones segmentados con un botón por cada valor de un enum. */
    private static <E extends Enum<E>> HBox segments(E[] values, ToggleGroup group, Map<E, ToggleButton> buttons,
                                                     Function<E, String> text) {
        HBox box = new HBox(4);
        box.getStyleClass().add("segment-group");
        for (E value : values) {
            ToggleButton b = new ToggleButton(text.apply(value));
            b.getStyleClass().add("segment");
            b.setToggleGroup(group);
            b.setMinWidth(90);
            buttons.put(value, b);
            box.getChildren().add(b);
        }
        return box;
    }

    private static void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) {
                group.selectToggle(was);
            }
        });
    }

    private static <E extends Enum<E>> E selected(ToggleGroup group, Map<E, ToggleButton> buttons, E fallback) {
        Toggle selected = group.getSelectedToggle();
        for (Map.Entry<E, ToggleButton> e : buttons.entrySet()) {
            if (e.getValue() == selected) {
                return e.getKey();
            }
        }
        return fallback;
    }

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
