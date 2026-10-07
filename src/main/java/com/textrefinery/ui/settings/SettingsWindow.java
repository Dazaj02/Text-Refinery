package com.textrefinery.ui.settings;

import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.SettingsPort;
import com.textrefinery.ui.components.BrandLogo;
import com.textrefinery.ui.components.Icons;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Ventana de ajustes (JavaFX): barra lateral, páginas y pie con Restaurar / Guardar. Solo existe mientras está abierta. */
public class SettingsWindow {

    private static final String HINT = "Los cambios se aplican al guardar.";

    private final SettingsPort port;
    private final Supplier<Settings> current;
    private final Consumer<Settings> onSaved;
    private Stage stage;
    private Runnable onClosed = () -> { };

    /** Se ejecuta cuando el usuario cierra la ventana (sin bandeja, cerrarla cierra la app). */
    public void setOnClosed(Runnable onClosed) {
        this.onClosed = onClosed;
    }

    public SettingsWindow(SettingsPort port, Supplier<Settings> current, Consumer<Settings> onSaved) {
        this.port = port;
        this.current = current;
        this.onSaved = onSaved;
    }

    /** Seguro de llamar desde cualquier hilo. */
    public void show() {
        Platform.runLater(() -> {
            if (stage != null && stage.isShowing()) {
                stage.setIconified(false);
                stage.toFront();
                return;
            }
            stage = createStage();
            stage.setOnHidden(e -> onClosed.run());
            stage.show();
        });
    }

    /** Construye la ventana sin mostrarla (también la usan las capturas de pantalla de documentación). */
    Stage createStage() {
        List<SettingsPage> pages = List.of(new CleaningPage(), new ShortcutsPage());
        pages.forEach(p -> p.load(current.get()));

        StackPane pageHost = new StackPane();
        for (SettingsPage page : pages) {
            page.view().setVisible(false);
            pageHost.getChildren().add(page.view());
        }

        Label status = new Label(HINT);
        status.getStyleClass().add("hint");
        PauseTransition resetStatus = new PauseTransition(Duration.seconds(3));
        resetStatus.setOnFinished(e -> showStatus(status, HINT, "hint"));

        Button restore = new Button("Restaurar");
        restore.getStyleClass().add("button-secondary");
        restore.setOnAction(e -> {
            pages.forEach(p -> p.load(current.get()));
            showStatus(status, HINT, "hint");
        });

        Button save = new Button("Guardar cambios");
        save.getStyleClass().add("button-primary");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            Settings updated = current.get().copy();
            try {
                pages.forEach(p -> p.apply(updated));
            } catch (IllegalArgumentException ex) {
                showStatus(status, ex.getMessage(), "footer-error");
                resetStatus.playFromStart();
                return;
            }
            port.save(updated);
            onSaved.accept(updated);
            showStatus(status, "Cambios guardados ✓", "footer-ok");
            resetStatus.playFromStart();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, status, spacer, restore, save);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(16, 36, 22, 36));

        BorderPane content = new BorderPane();
        content.setCenter(pageHost);
        content.setBottom(footer);

        BorderPane root = new BorderPane();
        root.setLeft(sidebar(pages, pageHost));
        root.setCenter(content);

        Scene scene = new Scene(root, 1040, 760);
        scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());

        Stage st = new Stage();
        st.setTitle("Text Refinery — Ajustes");
        st.getIcons().addAll(BrandLogo.fxImage(32), BrandLogo.fxImage(64), BrandLogo.fxImage(256));
        st.setMinWidth(940);
        st.setMinHeight(660);
        st.setScene(scene);
        return st;
    }

    private static void showStatus(Label label, String text, String styleClass) {
        label.setText(text);
        label.getStyleClass().removeAll("hint", "footer-ok", "footer-error");
        label.getStyleClass().add(styleClass);
    }

    private Node sidebar(List<SettingsPage> pages, StackPane pageHost) {
        ImageView mark = new ImageView(BrandLogo.fxImage(160)); // se dibuja grande y se reduce: queda nítido en pantallas HiDPI
        mark.setFitWidth(40);
        mark.setFitHeight(40);
        mark.setSmooth(true);
        mark.getStyleClass().add("brand-mark");
        Label name = new Label("Text Refinery");
        name.getStyleClass().add("brand-name");
        Label version = new Label("v0.1 · local");
        version.getStyleClass().add("brand-sub");
        HBox brand = new HBox(12, mark, new VBox(1, name, version));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 8, 24, 8));

        VBox nav = new VBox(4);
        ToggleGroup group = new ToggleGroup();
        for (SettingsPage page : pages) {
            ToggleButton item = new ToggleButton(page.title());
            item.getStyleClass().add("nav-button");
            item.setGraphic(Icons.outline(page.iconPath(), 18));
            item.setToggleGroup(group);
            item.setMaxWidth(Double.MAX_VALUE);
            item.selectedProperty().addListener((obs, was, now) -> page.view().setVisible(now));
            nav.getChildren().add(item);
        }
        group.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) {
                group.selectToggle(was);
            }
        });
        group.selectToggle(group.getToggles().get(0));

        Circle dot = new Circle(4);
        dot.getStyleClass().add("status-dot");
        Label cardTitle = new Label("Atajos activos");
        cardTitle.getStyleClass().add("sidebar-card-title");
        HBox cardHead = new HBox(8, dot, cardTitle);
        cardHead.setAlignment(Pos.CENTER_LEFT);
        Label cardText = new Label("Todo se procesa en tu equipo. Nada sale a internet.");
        cardText.getStyleClass().add("sidebar-card-text");
        cardText.setWrapText(true);
        VBox card = new VBox(8, cardHead, cardText);
        card.getStyleClass().add("sidebar-card");

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        VBox side = new VBox(brand, nav, grow, card);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(232);
        side.setMinWidth(232);
        return side;
    }
}
