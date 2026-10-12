package com.textrefinery;

import com.github.kwhat.jnativehook.NativeHookException;
import com.textrefinery.application.CleanClipboardUseCase;
import com.textrefinery.application.PipelineFactory;
import com.textrefinery.application.RefineWithAiUseCase;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.AutostartException;
import com.textrefinery.domain.port.AutostartPort;
import com.textrefinery.domain.port.MessagesPort;
import com.textrefinery.domain.port.NotifierPort;
import com.textrefinery.infrastructure.ai.OllamaAiProvider;
import com.textrefinery.infrastructure.autostart.SystemAutostart;
import com.textrefinery.infrastructure.clipboard.AwtClipboardAdapter;
import com.textrefinery.infrastructure.config.AppPaths;
import com.textrefinery.infrastructure.config.JsonSettingsAdapter;
import com.textrefinery.infrastructure.hotkey.GlobalHotkeyManager;
import com.textrefinery.infrastructure.hotkey.Hotkey;
import com.textrefinery.infrastructure.i18n.ResourceBundleMessages;
import com.textrefinery.infrastructure.lock.SingleInstanceLock;
import com.textrefinery.infrastructure.logging.FileLogging;
import com.textrefinery.infrastructure.notify.LogNotifier;
import com.textrefinery.infrastructure.notify.TrayNotifier;
import com.textrefinery.infrastructure.platform.PlatformInfo;
import com.textrefinery.ui.components.BrandLogo;
import com.textrefinery.ui.settings.SettingsWindow;
import com.textrefinery.ui.tray.TrayController;
import java.awt.Desktop;
import java.awt.Taskbar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Punto de entrada y composición de dependencias (aquí, y solo aquí, se conectan los adaptadores). */
public final class App {

