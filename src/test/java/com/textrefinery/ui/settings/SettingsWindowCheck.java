package com.textrefinery.ui.settings;

import com.textrefinery.domain.model.Language;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.AutostartPort;
import com.textrefinery.domain.port.SettingsPort;
import com.textrefinery.infrastructure.i18n.ResourceBundleMessages;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Comprobación interactiva de la ventana de ajustes: simula clics y teclas sobre la ventana real y imprime PASS/FAIL.
 * Necesita pantalla, por eso NO es una prueba de surefire (el nombre no termina en Test) y no corre en CI.
 * Ejecutar: mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.textrefinery.ui.settings.SettingsWindowCheck
 *
 * Los textos buscados están en español (idioma por defecto de la prueba); se cambia a inglés a mitad del guion.
 */
public final class SettingsWindowCheck {

    private static int fails = 0;
    private static Stage stage;
    private static final ResourceBundleMessages MESSAGES = new ResourceBundleMessages(Language.ES);
    private static Settings current = new Settings();
    private static final List<Settings> saved = new ArrayList<>();
    private static final List<Settings> applied = new ArrayList<>();

    private record Step(double waitMs, Runnable action) { }

    private static final List<Step> steps = new ArrayList<>();
    private static int next = 0;

    // ---------------------------------------------------------------- utilidades

