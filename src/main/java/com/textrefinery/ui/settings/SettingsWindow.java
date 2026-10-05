package com.textrefinery.ui.settings;

import com.textrefinery.application.PipelineFactory;
import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.SettingsPort;
import com.textrefinery.infrastructure.hotkey.Hotkey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Ventana de ajustes (JavaFX). Solo existe mientras está abierta. */
public class SettingsWindow {

    private final SettingsPort port;
    private final Supplier<Settings> current;
    private final Consumer<Settings> onSaved;
    private Stage stage;

    public SettingsWindow(SettingsPort port, Supplier<Settings> current, Consumer<Settings> onSaved) {
        this.port = port;
        this.current = current;
        this.onSaved = onSaved;
    }

    /** Seguro de llamar desde cualquier hilo. */
    public void show() {
        Platform.runLater(() -> {
            if (stage != null && stage.isShowing()) {
                stage.toFront();
                return;
            }
            stage = build();
            stage.show();
        });
    }

    private Stage build() {
        Settings s = current.get();

        ComboBox<CleaningProfile> profile = new ComboBox<>();
        profile.getItems().addAll(CleaningProfile.values());
        profile.setValue(s.defaultProfile);

        TextField cleanKey = new TextField(s.cleanHotkey);
        TextField aiKey = new TextField(s.aiHotkey);

        ComboBox<AiMode> mode = new ComboBox<>();
        mode.getItems().addAll(AiMode.values());
        mode.setValue(s.aiMode);

        TextField model = new TextField(s.aiModel);
        TextField url = new TextField(s.ollamaUrl);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        int row = 0;
        grid.addRow(row++, new Label("Perfil por defecto"), profile);
        grid.addRow(row++, new Label("Atajo de limpieza"), cleanKey);
        grid.addRow(row++, new Label("Atajo con IA"), aiKey);
        grid.addRow(row++, new Label("Modo de IA"), mode);
        grid.addRow(row++, new Label("Modelo Ollama"), model);
        grid.addRow(row++, new Label("URL de Ollama"), url);

        VBox processors = new VBox(4);
        Map<String, CheckBox> boxes = new LinkedHashMap<>();
        for (String name : PipelineFactory.processorNames()) {
            CheckBox box = new CheckBox(name);
            box.setSelected(!s.disabledProcessors.contains(name));
            boxes.put(name, box);
            processors.getChildren().add(box);
        }

        Button save = new Button("Guardar");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            try {
                Hotkey.parse(cleanKey.getText());
                Hotkey.parse(aiKey.getText());
            } catch (IllegalArgumentException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                return;
            }
            Settings updated = current.get().copy();
            updated.defaultProfile = profile.getValue();
            updated.cleanHotkey = cleanKey.getText().strip();
            updated.aiHotkey = aiKey.getText().strip();
            updated.aiMode = mode.getValue();
            updated.aiModel = model.getText().strip();
            updated.ollamaUrl = url.getText().strip();
            updated.disabledProcessors.clear();
            boxes.forEach((name, box) -> {
                if (!box.isSelected()) {
                    updated.disabledProcessors.add(name);
                }
            });
            port.save(updated);
            onSaved.accept(updated);
            stage.close();
        });

        VBox root = new VBox(14, grid, new Label("Procesadores activos"), processors, save);
        root.setPadding(new Insets(18));

        Stage st = new Stage();
        st.setTitle("Text Refinery — Ajustes");
        st.setScene(new Scene(root));
        return st;
    }
}
