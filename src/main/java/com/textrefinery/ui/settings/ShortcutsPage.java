package com.textrefinery.ui.settings;

import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.infrastructure.ai.OllamaAiProvider;
import com.textrefinery.infrastructure.hotkey.Hotkey;
import com.textrefinery.ui.components.Icons;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/** Atajos globales y conexión con el motor de IA (Ollama). */
final class ShortcutsPage implements SettingsPage {

    private static final Map<AiMode, String> MODE_HINTS = new EnumMap<>(AiMode.class);

    static {
        MODE_HINTS.put(AiMode.SUMMARIZE, "Reduce el texto a un párrafo fiel al original.");
        MODE_HINTS.put(AiMode.GRAMMAR, "Ortografía, puntuación y concordancia sin cambiar el sentido.");
        MODE_HINTS.put(AiMode.CITE, "Reescribe de forma formal sin inventar datos ni referencias.");
    }

    private final StringProperty cleanKey = new SimpleStringProperty("Ctrl+Shift+Y");
    private final StringProperty aiKey = new SimpleStringProperty("Ctrl+Shift+G");
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final Map<AiMode, ToggleButton> modeButtons = new EnumMap<>(AiMode.class);
    private final Label modeHint = new Label();
    private final TextField model = new TextField();
    private final TextField url = new TextField();
    private final TextField timeout = new TextField();
    private final Label connection = new Label();
    private final Circle connectionDot = new Circle(3.5);
    private final HBox connectionChip = new HBox(6);
    private final Label captureError = new Label();
    private final ScrollPane root = new ScrollPane();
    private boolean checkedOnce;