    private static void check(String name, boolean ok, String detail) {
        if (!ok) {
            fails++;
        }
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + (detail.isEmpty() ? "" : "  -> " + detail));
    }

    private static void step(Runnable action) {
        steps.add(new Step(450, action));
    }

    private static void step(double waitMs, Runnable action) {
        steps.add(new Step(waitMs, action));
    }

    private static void runNext() {
        if (next >= steps.size()) {
            System.out.println(fails == 0 ? "RESULTADO: todo OK" : "RESULTADO: " + fails + " fallos");
            Platform.exit();
            System.exit(fails == 0 ? 0 : 1);
        }
        Step s = steps.get(next++);
        PauseTransition p = new PauseTransition(Duration.millis(s.waitMs));
        p.setOnFinished(e -> {
            try {
                s.action.run();
            } catch (Throwable t) {
                t.printStackTrace();
                Platform.exit();
                System.exit(2);
            }
            runNext();
        });
        p.play();
    }

    /** Siempre sobre la raíz actual: al guardar la ventana se reconstruye y la raíz cambia. */
    private static List<Node> all(String css) {
        return new ArrayList<>(stage.getScene().getRoot().lookupAll(css));
    }

    private static String preview(int index) {
        return ((TextArea) all(".text-area").get(index)).getText();
    }

    private static Button button(String style, String label) {
        for (Node n : all(style)) {
            if (n instanceof Button b && label.equals(b.getText())) {
                return b;
            }
        }
        throw new IllegalStateException("No hay botón '" + label + "'");
    }

    private static ToggleButton nav(int index) {
        return (ToggleButton) all(".nav-button").get(index);
    }

    private static ToggleButton segment(int index) {
        return (ToggleButton) all(".segment").get(index);
    }

    private static TextField field(int index) {
        return (TextField) all(".text-field").get(index);
    }

    private static List<String> keycaps() {
        return all(".keycap").stream().map(n -> ((Label) n).getText()).toList();
    }

    /** Visible de verdad: el nodo y todos sus padres (las páginas ocultas siguen en el árbol). */
    private static boolean shown(Node n) {
        for (Node p = n; p != null; p = p.getParent()) {
            if (!p.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static String footer() {
        for (Node n : all(".label")) {
            if (n instanceof Label l && l.getParent() instanceof javafx.scene.layout.HBox hb && hb.getChildren().size() == 4
                    && (l.getStyleClass().contains("hint") || l.getStyleClass().contains("footer-ok") || l.getStyleClass().contains("footer-error"))) {
                return l.getText() + "|" + String.join(",", l.getStyleClass());
            }
        }
        return "?";
    }

    private static void click(Node n) {
        Event.fireEvent(n, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null));
    }

    private static void key(KeyCode code, boolean ctrl, boolean shift) {
        Event.fireEvent(stage.getScene().getRoot(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, false, false));
    }

    private static boolean dark() {
        return stage.getScene().getRoot().getStyleClass().contains("dark");
    }

    // ---------------------------------------------------------------- guion

    private static void script() {
        // ===== Limpieza
        step(1200, () -> {
            List<Node> cards = all(".profile-card");
            check("hay 4 perfiles", cards.size() == 4, "" + cards.size());
            check("Word seleccionado al abrir", ((ToggleButton) cards.get(0)).isSelected(), "");
            String before = preview(1);
            check("vista previa produce resultado", before.contains("investigación") && !before.contains("inves-"), before.replace("\n", "|"));
            ((ToggleButton) cards.get(1)).fire();
        });
        step(() -> {
            check("LaTeX escapa el %", preview(1).contains("50\\%"), "");
            ((ToggleButton) all(".profile-card").get(2)).fire();
        });
        step(() -> {
            check("Markdown seleccionado", ((ToggleButton) all(".profile-card").get(2)).isSelected(), "");
            ((ToggleButton) all(".profile-card").get(2)).fire(); // clic sobre el ya elegido
        });
        step(200, () -> {
            check("no se puede dejar sin perfil", ((ToggleButton) all(".profile-card").get(2)).isSelected(), "");
            ((ToggleButton) all(".profile-card").get(0)).fire();
            click(all(".toggle-switch").get(5)); // "Saltos de línea" (los de filas ocultas también cuentan)
        });
        step(600, () -> {
            check("apagar 'Saltos de línea' conserva los saltos", preview(1).contains("\n"), preview(1).replace("\n", "|"));
            click(all(".toggle-switch").get(5));
        });

        // ===== Atajos e IA: captura de teclas
        step(() -> nav(1).fire());
        step(() -> {
            check("teclas iniciales Ctrl+Shift+Y y Ctrl+Shift+G", keycaps().equals(List.of("Ctrl", "Shift", "Y", "Ctrl", "Shift", "G")), keycaps().toString());
            button(".button-outline", "Cambiar").fire();
        });
        step(200, () -> {
            check("'Cambiar' entra en modo captura", button(".button-outline", "Pulsa la combinación…") != null, "");
            key(KeyCode.K, false, false); // sin modificador
        });
        step(150, () -> {
            check("rechaza tecla sin modificador", button(".button-outline", "Pulsa la combinación…") != null, "");
            key(KeyCode.COMMA, true, false); // Ctrl+, no soportada
        });
        step(150, () -> {
            check("rechaza tecla no soportada", button(".button-outline", "Pulsa la combinación…") != null, "");
            key(KeyCode.K, true, true); // Ctrl+Shift+K
        });
        step(250, () -> {
            check("captura Ctrl+Shift+K", keycaps().subList(0, 3).equals(List.of("Ctrl", "Shift", "K")), keycaps().toString());
            all(".button-outline").stream().filter(n -> n instanceof Button b && "Cambiar".equals(b.getText())).map(n -> (Button) n).toList().get(1).fire();
        });
        step(200, () -> key(KeyCode.ESCAPE, false, false));
        step(200, () -> {
            check("Esc cancela y conserva el atajo", keycaps().subList(3, 6).equals(List.of("Ctrl", "Shift", "G")), keycaps().toString());
            segment(0).fire(); // modo IA = Resumir
            field(0).setText("phi3");
        });

        // ===== Guardar: reconstruye la ventana y vuelve a la página principal
        step(() -> button(".button-primary", "Guardar cambios").fire());
        step(300, () -> {
            check("Guardar llama a SettingsPort.save", saved.size() == 1, "" + saved.size());
            Settings v = saved.get(0);
            check("se guardó el atajo nuevo", "Ctrl+Shift+K".equals(v.cleanHotkey), v.cleanHotkey);
            check("se guardó modelo y modo", "phi3".equals(v.aiModel) && v.aiMode.name().equals("SUMMARIZE"), v.aiModel + "/" + v.aiMode);
            check("onSaved notificó el cambio", applied.size() == 1, "" + applied.size());
            check("pie muestra 'guardados'", footer().startsWith("Cambios guardados"), footer());
            check("al guardar vuelve a la página principal (Limpieza)", nav(0).isSelected() && shown(all(".profile-card").get(0)), "");
            check("las demás páginas quedan ocultas", !shown(all(".keycap").get(0)), "");
            nav(1).fire();
        });
        step(() -> {
            check("la ventana reconstruida conserva lo guardado", field(0).getText().equals("phi3") && keycaps().subList(0, 3).equals(List.of("Ctrl", "Shift", "K")), field(0).getText() + " " + keycaps());
        });

        // ===== Un valor inválido NO redirige ni guarda
        step(() -> {
            field(2).setText("abc");
            button(".button-primary", "Guardar cambios").fire();
        });
        step(200, () -> {
            check("tiempo inválido muestra error, no guarda y NO redirige", footer().contains("footer-error") && saved.size() == 1 && nav(1).isSelected(), footer());
            field(2).setText("60");
            field(0).setText("temporal");
            button(".button-secondary", "Restaurar").fire();
        });
        step(200, () -> check("Restaurar recarga lo último guardado", "phi3".equals(field(0).getText()), field(0).getText()));
        step(() -> button(".button-secondary", "Probar conexión").fire());
        step(2500, () -> check("sin Ollama muestra 'Sin conexión'",
                all(".chip").stream().anyMatch(n -> n instanceof javafx.scene.layout.HBox hb
                        && hb.getChildren().stream().anyMatch(c -> c instanceof Label l && "Sin conexión".equals(l.getText()))), ""));

        // ===== General: tema (en vivo, guardado, persistente tras reconstruir)
        step(() -> nav(2).fire());
        step(() -> {
            check("el tema empieza en Claro", segment(3).isSelected() && !dark(), "");
            segment(4).fire();
        });
        step(300, () -> {
            check("Oscuro se aplica al instante (clase 'dark')", dark(), "");
            button(".button-primary", "Guardar cambios").fire();
        });
        step(300, () -> {
            check("Guardar persiste el tema", saved.get(saved.size() - 1).theme.name().equals("DARK"), "");
            check("el tema oscuro sobrevive a la reconstrucción", dark(), "");
            nav(2).fire();
        });
        step(() -> {
            segment(3).fire();
        });
        step(300, () -> {
            check("Claro quita la clase 'dark'", !dark(), "");
            button(".button-secondary", "Restaurar").fire();
        });
        step(300, () -> check("Restaurar revierte a lo guardado (Oscuro)", dark() && segment(4).isSelected(), ""));

        // ===== General: idioma de la aplicación
        step(() -> {
            segment(3).fire(); // vuelve a Claro para dejar el resto como al inicio
            segment(5).fire(); // (aún) Español
            check("el idioma actual es Español", segment(5).isSelected() && stage.getTitle().equals("Text Refinery — Ajustes"), stage.getTitle());
            segment(6).fire(); // English
        });
        step(() -> button(".button-primary", "Guardar cambios").fire());
        step(300, () -> {
            Settings v = saved.get(saved.size() - 1);
            check("se guardó el idioma de la aplicación (EN)", v.appLanguage == Language.EN, "" + v.appLanguage);
            check("el idioma del texto no cambió", v.textLanguage == Language.ES, "" + v.textLanguage);
            check("el título de la ventana cambia al instante", stage.getTitle().equals("Text Refinery — Settings"), stage.getTitle());
            List<String> navTexts = all(".nav-button").stream().map(n -> ((ToggleButton) n).getText()).toList();
            check("la barra lateral queda en inglés", navTexts.equals(List.of("Cleaning", "Shortcuts & AI", "General")), navTexts.toString());
            check("tras guardar vuelve a la página principal", nav(0).isSelected(), "");
            check("pie en inglés", footer().startsWith("Changes saved"), footer());
            check("la vista previa usa el texto de ejemplo en inglés", preview(0).contains("energy"), preview(0).replace("\n", "|"));
            check("los perfiles se traducen", all(".profile-name").stream().anyMatch(n -> ((Label) n).getText().equals("Plain text")), "");
            nav(1).fire();
        });
        step(() -> {
            check("la página de atajos también se traduce", button(".button-outline", "Change") != null && button(".button-primary", "Save changes") != null, "");
            nav(2).fire();
        });
        step(() -> {
            check("en inglés el selector marca English", segment(6).isSelected(), "");
            segment(5).fire(); // volver a Español
        });
        step(() -> button(".button-primary", "Save changes").fire());
        step(300, () -> {
            check("volver a Español funciona", stage.getTitle().equals("Text Refinery — Ajustes") && nav(0).getText().equals("Limpieza"), nav(0).getText());
        });
    }

    public static void main(String[] args) {
        Platform.startup(() -> { });
        Platform.runLater(() -> {
            SettingsWindow window = new SettingsWindow(new SettingsPort() {
                public Settings load() {
                    return current;
                }

                public void save(Settings s) {
                    saved.add(s);
                }
            }, () -> current, s -> {
                // Lo que hace App al guardar: ajustes nuevos y idioma activo nuevo.
                current = s;
                applied.add(s);
                MESSAGES.setLanguage(s.appLanguage);
            }, new AutostartPort() {
                public boolean isSupported() {
                    return false;
                }

                public boolean isEnabled() {
                    return false;
                }

                public void setEnabled(boolean e) {
                }
            }, MESSAGES);
            current.appLanguage = Language.ES; // independiente del idioma del equipo donde se ejecute
            stage = window.createStage();
            stage.show();
            script();
            runNext();
        });
    }
}
