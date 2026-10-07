package com.textrefinery.ui.settings;

import com.textrefinery.application.PipelineFactory;
import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.model.ProcessingContext;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.ui.components.Fonts;
import com.textrefinery.ui.components.Icons;
import com.textrefinery.ui.components.ToggleSwitch;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Perfil de salida, procesadores activos y vista previa en vivo con el pipeline real. */
final class CleaningPage implements SettingsPage {

    /** Un procesador configurable. {@code onlyFor} vacío = aplica a todos los perfiles. */
    record ProcessorInfo(String name, String label, String hint, Set<CleaningProfile> onlyFor) {
    }

    /** Debe coincidir con {@link PipelineFactory#processorNames()} (lo comprueba una prueba). */
    static final List<ProcessorInfo> PROCESSORS = List.of(
            new ProcessorInfo("HtmlToMarkdownConverter", "HTML a Markdown", "Convierte lo copiado de la web", Set.of(CleaningProfile.MARKDOWN)),
            new ProcessorInfo("HtmlStripper", "Quitar etiquetas HTML", "Deja solo el texto", Set.of(CleaningProfile.PLAIN)),
            new ProcessorInfo("ControlCharRemover", "Caracteres invisibles", "Control y ancho cero", Set.of()),
            new ProcessorInfo("LigatureFixer", "Ligaduras", "ﬁ → fi · ﬂ → fl", Set.of()),
            new ProcessorInfo("HyphenationFixer", "Guiones de partición", "infor-↵mación → información", Set.of()),
            new ProcessorInfo("LineBreakJoiner", "Saltos de línea", "Une líneas, respeta párrafos", Set.of()),
            new ProcessorInfo("QuoteNormalizer", "Comillas rectas", "Comillas curvas → rectas", Set.of()),
            new ProcessorInfo("WhitespaceNormalizer", "Espacios", "Colapsa y recorta", Set.of()),
            new ProcessorInfo("LatexEscaper", "Escapar para LaTeX", "% & _ # $ { } ~ ^ \\", Set.of(CleaningProfile.LATEX)));

    private static final String SAMPLE = "El 50% de la inves-\ntigación sobre la eﬁciencia   energética crece.\n"
            + "El “consumo”   sigue\nsubiendo.";

    private static final Map<CleaningProfile, String[]> PROFILE_TEXT = new EnumMap<>(CleaningProfile.class);

    static {
        PROFILE_TEXT.put(CleaningProfile.WORD, new String[]{"W", "Texto limpio para documentos."});
        PROFILE_TEXT.put(CleaningProfile.LATEX, new String[]{"Tx", "Escapa % & _ # $ { }."});
        PROFILE_TEXT.put(CleaningProfile.MARKDOWN, new String[]{"M↓", "Convierte el HTML copiado."});
        PROFILE_TEXT.put(CleaningProfile.PLAIN, new String[]{"Aa", "Sin etiquetas ni formato."});
    }

