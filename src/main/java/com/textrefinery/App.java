package com.textrefinery;

import com.textrefinery.application.CleanClipboardUseCase;
import com.textrefinery.application.PipelineFactory;
import com.textrefinery.application.RefineWithAiUseCase;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.NotifierPort;
import com.textrefinery.infrastructure.ai.OllamaAiProvider;
import com.textrefinery.infrastructure.clipboard.AwtClipboardAdapter;
import com.textrefinery.infrastructure.config.AppPaths;
import com.textrefinery.infrastructure.config.JsonSettingsAdapter;
import com.textrefinery.infrastructure.hotkey.GlobalHotkeyManager;
import com.textrefinery.infrastructure.hotkey.Hotkey;
import com.textrefinery.infrastructure.lock.SingleInstanceLock;
import com.textrefinery.infrastructure.notify.LogNotifier;
import com.textrefinery.infrastructure.notify.TrayNotifier;
import com.textrefinery.ui.settings.SettingsWindow;
import com.textrefinery.ui.tray.TrayController;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Punto de entrada y composición de dependencias (aquí, y solo aquí, se conectan los adaptadores). */
public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false");

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

        TrayController tray = new TrayController();
        NotifierPort notifier = tray.icon().<NotifierPort>map(TrayNotifier::new).orElseGet(LogNotifier::new);

        AwtClipboardAdapter clipboard = new AwtClipboardAdapter();
        CleanClipboardUseCase clean = new CleanClipboardUseCase(clipboard, new PipelineFactory(), notifier, settings::get);
        RefineWithAiUseCase refine = new RefineWithAiUseCase(
                clean, new OllamaAiProvider(settings::get), clipboard, notifier, settings::get, executor);

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

        SettingsWindow settingsWindow = new SettingsWindow(settingsAdapter, settings::get, updated -> {
            settings.set(updated);
            applyHotkeys(hotkeys, updated);
        });

        tray.install(settings.get(), new TrayController.Actions(
                () -> executor.submit(clean::execute),
                mode -> executor.submit(() -> refine.execute(mode)),
                profile -> {
                    Settings updated = settings.get().copy();
                    updated.defaultProfile = profile;
                    settings.set(updated);
                    settingsAdapter.save(updated);
                },
                paused::set,
                settingsWindow::show,
                exit));

        hotkeys.start();
        log.info("Text Refinery listo. Limpieza: {}  IA: {}", settings.get().cleanHotkey, settings.get().aiHotkey);
        Runtime.getRuntime().addShutdownHook(new Thread(lock::release));
    }

    private static void applyHotkeys(GlobalHotkeyManager hotkeys, Settings s) {
        hotkeys.configure(Hotkey.parse(s.cleanHotkey), Hotkey.parse(s.aiHotkey));
    }
}