    ShortcutsPage() {
        Label title = new Label("Atajos e IA");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Un atajo rápido para limpiar y otro, opcional, que además usa IA local.");
        sub.getStyleClass().add("page-subtitle");

        GridPane shortcuts = new GridPane();
        shortcuts.setHgap(16);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50);
            shortcuts.getColumnConstraints().add(col);
        }
        shortcuts.add(shortcutCard("Limpiar portapapeles", "Instantáneo", "chip-green", cleanKey,
                "Funciona aunque Ollama no esté encendido."), 0, 0);
        shortcuts.add(shortcutCard("Limpiar y refinar con IA", "Opcional", "chip-red", aiKey,
                "Si la IA no responde, se aplica solo la limpieza."), 1, 0);

        captureError.getStyleClass().add("footer-error");
        captureError.setManaged(false);
        captureError.setVisible(false);

        VBox content = new VBox(18, new VBox(4, title, sub), shortcuts, captureError, aiCard());
        content.setPadding(new Insets(30, 36, 8, 36));
        root.setContent(content);
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        modeGroup.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) {
                modeGroup.selectToggle(was);
                return;
            }
            modeHint.setText(MODE_HINTS.get(selectedMode()));
        });
    }

    @Override
    public String title() {
        return "Atajos e IA";
    }

    @Override
    public String iconPath() {
        return Icons.KEYBOARD;
    }

    @Override
    public Node view() {
        return root;
    }

    @Override
    public void load(Settings s) {
        cleanKey.set(s.cleanHotkey);
        aiKey.set(s.aiHotkey);
        modeGroup.selectToggle(modeButtons.get(s.aiMode));
        model.setText(s.aiModel);
        url.setText(s.ollamaUrl);
        timeout.setText(String.valueOf(s.aiTimeoutSeconds));
        if (!checkedOnce) {
            checkedOnce = true;
            testConnection();
        }
    }

    @Override
    public void apply(Settings target) {
        Hotkey clean = Hotkey.parse(cleanKey.get());
        Hotkey ai = Hotkey.parse(aiKey.get());
        if (clean.equals(ai)) {
            throw new IllegalArgumentException("Los dos atajos no pueden ser iguales.");
        }
        if (model.getText().isBlank()) {
            throw new IllegalArgumentException("Escribe el nombre del modelo de Ollama.");
        }
        if (url.getText().isBlank()) {
            throw new IllegalArgumentException("Escribe la dirección de Ollama.");
        }
        int seconds;
        try {
            seconds = Integer.parseInt(timeout.getText().strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El tiempo máximo debe ser un número entre 5 y 600.");
        }
        if (seconds < 5 || seconds > 600) {
            throw new IllegalArgumentException("El tiempo máximo debe ser un número entre 5 y 600.");
        }
        target.cleanHotkey = cleanKey.get();
        target.aiHotkey = aiKey.get();
        target.aiMode = selectedMode();
        target.aiModel = model.getText().strip();
        target.ollamaUrl = url.getText().strip();
        target.aiTimeoutSeconds = seconds;
    }

    // ---------------------------------------------------------------- atajos

    private Node shortcutCard(String name, String badge, String badgeStyle, StringProperty key, String note) {
        Label title = new Label(name);
        title.getStyleClass().add("card-title");
        Label chip = new Label(badge);
        chip.getStyleClass().addAll("chip", badgeStyle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, title, spacer, chip);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox caps = new HBox(8);
        caps.setAlignment(Pos.CENTER_LEFT);
        Runnable render = () -> renderKeycaps(caps, key.get());
        key.addListener((obs, was, now) -> render.run());
        render.run();

        Button change = new Button("Cambiar");
        change.getStyleClass().add("button-outline");
        change.setOnAction(e -> capture(key, change, caps));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox keysRow = new HBox(8, caps, grow, change);
        keysRow.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(note);
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        VBox card = new VBox(14, top, keysRow, hint);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(18));
        return card;
    }

    private static void renderKeycaps(HBox box, String combo) {
        box.getChildren().clear();
        String[] parts = combo.split("\\+");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                Label plus = new Label("+");
                plus.getStyleClass().add("keycap-plus");
                box.getChildren().add(plus);
            }
            Label cap = new Label(parts[i].strip());
            cap.getStyleClass().add("keycap");
            if (i == parts.length - 1) {
                cap.getStyleClass().add("keycap-accent");
            }
            box.getChildren().add(cap);
        }
    }

    /** Escucha la próxima combinación en la ventana. Esc cancela; exige un modificador y una letra, número o tecla F. */
    private void capture(StringProperty target, Button button, HBox caps) {
        Scene scene = button.getScene();
        String original = button.getText();
        button.setText("Pulsa la combinación…");
        caps.getChildren().clear();
        Label waiting = new Label("Esperando teclas…");
        waiting.getStyleClass().add("keycap-capturing");
        caps.getChildren().add(waiting);
        hideCaptureError();

        List<EventHandler<KeyEvent>> holder = new ArrayList<>();
        Runnable finish = () -> {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, holder.get(0));
            button.setText(original);
            renderKeycaps(caps, target.get());
        };
        EventHandler<KeyEvent> handler = e -> {
            e.consume();
            KeyCode code = e.getCode();
            if (code == KeyCode.ESCAPE) {
                finish.run();
                return;
            }
            if (code.isModifierKey()) {
                return;
            }
            if (!(e.isControlDown() || e.isAltDown() || e.isShiftDown() || e.isMetaDown())) {
                showCaptureError("Añade Ctrl, Shift, Alt o Cmd al atajo.");
                return;
            }
            String key = code.getName().toUpperCase(Locale.ROOT);
            if (!key.matches("[A-Z0-9]|F\\d{1,2}")) {
                showCaptureError("Usa una letra, un número o una tecla F.");
                return;
            }
            List<String> parts = new ArrayList<>();
            if (e.isControlDown()) {
                parts.add("Ctrl");
            }
            if (e.isShiftDown()) {
                parts.add("Shift");
            }
            if (e.isAltDown()) {
                parts.add("Alt");
            }
            if (e.isMetaDown()) {
                parts.add("Meta");
            }
            parts.add(key);
            target.set(String.join("+", parts));
            hideCaptureError();
            finish.run();
        };
        holder.add(handler);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, handler);
    }

    private void showCaptureError(String message) {
        captureError.setText(message);
        captureError.setManaged(true);
        captureError.setVisible(true);
    }

    private void hideCaptureError() {
        captureError.setManaged(false);
        captureError.setVisible(false);
    }

    // ---------------------------------------------------------------- IA

    private Node aiCard() {
        Label title = new Label("Motor de IA · Ollama");
        title.getStyleClass().add("card-title");
        connectionChip.getChildren().addAll(connectionDot, connection);
        connectionChip.setAlignment(Pos.CENTER_LEFT);
        connectionChip.setMaxHeight(Region.USE_PREF_SIZE); // si no, el HBox del encabezado la estira
        connectionChip.getStyleClass().addAll("chip", "chip-muted");
        setConnection("Sin comprobar", null);
        Button test = new Button("Probar conexión");
        test.getStyleClass().add("button-secondary");
        test.setOnAction(e -> testConnection());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, connectionChip, spacer, test);
        header.getStyleClass().add("card-header");

        Label modeLabel = new Label("QUÉ HACER CON EL TEXTO");
        modeLabel.getStyleClass().add("section-label");
        HBox segments = new HBox(4);
        segments.getStyleClass().add("segment-group");
        for (AiMode mode : AiMode.values()) {
            ToggleButton b = new ToggleButton(mode.label());
            b.getStyleClass().add("segment");
            b.setToggleGroup(modeGroup);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
            modeButtons.put(mode, b);
            segments.getChildren().add(b);
        }
        modeHint.getStyleClass().add("hint");

        GridPane fields = new GridPane();
        fields.setHgap(14);
        double[] widths = {30, 50, 20};
        for (double w : widths) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(w);
            fields.getColumnConstraints().add(col);
        }
        fields.add(field("Modelo", model), 0, 0);
        fields.add(field("Dirección", url), 1, 0);
        fields.add(field("Tiempo máx. (s)", timeout), 2, 0);

        Node lock = Icons.outline(Icons.LOCK, 20);
        lock.getStyleClass().add("note-icon");
        Label noteText = new Label("100 % local. El contenido del portapapeles nunca se guarda en registros ni sale de tu equipo.");
        noteText.getStyleClass().add("note-text");
        noteText.setWrapText(true);
        HBox note = new HBox(12, new javafx.scene.layout.StackPane(lock), noteText);
        note.getStyleClass().add("note-box");

        VBox body = new VBox(18, new VBox(8, modeLabel, segments, modeHint), fields, note);
        body.setPadding(new Insets(18));

        VBox card = new VBox(header, body);
        card.getStyleClass().add("card");
        return card;
    }

    private static Node field(String label, TextField input) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        return new VBox(6, l, input);
    }

    private AiMode selectedMode() {
        Toggle selected = modeGroup.getSelectedToggle();
        for (Map.Entry<AiMode, ToggleButton> e : modeButtons.entrySet()) {
            if (e.getValue() == selected) {
                return e.getKey();
            }
        }
        return AiMode.GRAMMAR;
    }

    private void setConnection(String text, Boolean ok) {
        connection.setText(text);
        connectionChip.getStyleClass().removeAll("chip-muted", "chip-green", "chip-red");
        connectionDot.getStyleClass().removeAll("dot-green", "dot-red");
        if (ok == null) {
            connectionChip.getStyleClass().add("chip-muted");
            connectionDot.setVisible(false);
            connectionDot.setManaged(false);
        } else {
            connectionDot.setVisible(true);
            connectionDot.setManaged(true);
            connectionChip.getStyleClass().add(ok ? "chip-green" : "chip-red");
            connectionDot.getStyleClass().add(ok ? "dot-green" : "dot-red");
        }
        connection.setStyle("-fx-text-fill: inherit; -fx-font-size: 12px; -fx-font-weight: bold;");
    }

    /** Comprueba la dirección escrita (aunque aún no esté guardada), sin bloquear la ventana. */
    private void testConnection() {
        setConnection("Comprobando…", null);
        Settings probe = new Settings();
        probe.ollamaUrl = url.getText().isBlank() ? "http://localhost:11434" : url.getText().strip();
        Thread t = new Thread(() -> {
            boolean ok;
            try {
                ok = new OllamaAiProvider(() -> probe).isAvailable();
            } catch (RuntimeException e) {
                ok = false;
            }
            boolean result = ok;
            Platform.runLater(() -> setConnection(result ? "Conectado" : "Sin conexión", result));
        }, "ollama-check");
        t.setDaemon(true);
        t.start();
    }
}