    private final PipelineFactory factory = new PipelineFactory();
    private final ToggleGroup profileGroup = new ToggleGroup();
    private final Map<CleaningProfile, ToggleButton> cards = new EnumMap<>(CleaningProfile.class);
    private final Map<String, ToggleSwitch> switches = new LinkedHashMap<>();
    private final Map<String, Node> rows = new LinkedHashMap<>();
    private final TextArea input = new TextArea(SAMPLE);
    private final TextArea output = new TextArea();
    private final Label deltaChip = new Label();
    private final Label resultLabel = new Label();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(150));
    private final ScrollPane root = new ScrollPane();

    CleaningPage() {
        debounce.setOnFinished(e -> refresh());

        VBox content = new VBox(22, header(), profiles(), workArea());
        content.setPadding(new Insets(30, 36, 8, 36));
        root.setContent(content);
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        profileGroup.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) { // un perfil siempre debe quedar elegido
                profileGroup.selectToggle(was);
                return;
            }
            updateRowVisibility();
            refresh();
        });
    }

    @Override
    public String title() {
        return "Limpieza";
    }

    @Override
    public String iconPath() {
        return Icons.SPARKLE;
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void load(Settings s) {
        profileGroup.selectToggle(cards.get(s.defaultProfile));
        switches.forEach((name, sw) -> sw.setSelected(!s.disabledProcessors.contains(name)));
        updateRowVisibility();
        refresh();
    }

    @Override
    public void apply(Settings target) {
        target.defaultProfile = selectedProfile();
        Set<String> disabled = new HashSet<>();
        switches.forEach((name, sw) -> {
            if (!sw.isSelected()) {
                disabled.add(name);
            }
        });
        target.disabledProcessors = disabled;
    }

    // ---------------------------------------------------------------- construcción

    private Node header() {
        Label title = new Label("Limpieza");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Elige cómo quieres recibir el texto que pegas.");
        sub.getStyleClass().add("page-subtitle");
        return new VBox(4, title, sub);
    }

    private Node profiles() {
        Label label = new Label("PERFIL DE SALIDA");
        label.getStyleClass().add("section-label");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(25);
            grid.getColumnConstraints().add(col);
        }
        int column = 0;
        for (CleaningProfile profile : CleaningProfile.values()) {
            ToggleButton card = profileCard(profile);
            cards.put(profile, card);
            grid.add(card, column++, 0);
        }
        return new VBox(10, label, grid);
    }

    private ToggleButton profileCard(CleaningProfile profile) {
        String[] text = PROFILE_TEXT.get(profile);

        Label glyph = new Label(text[0]);
        glyph.getStyleClass().add("profile-badge-text");
        StackPane badge = new StackPane(glyph);
        badge.getStyleClass().add("profile-badge");

        Label name = new Label(profile.label());
        name.getStyleClass().add("profile-name");
        Label desc = new Label(text[1]);
        desc.getStyleClass().add("profile-desc");
        desc.setWrapText(true);

        VBox box = new VBox(6, badge, name, desc);
        box.setAlignment(Pos.TOP_LEFT);

        ToggleButton card = new ToggleButton();
        card.getStyleClass().add("profile-card");
        card.setGraphic(box);
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefHeight(148); // fija: el espacio sobrante debe ir a la vista previa, no a las tarjetas
        card.setMinHeight(Region.USE_PREF_SIZE);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setToggleGroup(profileGroup);
        card.setAccessibleText(profile.label() + ". " + text[1]);
        desc.maxWidthProperty().bind(card.widthProperty().subtract(32));
        return card;
    }

    private Node workArea() {
        Node preview = previewCard();
        Node processors = processorsCard();
        HBox row = new HBox(16, preview, processors);
        HBox.setHgrow(preview, Priority.ALWAYS);
        ((Region) preview).setPrefWidth(520);
        ((Region) processors).setPrefWidth(330);
        ((Region) processors).setMinWidth(300);
        row.setMinHeight(300);
        VBox.setVgrow(row, Priority.ALWAYS);
        return row;
    }

    private Node previewCard() {
        Label title = new Label("Vista previa");
        title.getStyleClass().add("card-title");
        deltaChip.getStyleClass().addAll("chip", "chip-red");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer, deltaChip);
        header.getStyleClass().add("card-header");

        Label originalLabel = new Label("ORIGINAL (editable)");
        originalLabel.getStyleClass().add("pane-label");
        configure(input, true);
        input.textProperty().addListener((obs, was, now) -> debounce.playFromStart());
        VBox left = new VBox(10, originalLabel, input);
        left.getStyleClass().add("pane-left");
        left.setPadding(new Insets(16, 18, 14, 18));
        VBox.setVgrow(input, Priority.ALWAYS);

        resultLabel.getStyleClass().add("pane-label-red");
        configure(output, false);
        VBox right = new VBox(10, resultLabel, output);
        right.getStyleClass().add("pane-result");
        right.setPadding(new Insets(16, 18, 14, 18));
        VBox.setVgrow(output, Priority.ALWAYS);

        HBox panes = new HBox(left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(10);
        right.setPrefWidth(10);
        VBox.setVgrow(panes, Priority.ALWAYS);

        VBox card = new VBox(header, panes);
        card.getStyleClass().add("card");
        return card;
    }

    private static void configure(TextArea area, boolean editable) {
        area.getStyleClass().add("mono");
        area.setStyle("-fx-font-family: '" + Fonts.mono() + "'; -fx-font-size: 13px;");
        area.setWrapText(true);
        area.setEditable(editable);
        area.setMinHeight(120);
    }

    private Node processorsCard() {
        Label title = new Label("Procesadores");
        title.getStyleClass().add("card-title");
        HBox header = new HBox(title);
        header.getStyleClass().add("card-header");

        VBox list = new VBox(2);
        list.setPadding(new Insets(6, 0, 6, 0));
        for (ProcessorInfo info : PROCESSORS) {
            Label name = new Label(info.label());
            name.getStyleClass().add("processor-title");
            Label hint = new Label(info.hint());
            hint.getStyleClass().add("processor-hint");
            VBox texts = new VBox(1, name, hint);

            ToggleSwitch sw = new ToggleSwitch();
            sw.setAccessibleText(info.label());
            sw.selectedProperty().addListener((obs, was, now) -> debounce.playFromStart());
            switches.put(info.name(), sw);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(12, texts, spacer, sw);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 18, 8, 18));
            rows.put(info.name(), row);
            list.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox card = new VBox(header, scroll);
        card.getStyleClass().add("card");
        return card;
    }

    // ---------------------------------------------------------------- comportamiento

    private CleaningProfile selectedProfile() {
        Toggle selected = profileGroup.getSelectedToggle();
        for (Map.Entry<CleaningProfile, ToggleButton> e : cards.entrySet()) {
            if (e.getValue() == selected) {
                return e.getKey();
            }
        }
        return CleaningProfile.WORD;
    }

    private void updateRowVisibility() {
        CleaningProfile profile = selectedProfile();
        for (ProcessorInfo info : PROCESSORS) {
            boolean visible = info.onlyFor().isEmpty() || info.onlyFor().contains(profile);
            Node row = rows.get(info.name());
            row.setVisible(visible);
            row.setManaged(visible);
        }
    }

    private void refresh() {
        CleaningProfile profile = selectedProfile();
        Set<String> disabled = new HashSet<>();
        switches.forEach((name, sw) -> {
            if (!sw.isSelected()) {
                disabled.add(name);
            }
        });
        String in = input.getText();
        String out;
        try {
            out = factory.build(profile, disabled).process(in, ProcessingContext.of(profile));
        } catch (RuntimeException e) {
            out = "No se pudo procesar la vista previa (" + e.getClass().getSimpleName() + ")";
        }
        output.setText(out);
        resultLabel.setText("RESULTADO · " + profile.label().toUpperCase(Locale.ROOT));

        int delta = in.length() - out.length();
        deltaChip.setText(delta >= 0 ? delta + " caracteres menos" : (-delta) + " caracteres más");
    }
}