    // Debe ir antes de crear el primer logger: duplica la salida de SLF4J Simple en app.log.
    static {
        FileLogging.install(AppPaths.configDir().resolve("app.log"));
    }

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            // Solo tipo y traza, nunca el mensaje: podría arrastrar texto del usuario.
            log.error("Excepción no controlada en {}: {}", thread.getName(), error.getClass().getName());
            for (StackTraceElement frame : error.getStackTrace()) {
                log.error("    en {}", frame);
            }
        });
        log.info("Iniciando · Java {} · SO {} · lanzador {}", System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("jpackage.app-path", "(java -jar)"));

        SingleInstanceLock lock = new SingleInstanceLock(AppPaths.configDir().resolve("app.lock"));
        if (!lock.tryAcquire()) {
            log.info("Ya hay una instancia en ejecución; se cierra esta.");
            return;
        }

        JsonSettingsAdapter settingsAdapter = new JsonSettingsAdapter(AppPaths.configDir().resolve("settings.json"));
        AtomicReference<Settings> settings = new AtomicReference<>(settingsAdapter.load());
        AtomicBoolean paused = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // JavaFX solo se usa para la ventana de ajustes: no se cierra al cerrarla.
        Platform.startup(() -> { });
        Platform.setImplicitExit(false);

        // Idioma de la interfaz: todos los textos que ve el usuario salen de aquí y cambian en caliente.
        ResourceBundleMessages messages = new ResourceBundleMessages(settings.get().appLanguage);

        TrayController tray = new TrayController(messages);
        NotifierPort notifier = tray.icon().<NotifierPort>map(TrayNotifier::new).orElseGet(LogNotifier::new);

        AwtClipboardAdapter clipboard = new AwtClipboardAdapter();
        CleanClipboardUseCase clean = new CleanClipboardUseCase(
                clipboard, new PipelineFactory(), notifier, settings::get, messages);
        RefineWithAiUseCase refine = new RefineWithAiUseCase(
                clean, new OllamaAiProvider(settings::get), clipboard, notifier, settings::get, executor, messages);

        GlobalHotkeyManager hotkeys = new GlobalHotkeyManager(
                () -> executor.submit(() -> {
                    if (!paused.get()) {
                        clean.execute();
                    }
                }),
                () -> executor.submit(() -> {
                    if (!paused.get()) {
                        refine.execute(settings.get().aiMode);
                    }
                }));
        applyHotkeys(hotkeys, settings.get());

        Runnable exit = () -> {
            hotkeys.stop();
            tray.remove();
            Platform.exit();
            executor.shutdownNow();
            lock.release();
            System.exit(0);
        };

        AutostartPort autostart = new SystemAutostart();
        SettingsWindow settingsWindow = new SettingsWindow(settingsAdapter, settings::get, updated -> {
            settings.set(updated);
            applyHotkeys(hotkeys, updated);
            messages.setLanguage(updated.appLanguage);
            tray.refreshLanguage();
            tray.selectProfile(updated.defaultProfile);
            executor.submit(() -> syncAutostart(autostart, updated.launchAtLogin, notifier, messages));
        }, autostart, messages);

        // Al grabar un atajo nuevo se sueltan los atajos globales: si no, el que ya está asignado no llegaría a la ventana.
        settingsWindow.setOnShortcutCapture(hotkeys::setSuspended);

        tray.install(settings.get(), new TrayController.Actions(
                () -> executor.submit(clean::execute),
                () -> executor.submit(clean::restoreOriginal),
                mode -> executor.submit(() -> refine.execute(mode)),
                profile -> {
                    Settings updated = settings.get().copy();
                    updated.defaultProfile = profile;
                    settings.set(updated);
                    settingsAdapter.save(updated);
                },
                paused::set,
                settingsWindow::show,
                () -> executor.submit(() -> openLog(notifier, messages)),
                exit));

        try {
            hotkeys.start();
        } catch (NativeHookException | UnsatisfiedLinkError | RuntimeException e) {
            // La app sigue viva: el menú de la bandeja y la ventana de ajustes funcionan sin atajo global.
            log.error("No se pudo activar el atajo global: {}", e.getClass().getSimpleName());
            notifier.error(messages.get("notify.hotkeyFailed", messages.get(
                    PlatformInfo.hotkeyProblemKey(PlatformInfo.current(), PlatformInfo.isWayland()))));
        }

        if (tray.icon().isEmpty()) {
            // Sin bandeja (p. ej. algunos escritorios Linux) la ventana de ajustes es la única interfaz.
            log.warn("El sistema no ofrece bandeja: se abre la ventana de ajustes; al cerrarla termina la app.");
            settingsWindow.setOnClosed(exit);
            settingsWindow.show();
        }
        setDockIcon();
        log.info("Text Refinery listo. Limpieza: {}  IA: {}", settings.get().cleanHotkey, settings.get().aiHotkey);
        Runtime.getRuntime().addShutdownHook(new Thread(lock::release));
    }

    /** Abre app.log con el programa predeterminado del sistema (o su carpeta si no hay ninguno asociado). */
    private static void openLog(NotifierPort notifier, MessagesPort messages) {
        Path file = AppPaths.configDir().resolve("app.log");
        try {
            Desktop desktop = Desktop.getDesktop();
            if (Files.exists(file) && desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(file.toFile());
            } else {
                desktop.open(file.getParent().toFile());
            }
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo abrir el registro: {}", e.getClass().getSimpleName());
            notifier.error(messages.get("notify.openLogFailed", file));
        }
    }

    /** Lleva el arranque con el sistema al estado pedido; si el sistema lo rechaza, avisa y la app sigue. */
    private static void syncAutostart(AutostartPort autostart, boolean wanted, NotifierPort notifier, MessagesPort messages) {
        try {
            if (autostart.isSupported() && autostart.isEnabled() != wanted) {
                autostart.setEnabled(wanted);
                log.info("Arranque con el sistema: {}", wanted ? "activado" : "desactivado");
            }
        } catch (AutostartException e) {
            log.warn("No se pudo cambiar el arranque con el sistema: {}", e.getMessage());
            notifier.error(messages.get("notify.autostartFailed"));
        }
    }

    /** En macOS el icono del Dock sería el de Java; se sustituye por el logo (en otros sistemas no hace nada). */
    private static void setDockIcon() {
        if (PlatformInfo.current() != PlatformInfo.Os.MAC) {
            return;
        }
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(BrandLogo.awtImage(256));
            }
        } catch (RuntimeException e) {
            log.debug("No se pudo poner el icono del Dock: {}", e.getClass().getSimpleName());
        }
    }

    private static void applyHotkeys(GlobalHotkeyManager hotkeys, Settings s) {
        hotkeys.configure(Hotkey.parse(s.cleanHotkey), Hotkey.parse(s.aiHotkey));
    }
}
