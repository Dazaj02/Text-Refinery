package com.textrefinery.ui.settings;

import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.MessagesPort;
import com.textrefinery.infrastructure.ai.OllamaAiProvider;
import com.textrefinery.infrastructure.hotkey.Hotkey;
import com.textrefinery.ui.components.Icons;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
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
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Window;

/** Atajos globales y conexión con el motor de IA (Ollama). */
final class ShortcutsPage implements SettingsPage {

    private final MessagesPort messages;
    /** Se avisa con {@code true} al empezar a grabar un atajo y con {@code false} al terminar (para soltar el atajo global). */
    private final Consumer<Boolean> hotkeysSuspended;
    private final ShortcutEditor cleanEditor;
    private final ShortcutEditor aiEditor;
    private ShortcutEditor activeEditor;
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final Map<AiMode, ToggleButton> modeButtons = new EnumMap<>(AiMode.class);
    private final Label modeHint = new Label();
    private final TextField model = new TextField();
    private final TextField url = new TextField();
    private final TextField timeout = new TextField();
    private final Label connection = new Label();
    private final Circle connectionDot = new Circle(3.5);
    private final HBox connectionChip = new HBox(6);
    private final ScrollPane root = new ScrollPane();
    private boolean checkedOnce;

    ShortcutsPage(MessagesPort messages, Consumer<Boolean> hotkeysSuspended) {
        this.messages = messages;
        this.hotkeysSuspended = hotkeysSuspended;
        // Cada editor necesita saber el valor del otro (para rechazar duplicados); se pide por método porque los campos
        // finales aún no están asignados dentro de una lambda del constructor.
        this.cleanEditor = new ShortcutEditor(Settings.DEFAULT_CLEAN_HOTKEY, this::aiValue);
        this.aiEditor = new ShortcutEditor(Settings.DEFAULT_AI_HOTKEY, this::cleanValue);

        Label title = new Label(messages.get("shortcuts.title"));
        title.getStyleClass().add("page-title");
        Label sub = new Label(messages.get("shortcuts.subtitle"));
        sub.getStyleClass().add("page-subtitle");

        GridPane shortcuts = new GridPane();
        shortcuts.setHgap(16);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50);
            shortcuts.getColumnConstraints().add(col);
        }
        shortcuts.add(cleanEditor.card(messages.get("shortcuts.clean.title"), messages.get("shortcuts.clean.badge"),
                "chip-green", messages.get("shortcuts.clean.note")), 0, 0);
        shortcuts.add(aiEditor.card(messages.get("shortcuts.ai.title"), messages.get("shortcuts.ai.badge"),
                "chip-red", messages.get("shortcuts.ai.note")), 1, 0);

        VBox content = new VBox(18, new VBox(4, title, sub), shortcuts, aiCard());
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
            modeHint.setText(messages.get("ai.mode." + selectedMode().name() + ".hint"));
        });
    }

    @Override
    public String title() {
        return messages.get("nav.shortcuts");
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
        cleanEditor.value.set(s.cleanHotkey);
        aiEditor.value.set(s.aiHotkey);
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
        Hotkey clean = parse(cleanEditor.value.get());
        Hotkey ai = parse(aiEditor.value.get());
        if (clean.equals(ai)) {
            throw new IllegalArgumentException(messages.get("error.hotkey.same"));
        }
        if (model.getText().isBlank()) {
            throw new IllegalArgumentException(messages.get("error.model.empty"));
        }
        if (url.getText().isBlank()) {
            throw new IllegalArgumentException(messages.get("error.url.empty"));
        }
        int seconds;
        try {
            seconds = Integer.parseInt(timeout.getText().strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(messages.get("error.timeout"));
        }
        if (seconds < 5 || seconds > 600) {
            throw new IllegalArgumentException(messages.get("error.timeout"));
        }
        target.cleanHotkey = cleanEditor.value.get();
        target.aiHotkey = aiEditor.value.get();
        target.aiMode = selectedMode();
        target.aiModel = model.getText().strip();
        target.ollamaUrl = url.getText().strip();
        target.aiTimeoutSeconds = seconds;
    }

    /** Si hay una grabación en curso la cancela y devuelve los atajos globales (al cerrar la ventana o reconstruirla). */
    @Override
    public void dispose() {
        if (activeEditor != null) {
            activeEditor.cancel();
        }
    }

    private String cleanValue() {
        return cleanEditor.value.get();
    }

    private String aiValue() {
        return aiEditor.value.get();
    }

    /** Hotkey.parse lanza mensajes técnicos; al usuario se le muestra el texto traducido. */
    private Hotkey parse(String combo) {
        try {
            return Hotkey.parse(combo);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(messages.get("error.hotkey.invalid", combo));
        }
    }

    private static boolean sameCombo(String a, String b) {
        try {
            return Hotkey.parse(a).equals(Hotkey.parse(b));
        } catch (IllegalArgumentException e) {
            return false;
        }
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
            cap.setMinWidth(Region.USE_PREF_SIZE); // una tecla nunca se recorta con «…»
            cap.getStyleClass().add("keycap");
            if (i == parts.length - 1) {
                cap.getStyleClass().add("keycap-accent");
            }
            box.getChildren().add(cap);
        }
    }

    // ---------------------------------------------------------------- editor de un atajo

    /**
     * Un atajo editable: muestra las teclas, permite grabar una combinación nueva y volver a la predeterminada.
     * Al grabar suspende los atajos globales, enseña en vivo lo que se va pulsando y se cancela con Esc,
     * con un clic fuera o al perder el foco la ventana.
     */
    private final class ShortcutEditor {
        final StringProperty value = new SimpleStringProperty();
        private final String defaultValue;
        private final Supplier<String> otherValue;
        private final HBox caps = new HBox(8);
        private final Button change = new Button();
        private final Button reset = new Button();
        private final Label error = new Label();
        private boolean capturing;
        private Scene scene;
        private Window window;
        private EventHandler<KeyEvent> keyFilter;
        private EventHandler<MouseEvent> mouseFilter;
        private ChangeListener<Boolean> focusListener;

        ShortcutEditor(String defaultValue, Supplier<String> otherValue) {
            this.defaultValue = defaultValue;
            this.otherValue = otherValue;
            value.set(defaultValue);

            caps.setAlignment(Pos.CENTER_LEFT);
            value.addListener((obs, was, now) -> {
                if (!capturing) {
                    renderKeycaps(caps, now);
                }
            });
            renderKeycaps(caps, defaultValue);

            change.setText(messages.get("shortcuts.change"));
            change.getStyleClass().add("button-outline");
            change.setOnAction(e -> {
                if (capturing) {
                    cancel();
                } else {
                    start();
                }
            });

            reset.setText(messages.get("shortcuts.reset"));
            reset.getStyleClass().add("button-ghost");
            reset.setTooltip(new Tooltip(messages.get("shortcuts.reset.tip", defaultValue)));
            reset.disableProperty().bind(value.isEqualTo(defaultValue)); // ya está en el predeterminado: nada que restablecer
            reset.setOnAction(e -> {
                if (capturing) {
                    cancel();
                }
                value.set(defaultValue);
                hideError();
            });

            error.getStyleClass().add("footer-error");
            error.setWrapText(true);
            hideError();
        }

        Node card(String name, String badge, String badgeStyle, String note) {
            Label title = new Label(name);
            title.getStyleClass().add("card-title");
            Label chip = new Label(badge);
            chip.getStyleClass().addAll("chip", badgeStyle);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox top = new HBox(8, title, spacer, chip);
            top.setAlignment(Pos.CENTER_LEFT);

            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            change.setMinWidth(Region.USE_PREF_SIZE); // nunca se recorta: es el botón principal de la tarjeta
            HBox keysRow = new HBox(8, caps, grow, change);
            keysRow.setAlignment(Pos.CENTER_LEFT);

            Label hint = new Label(note);
            hint.getStyleClass().add("hint");
            hint.setWrapText(true);
            HBox.setHgrow(hint, Priority.ALWAYS);
            hint.setMaxWidth(Double.MAX_VALUE);
            reset.setMinWidth(Region.USE_PREF_SIZE);
            // «Restablecer» va abajo, junto a la nota: en la fila de las teclas dejaba la tarjeta tan estrecha que se recortaban.
            HBox bottomRow = new HBox(8, hint, reset);
            bottomRow.setAlignment(Pos.CENTER_LEFT);

            VBox card = new VBox(12, top, keysRow, error, bottomRow);
            card.getStyleClass().add("card");
            card.setPadding(new Insets(18));
            return card;
        }

        // ------------------------------------------------------------ grabación

        void start() {
            if (activeEditor != null && activeEditor != this) {
                activeEditor.cancel(); // solo una grabación a la vez
            }
            scene = change.getScene();
            if (scene == null) {
                return;
            }
            window = scene.getWindow();
            capturing = true;
            activeEditor = this;
            hotkeysSuspended.accept(true); // si no, el atajo ya asignado nunca llegaría a esta ventana
            change.setText(messages.get("shortcuts.capturing.button"));
            hideError();
            showHeld(false, false, false, false);

            keyFilter = this::onKey;
            scene.addEventFilter(KeyEvent.ANY, keyFilter);
            mouseFilter = e -> {
                if (!isInside(e.getTarget(), change)) {
                    cancel(); // un clic en cualquier otro sitio cancela
                }
            };
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, mouseFilter);
            if (window != null) {
                focusListener = (obs, was, now) -> {
                    if (!now) {
                        cancel(); // sin foco la ventana no recibiría las teclas: mejor salir
                    }
                };
                window.focusedProperty().addListener(focusListener);
            }
        }

        private void onKey(KeyEvent e) {
            e.consume(); // durante la grabación ninguna tecla debe llegar a otros controles
            if (e.getEventType() == KeyEvent.KEY_TYPED) {
                return;
            }
            boolean pressed = e.getEventType() == KeyEvent.KEY_PRESSED;
            KeyCode code = e.getCode();
            if (pressed && code == KeyCode.ESCAPE) {
                cancel();
                return;
            }
            showHeld(e.isControlDown(), e.isShiftDown(), e.isAltDown(), e.isMetaDown());
            if (!pressed || code.isModifierKey()) {
                return;
            }
            if (!(e.isControlDown() || e.isAltDown() || e.isShiftDown() || e.isMetaDown())) {
                showError(messages.get("shortcuts.capture.needModifier"));
                return;
            }
            String key = code.getName().toUpperCase(Locale.ROOT);
            if (!key.matches("[A-Z0-9]|F\\d{1,2}")) {
                showError(messages.get("shortcuts.capture.badKey"));
                return;
            }
            String combo = format(e, key);
            if (sameCombo(combo, otherValue.get())) {
                showError(messages.get("shortcuts.capture.conflict"));
                return;
            }
            value.set(combo);
            finish();
        }

        private String format(KeyEvent e, String key) {
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
            return String.join("+", parts);
        }

        /** Enseña en vivo las teclas modificadoras que se están manteniendo, para que se vea que la app responde. */
        private void showHeld(boolean ctrl, boolean shift, boolean alt, boolean meta) {
            caps.getChildren().clear();
            List<String> held = new ArrayList<>();
            if (ctrl) {
                held.add("Ctrl");
            }
            if (shift) {
                held.add("Shift");
            }
            if (alt) {
                held.add("Alt");
            }
            if (meta) {
                held.add("Meta");
            }
            if (held.isEmpty()) {
                Label waiting = new Label(messages.get("shortcuts.capturing.label"));
                waiting.getStyleClass().add("keycap-capturing");
                caps.getChildren().add(waiting);
                return;
            }
            for (String h : held) {
                Label cap = new Label(h);
                cap.getStyleClass().add("keycap");
                Label plus = new Label("+");
                plus.getStyleClass().add("keycap-plus");
                caps.getChildren().addAll(cap, plus);
            }
            Label next = new Label("…");
            next.getStyleClass().addAll("keycap", "keycap-capturing");
            caps.getChildren().add(next);
        }

        /** Termina la grabación (con o sin valor nuevo): suelta los filtros y devuelve el atajo global. */
        private void finish() {
            if (!capturing) {
                return;
            }
            capturing = false;
            if (scene != null) {
                scene.removeEventFilter(KeyEvent.ANY, keyFilter);
                scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, mouseFilter);
            }
            if (window != null && focusListener != null) {
                window.focusedProperty().removeListener(focusListener);
            }
            change.setText(messages.get("shortcuts.change"));
            renderKeycaps(caps, value.get());
            activeEditor = null;
            hotkeysSuspended.accept(false);
        }

        void cancel() {
            finish();
            hideError();
        }

        private void showError(String text) {
            error.setText(text);
            error.setManaged(true);
            error.setVisible(true);
        }

        private void hideError() {
            error.setManaged(false);
            error.setVisible(false);
        }

        private boolean isInside(Object target, Node node) {
            for (Object n = target; n instanceof Node current; n = current.getParent()) {
                if (current == node) {
                    return true;
                }
            }
            return false;
        }
    }

    // ---------------------------------------------------------------- IA

    private Node aiCard() {
        Label title = new Label(messages.get("ai.title"));
        title.getStyleClass().add("card-title");
        connectionChip.getChildren().addAll(connectionDot, connection);
        connectionChip.setAlignment(Pos.CENTER_LEFT);
        connectionChip.setMaxHeight(Region.USE_PREF_SIZE); // si no, el HBox del encabezado la estira
        connectionChip.getStyleClass().addAll("chip", "chip-muted");
        setConnection(messages.get("ai.status.unchecked"), null);
        Button test = new Button(messages.get("ai.test"));
        test.getStyleClass().add("button-secondary");
        test.setOnAction(e -> testConnection());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, connectionChip, spacer, test);
        header.getStyleClass().add("card-header");

        Label modeLabel = new Label(messages.get("ai.modeLabel"));
        modeLabel.getStyleClass().add("section-label");
        HBox segments = new HBox(4);
        segments.getStyleClass().add("segment-group");
        for (AiMode mode : AiMode.values()) {
            ToggleButton b = new ToggleButton(messages.get("ai.mode." + mode.name()));
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
        fields.add(field(messages.get("ai.field.model"), model), 0, 0);
        fields.add(field(messages.get("ai.field.url"), url), 1, 0);
        fields.add(field(messages.get("ai.field.timeout"), timeout), 2, 0);

        Node lock = Icons.outline(Icons.LOCK, 20);
        lock.getStyleClass().add("note-icon");
        Label noteText = new Label(messages.get("ai.privacy"));
        noteText.getStyleClass().add("note-text");
        noteText.setWrapText(true);
        HBox note = new HBox(12, new StackPane(lock), noteText);
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
        setConnection(messages.get("ai.status.checking"), null);
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
            Platform.runLater(() -> setConnection(
                    messages.get(result ? "ai.status.connected" : "ai.status.disconnected"), result));
        }, "ollama-check");
        t.setDaemon(true);
        t.start();
    }
}
