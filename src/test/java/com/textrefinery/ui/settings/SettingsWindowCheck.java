package com.textrefinery.ui.settings;

import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.SettingsPort;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Comprobación interactiva de la ventana de ajustes: simula clics y teclas sobre la ventana real y imprime PASS/FAIL.
 * Necesita pantalla, por eso NO es una prueba de surefire (el nombre no termina en Test) y no corre en CI.
 * Ejecutar: mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.textrefinery.ui.settings.SettingsWindowCheck
 */
public final class SettingsWindowCheck {
    static int fails = 0;
    static Stage st;
    static Parent root;
    static final List<Settings> saved = new ArrayList<>();
    static final List<Settings> applied = new ArrayList<>();

    static void check(String name, boolean ok, String detail) {
        if (!ok) fails++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + (detail.isEmpty() ? "" : "  -> " + detail));
    }

    static void later(double ms, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(e -> { try { r.run(); } catch (Throwable t) { t.printStackTrace(); fails++; Platform.exit(); System.exit(2); } });
        p.play();
    }

    static List<Node> all(String css) { return new ArrayList<>(root.lookupAll(css)); }
    static String text(Node n) { return ((TextArea) n).getText(); }
    static Button button(String style, String label) {
        for (Node n : all(style)) if (n instanceof Button b && label.equals(b.getText())) return b;
        return null;
    }
    static String footer() {
        for (Node n : all(".label")) {
            if (n instanceof Label l && (l.getStyleClass().contains("hint") || l.getStyleClass().contains("footer-ok") || l.getStyleClass().contains("footer-error"))
                    && l.getParent() != null && l.getParent().getStyleClass().isEmpty() && l.getParent() instanceof javafx.scene.layout.HBox hb && hb.getChildren().contains(l) && hb.getChildren().size() == 4) {
                return l.getText() + "|" + String.join(",", l.getStyleClass());
            }
        }
        return "?";
    }
    static void key(KeyCode code, boolean ctrl, boolean shift, boolean alt) {
        Event.fireEvent(st.getScene().getRoot(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, alt, false));
    }

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        Platform.runLater(() -> {
            Settings s = new Settings();
            SettingsWindow w = new SettingsWindow(new SettingsPort() {
                public Settings load() { return s; }
                public void save(Settings x) { saved.add(x); }
            }, () -> s, applied::add);
            st = w.createStage();
            st.show();
            root = st.getScene().getRoot();

            later(1200, () -> {
                // --- Página Limpieza
                List<Node> cards = all(".profile-card");
                check("hay 4 perfiles", cards.size() == 4, "" + cards.size());
                check("Word seleccionado al abrir", ((ToggleButton) cards.get(0)).isSelected(), "");
                String before = text(all(".text-area").get(1));
                check("vista previa produce resultado", before.contains("investigación") && !before.contains("inves-"), before.replace("\n", "|"));

                ((ToggleButton) cards.get(1)).fire();               // LaTeX
                later(500, () -> {
                    check("LaTeX escapa el %", text(all(".text-area").get(1)).contains("50\\%"), "");
                    ((ToggleButton) cards.get(2)).fire();           // Markdown
                    later(500, () -> {
                        check("Markdown seleccionado", ((ToggleButton) cards.get(2)).isSelected(), "");
                        ((ToggleButton) cards.get(2)).fire();       // clic sobre el ya elegido: debe seguir elegido
                        later(200, () -> {
                            check("no se puede dejar sin perfil", ((ToggleButton) cards.get(2)).isSelected(), "");
                            ((ToggleButton) cards.get(0)).fire();   // vuelve a Word
                            // Interruptor: apagar "Saltos de línea" y ver que la vista previa lo refleja
                            Node sw = all(".toggle-switch").stream().filter(n -> true).toList().get(5);
                            Event.fireEvent(sw, new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                                    javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true, false, false, null));
                            later(600, () -> {
                                String out = text(all(".text-area").get(1));
                                check("apagar 'Saltos de línea' conserva los saltos", out.contains("\n"), out.replace("\n", "|"));
                                Event.fireEvent(sw, new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                                        javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true, false, false, null));
                                paso2();
                            });
                        });
                    });
                });
            });
        });
    }

    static void paso2() {
        later(500, () -> {
            // --- Página Atajos e IA
            ((ToggleButton) all(".nav-button").get(1)).fire();
            later(500, () -> {
                List<String> caps = all(".keycap").stream().map(n -> ((Label) n).getText()).toList();
                check("teclas iniciales Ctrl+Shift+Y y Ctrl+Shift+G", caps.equals(List.of("Ctrl", "Shift", "Y", "Ctrl", "Shift", "G")), caps.toString());

                Button change = button(".button-outline", "Cambiar");
                change.fire();
                later(200, () -> {
                    check("'Cambiar' entra en modo captura", change.getText().startsWith("Pulsa"), change.getText());
                    key(KeyCode.K, false, false, false);                        // sin modificador: debe rechazarse
                    later(150, () -> {
                        check("rechaza tecla sin modificador", change.getText().startsWith("Pulsa"), "");
                        key(KeyCode.COMMA, true, false, false);                 // Ctrl+, no soportada
                        later(150, () -> {
                            check("rechaza tecla no soportada", change.getText().startsWith("Pulsa"), "");
                            key(KeyCode.K, true, true, false);                  // Ctrl+Shift+K válido
                            later(250, () -> {
                                List<String> caps2 = all(".keycap").stream().map(n -> ((Label) n).getText()).toList();
                                check("captura Ctrl+Shift+K", caps2.subList(0, 3).equals(List.of("Ctrl", "Shift", "K")) && change.getText().equals("Cambiar"), caps2.toString());
                                paso3();
                            });
                        });
                    });
                });
            });
        });
    }

    static void paso3() {
        // Esc cancela
        Button change2 = all(".button-outline").stream().filter(n -> n instanceof Button b && "Cambiar".equals(b.getText())).map(n -> (Button) n).toList().get(1);
        change2.fire();
        later(200, () -> {
            key(KeyCode.ESCAPE, false, false, false);
            later(200, () -> {
                List<String> caps = all(".keycap").stream().map(n -> ((Label) n).getText()).toList();
                check("Esc cancela y conserva el atajo", caps.subList(3, 6).equals(List.of("Ctrl", "Shift", "G")) && change2.getText().equals("Cambiar"), caps.toString());

                // Guardar con valores válidos (modo IA = Resumir, modelo nuevo)
                ((ToggleButton) all(".segment").get(0)).fire();
                TextField model = (TextField) all(".text-field").get(0);
                model.setText("phi3");
                button(".button-primary", "Guardar cambios").fire();
                later(300, () -> {
                    check("Guardar llama a SettingsPort.save", saved.size() == 1, "" + saved.size());
                    Settings v = saved.isEmpty() ? new Settings() : saved.get(0);
                    check("se guardó el atajo nuevo", "Ctrl+Shift+K".equals(v.cleanHotkey), v.cleanHotkey);
                    check("se guardó modelo y modo", "phi3".equals(v.aiModel) && v.aiMode.name().equals("SUMMARIZE"), v.aiModel + "/" + v.aiMode);
                    check("onSaved notificó el cambio", applied.size() == 1, "" + applied.size());
                    check("pie muestra 'guardados'", footer().startsWith("Cambios guardados"), footer());

                    // Validación: dos atajos iguales
                    ((TextField) all(".text-field").get(2)).setText("abc");
                    button(".button-primary", "Guardar cambios").fire();
                    later(200, () -> {
                        check("tiempo inválido muestra error y no guarda", footer().contains("footer-error") && saved.size() == 1, footer());
                        ((TextField) all(".text-field").get(2)).setText("60");

                        // Probar conexión sin Ollama
                        button(".button-secondary", "Probar conexión").fire();
                        later(2500, () -> {
                            boolean sinConexion = all(".chip").stream().anyMatch(n -> n instanceof Label l && "Sin conexión".equals(l.getText()))
                                    || all(".chip-red").stream().anyMatch(n -> n instanceof javafx.scene.layout.HBox hb && hb.getChildren().stream().anyMatch(c -> c instanceof Label l && "Sin conexión".equals(l.getText())));
                            check("sin Ollama muestra 'Sin conexión'", sinConexion, "");

                            // Restaurar vuelve a los valores guardados en memoria (current)
                            button(".button-secondary", "Restaurar").fire();
                            later(200, () -> {
                                check("Restaurar recarga el modelo original", "llama3.2".equals(((TextField) all(".text-field").get(0)).getText()), ((TextField) all(".text-field").get(0)).getText());
                                System.out.println(fails == 0 ? "RESULTADO: todo OK" : "RESULTADO: " + fails + " fallos");
                                Platform.exit();
                                System.exit(fails == 0 ? 0 : 1);
                            });
                        });
                    });
                });
            });
        });
    }
}

